/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.versioned.transfer;

import static com.google.common.base.Preconditions.checkState;
import static org.projectnessie.versioned.transfer.ExportImportConstants.DEFAULT_ATTACHMENT_BATCH_SIZE;
import static org.projectnessie.versioned.transfer.ExportImportConstants.DEFAULT_COMMIT_BATCH_SIZE;
import static org.projectnessie.versioned.transfer.ExportImportConstants.EXPORT_METADATA;
import static org.projectnessie.versioned.transfer.ExportImportConstants.HEADS_AND_FORKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import org.immutables.value.Value;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Content;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.CommitMetaSerializer;
import org.projectnessie.versioned.ContentAttachment;
import org.projectnessie.versioned.ContentAttachmentKey;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.NamedRef;
import org.projectnessie.versioned.ReferenceAlreadyExistsException;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.TagName;
import org.projectnessie.versioned.persist.adapter.CommitLogEntry;
import org.projectnessie.versioned.persist.adapter.ContentId;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.ImmutableCommitLogEntry;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;
import org.projectnessie.versioned.store.DefaultStoreWorker;
import org.projectnessie.versioned.transfer.serialize.TransferTypes.Commit;
import org.projectnessie.versioned.transfer.serialize.TransferTypes.ExportMeta;
import org.projectnessie.versioned.transfer.serialize.TransferTypes.ExportVersion;
import org.projectnessie.versioned.transfer.serialize.TransferTypes.HeadsAndForks;
import org.projectnessie.versioned.transfer.serialize.TransferTypes.NamedReference;

@Value.Immutable
public abstract class NessieImporter {

  public static NessieImporter.Builder builder() {
    return ImmutableNessieImporter.builder();
  }

  @SuppressWarnings("UnusedReturnValue")
  public interface Builder {
    /** Mandatory, specify the {@code DatabaseAdapter} to use. */
    Builder databaseAdapter(DatabaseAdapter databaseAdapter);

    /** Optional, specify a custom {@link ObjectMapper}. */
    Builder objectMapper(ObjectMapper objectMapper);

    /** Optional, specify a custom {@link StoreWorker}. */
    Builder storeWorker(StoreWorker storeWorker);

    /**
     * Optional, specify the number of commit log entries to be written at once, defaults to {@value
     * ExportImportConstants#DEFAULT_COMMIT_BATCH_SIZE}.
     */
    Builder commitBatchSize(int commitBatchSize);

    /**
     * Optional, specify the number of content attachments to be written at once, defaults to
     * {@value ExportImportConstants#DEFAULT_ATTACHMENT_BATCH_SIZE}.
     */
    Builder attachmentBatchSize(int attachmentBatchSize);

    Builder progressListener(ProgressListener progressListener);

    Builder importFileSupplier(ImportFileSupplier importFileSupplier);

    NessieImporter build();
  }

  abstract DatabaseAdapter databaseAdapter();

  @Value.Default
  int commitBatchSize() {
    return DEFAULT_COMMIT_BATCH_SIZE;
  }

  @Value.Default
  int attachmentBatchSize() {
    return DEFAULT_ATTACHMENT_BATCH_SIZE;
  }

  @Value.Default
  StoreWorker storeWorker() {
    return DefaultStoreWorker.instance();
  }

  @Value.Default
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Value.Default
  ProgressListener progressListener() {
    return (x, y) -> {};
  }

  abstract ImportFileSupplier importFileSupplier();

  public ImportResult importNessieRepository() throws IOException {
    @SuppressWarnings("resource")
    ImportFileSupplier importFiles = importFileSupplier();

    progressListener().progress(ProgressEvent.STARTED);

    progressListener().progress(ProgressEvent.START_META);
    ExportMeta exportMeta;
    try (InputStream input = importFiles.newFileInput(EXPORT_METADATA)) {
      exportMeta = ExportMeta.parseFrom(input);
    }
    progressListener().progress(ProgressEvent.END_META, exportMeta);

    checkState(
        exportMeta.getVersion() == ExportVersion.V1,
        "This Nessie-version version does not support importing a %s (%s) export",
        exportMeta.getVersion().name(),
        exportMeta.getVersionValue());

    HeadsAndForks headsAndForks;
    try (InputStream input = importFiles.newFileInput(HEADS_AND_FORKS)) {
      headsAndForks = HeadsAndForks.parseFrom(input);
    }

    progressListener().progress(ProgressEvent.START_COMMITS);
    long commitCount = importCommits(exportMeta);
    progressListener().progress(ProgressEvent.END_COMMITS);

    progressListener().progress(ProgressEvent.START_NAMED_REFERENCES);
    long namedReferenceCount = importNamedReferences(exportMeta);
    progressListener().progress(ProgressEvent.END_NAMED_REFERENCES);

    progressListener().progress(ProgressEvent.FINISHED);

    return ImmutableImportResult.builder()
        .exportMeta(exportMeta)
        .headsAndForks(headsAndForks)
        .importedCommitCount(commitCount)
        .importedReferenceCount(namedReferenceCount)
        .build();
  }

  private long importNamedReferences(ExportMeta exportMeta) throws IOException {
    long namedReferenceCount = 0L;
    @SuppressWarnings("resource")
    ImportFileSupplier importFiles = importFileSupplier();
    for (String fileName : exportMeta.getNamedReferencesFilesList()) {
      try (InputStream input = importFiles.newFileInput(fileName)) {
        while (true) {
          NamedReference namedReference = NamedReference.parseDelimitedFrom(input);
          if (namedReference == null) {
            break;
          }

          NamedRef ref;
          switch (namedReference.getRefType()) {
            case Tag:
              ref = TagName.of(namedReference.getName());
              break;
            case Branch:
              ref = BranchName.of(namedReference.getName());
              break;
            default:
              throw new IllegalArgumentException("Unknown reference type " + namedReference);
          }

          try {
            databaseAdapter().create(ref, Hash.of(namedReference.getCommitId()));
          } catch (ReferenceAlreadyExistsException | ReferenceNotFoundException e) {
            throw new RuntimeException(e);
          }

          namedReferenceCount++;
          progressListener().progress(ProgressEvent.NAMED_REFERENCE_WRITTEN);
        }
      }
    }
    return namedReferenceCount;
  }

  private long importCommits(ExportMeta exportMeta) throws IOException {
    long commitCount = 0L;
    int keyListDistance = databaseAdapter().getConfig().getKeyListDistance();

    try (BatchWriter<Hash, CommitLogEntry> commitBatchWriter =
            BatchWriter.commitBatchWriter(commitBatchSize(), databaseAdapter());
        BatchWriter<ContentAttachmentKey, ContentAttachment> attachmentsBatchWriter =
            BatchWriter.attachmentsBatchWriter(attachmentBatchSize(), databaseAdapter())) {

      @SuppressWarnings("resource")
      ImportFileSupplier importFiles = importFileSupplier();
      for (String fileName : exportMeta.getCommitsFilesList()) {
        try (InputStream input = importFiles.newFileInput(fileName)) {
          while (true) {
            Commit commit = Commit.parseDelimitedFrom(input);
            if (commit == null) {
              break;
            }

            ByteString metadata;
            try (InputStream in = commit.getMetadata().newInput()) {
              metadata =
                  CommitMetaSerializer.METADATA_SERIALIZER.toBytes(
                      objectMapper().readValue(in, CommitMeta.class));
            }

            ImmutableCommitLogEntry.Builder logEntry =
                ImmutableCommitLogEntry.builder()
                    .createdTime(commit.getCreatedTimeMicros())
                    .commitSeq(commit.getCommitSequence())
                    .hash(Hash.of(commit.getCommitId()))
                    .metadata(metadata)
                    .keyListDistance((int) (commit.getCommitSequence() % keyListDistance))
                    .addParents(Hash.of(commit.getParentCommitId()));
            commit
                .getAdditionalParentsList()
                .forEach(ap -> logEntry.addAdditionalParents(Hash.of(ap)));
            commit
                .getOperationsList()
                .forEach(
                    op -> {
                      Key key = Key.of(op.getContentKeyList());
                      switch (op.getOperationType()) {
                        case Delete:
                          logEntry.addDeletes(key);
                          break;
                        case Put:
                          try (InputStream inValue = op.getValue().newInput()) {
                            Content content = objectMapper().readValue(inValue, Content.class);
                            ByteString onRef =
                                storeWorker()
                                    .toStoreOnReferenceState(content, attachmentsBatchWriter::add);

                            logEntry.addPuts(
                                KeyWithBytes.of(
                                    key,
                                    ContentId.of(op.getContentId()),
                                    (byte) op.getPayload(),
                                    onRef));

                          } catch (IOException e) {
                            throw new RuntimeException(e);
                          }
                          break;
                        default:
                          throw new IllegalArgumentException("Unknown operation type " + op);
                      }
                    });

            commitBatchWriter.add(logEntry.build());
            commitCount++;

            progressListener().progress(ProgressEvent.COMMIT_WRITTEN);
          }
        }
      }
    }
    return commitCount;
  }
}
