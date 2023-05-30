/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.jaxrs.tests;

import static com.google.common.collect.Maps.immutableEntry;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.projectnessie.model.CommitMeta.fromMessage;
import static org.projectnessie.model.FetchOption.ALL;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectnessie.client.api.CommitMultipleOperationsBuilder;
import org.projectnessie.client.api.CreateNamespaceResult;
import org.projectnessie.client.api.DeleteNamespaceResult;
import org.projectnessie.client.api.GetAllReferencesBuilder;
import org.projectnessie.client.api.GetDiffBuilder;
import org.projectnessie.client.api.GetEntriesBuilder;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.client.api.PagingBuilder;
import org.projectnessie.client.api.UpdateNamespaceResult;
import org.projectnessie.client.ext.NessieApiVersion;
import org.projectnessie.client.ext.NessieApiVersions;
import org.projectnessie.client.ext.NessieClientFactory;
import org.projectnessie.error.BaseNessieClientServerException;
import org.projectnessie.error.ContentKeyErrorDetails;
import org.projectnessie.error.NessieBadRequestException;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieContentNotFoundException;
import org.projectnessie.error.NessieNamespaceNotEmptyException;
import org.projectnessie.error.NessieNamespaceNotFoundException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.error.NessieReferenceConflictException;
import org.projectnessie.error.ReferenceConflicts;
import org.projectnessie.model.Branch;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.CommitResponse;
import org.projectnessie.model.CommitResponse.AddedContent;
import org.projectnessie.model.Conflict;
import org.projectnessie.model.Conflict.ConflictType;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.ContentResponse;
import org.projectnessie.model.DiffResponse;
import org.projectnessie.model.DiffResponse.DiffEntry;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.EntriesResponse.Entry;
import org.projectnessie.model.GetMultipleContentsResponse;
import org.projectnessie.model.GetNamespacesResponse;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.IcebergView;
import org.projectnessie.model.ImmutableReferenceMetadata;
import org.projectnessie.model.LogResponse;
import org.projectnessie.model.LogResponse.LogEntry;
import org.projectnessie.model.MergeResponse.ContentKeyConflict;
import org.projectnessie.model.MergeResponse.ContentKeyDetails;
import org.projectnessie.model.Namespace;
import org.projectnessie.model.NessieConfiguration;
import org.projectnessie.model.Operation;
import org.projectnessie.model.Operation.Delete;
import org.projectnessie.model.Operation.Put;
import org.projectnessie.model.Reference;
import org.projectnessie.model.ReferencesResponse;
import org.projectnessie.model.Tag;

/** Nessie-API tests. */
@NessieApiVersions // all versions
public abstract class BaseTestNessieApi {

  public static final String EMPTY = Hashing.sha256().hashString("empty", UTF_8).toString();

  private NessieApiV1 api;
  private NessieApiVersion apiVersion;

  // Cannot use @ExtendWith(SoftAssertionsExtension.class) + @InjectSoftAssertions here, because
  // of Quarkus class loading issues. See https://github.com/quarkusio/quarkus/issues/19814
  protected final SoftAssertions soft = new SoftAssertions();

  static {
    // Note: REST tests validate some locale-specific error messages, but expect on the messages to
    // be in ENGLISH. However, the JRE's startup classes (in particular class loaders) may cause the
    // default Locale to be initialized before Maven is able to override the user.language system
    // property. Therefore, we explicitly set the default Locale to ENGLISH here to match tests'
    // expectations.
    Locale.setDefault(Locale.ENGLISH);
  }

  @SuppressWarnings("JUnitMalformedDeclaration")
  @BeforeEach
  void initApi(NessieClientFactory clientFactory) {
    this.api = clientFactory.make();
    this.apiVersion = clientFactory.apiVersion();
  }

  @NotNull
  @jakarta.validation.constraints.NotNull
  public NessieApiV1 api() {
    return api;
  }

  public boolean isV2() {
    return NessieApiVersion.V2 == apiVersion;
  }

  @AfterEach
  public void tearDown() throws Exception {
    try {
      // Cannot use @ExtendWith(SoftAssertionsExtension.class) + @InjectSoftAssertions here, because
      // of Quarkus class loading issues. See https://github.com/quarkusio/quarkus/issues/19814
      soft.assertAll();
    } finally {
      Branch defaultBranch = api.getDefaultBranch();
      api()
          .assignBranch()
          .branch(defaultBranch)
          .assignTo(Branch.of(defaultBranch.getName(), EMPTY))
          .assign();
      api.getAllReferences().stream()
          .forEach(
              ref -> {
                try {
                  if (ref instanceof Branch && !ref.getName().equals(defaultBranch.getName())) {
                    api.deleteBranch().branch((Branch) ref).delete();
                  } else if (ref instanceof Tag) {
                    api.deleteTag().tag((Tag) ref).delete();
                  }
                } catch (NessieConflictException | NessieNotFoundException e) {
                  throw new RuntimeException(e);
                }
              });
      api.close();
    }
  }

  @SuppressWarnings("unchecked")
  protected <R extends Reference> R createReference(R reference, String sourceRefName)
      throws NessieConflictException, NessieNotFoundException {
    return (R) api().createReference().sourceRefName(sourceRefName).reference(reference).create();
  }

  protected CommitMultipleOperationsBuilder prepCommit(
      Branch branch, String msg, Operation... operations) {
    return api()
        .commitMultipleOperations()
        .branch(branch)
        .commitMeta(fromMessage(msg))
        .operations(asList(operations));
  }

  protected Put dummyPut(String... elements) {
    return Put.of(ContentKey.of(elements), dummyTable());
  }

  private static IcebergTable dummyTable() {
    return IcebergTable.of("foo", 1, 2, 3, 4);
  }

  protected boolean fullPagingSupport() {
    return false;
  }

  protected boolean pagingSupported(PagingBuilder<?, ?, ?> apiRequestBuilder) {
    if (fullPagingSupport()) {
      return isV2() || !(apiRequestBuilder instanceof GetDiffBuilder);
    }
    // Note: paging API is provided for diff, entries and references API, but the server does not
    // support that yet.
    return !(apiRequestBuilder instanceof GetDiffBuilder)
        && !(apiRequestBuilder instanceof GetAllReferencesBuilder)
        && !(apiRequestBuilder instanceof GetEntriesBuilder);
  }

  @Test
  public void config() throws NessieNotFoundException {
    NessieConfiguration config = api().getConfig();
    soft.assertThat(config)
        .extracting(
            NessieConfiguration::getDefaultBranch, NessieConfiguration::getMaxSupportedApiVersion)
        .containsExactly("main", 2);

    if (isV2()) {
      soft.assertThat(config.getNoAncestorHash()).isNotNull();
    }

    soft.assertThat(api().getDefaultBranch())
        .extracting(Branch::getName, Branch::getHash)
        .containsExactly(config.getDefaultBranch(), EMPTY);
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2)
  public void specVersion() {
    NessieConfiguration config = api().getConfig();
    soft.assertThat(config.getSpecVersion()).isNotEmpty();
  }

  @Test
  public void references() throws Exception {
    Branch main = api().getDefaultBranch();
    soft.assertThat(api().getAllReferences().get().getReferences()).containsExactly(main);

    Branch main1 =
        prepCommit(
                main,
                "commit",
                Put.of(ContentKey.of("key"), Namespace.of("key")),
                dummyPut("key", "foo"))
            .commit();

    CommitMeta commitMetaMain =
        api().getCommitLog().reference(main1).get().getLogEntries().get(0).getCommitMeta();

    Tag tag = createReference(Tag.of("tag1", main1.getHash()), main.getName());

    Branch branch = createReference(Branch.of("branch1", main1.getHash()), main.getName());

    Branch branch1 = prepCommit(branch, "branch", dummyPut("key", "bar")).commit();

    CommitMeta commitMetaBranch =
        api().getCommitLog().reference(branch1).get().getLogEntries().get(0).getCommitMeta();

    // Get references

    soft.assertThat(api().getAllReferences().get().getReferences())
        .containsExactlyInAnyOrder(main1, tag, branch1);
    soft.assertThat(api().getReference().refName(main.getName()).get()).isEqualTo(main1);
    soft.assertThat(api().getReference().refName(tag.getName()).get()).isEqualTo(tag);
    soft.assertThat(api().getReference().refName(branch.getName()).get()).isEqualTo(branch1);

    // Get references / FULL

    Branch mainFull =
        Branch.of(
            main.getName(),
            main1.getHash(),
            ImmutableReferenceMetadata.builder()
                .numTotalCommits(1L)
                .commitMetaOfHEAD(commitMetaMain)
                .build());
    Branch branchFull =
        Branch.of(
            branch.getName(),
            branch1.getHash(),
            ImmutableReferenceMetadata.builder()
                .numTotalCommits(2L)
                .numCommitsAhead(1)
                .numCommitsBehind(0)
                .commonAncestorHash(main1.getHash())
                .commitMetaOfHEAD(commitMetaBranch)
                .build());
    Tag tagFull =
        Tag.of(
            tag.getName(),
            tag.getHash(),
            ImmutableReferenceMetadata.builder()
                .numTotalCommits(1L)
                .commitMetaOfHEAD(commitMetaMain)
                .build());
    soft.assertThat(api().getAllReferences().fetch(ALL).get().getReferences())
        .containsExactlyInAnyOrder(mainFull, branchFull, tagFull);
    soft.assertThat(api().getReference().refName(main.getName()).fetch(ALL).get())
        .isEqualTo(mainFull);
    soft.assertThat(api().getReference().refName(tag.getName()).fetch(ALL).get())
        .isEqualTo(tagFull);
    soft.assertThat(api().getReference().refName(branch.getName()).fetch(ALL).get())
        .isEqualTo(branchFull);

    // Assign

    if (isV2()) {
      tag = api.assignTag().tag(tag).assignTo(main).assignAndGet();
      soft.assertThat(tag).isEqualTo(Tag.of(tag.getName(), main.getHash()));
    } else {
      api.assignTag().tag(tag).assignTo(main).assign();
      tag = Tag.of(tag.getName(), main.getHash());
    }

    AbstractThrowableAssert<?, ? extends Throwable> assignConflict =
        isV2()
            ? soft.assertThatThrownBy(
                () -> api.assignBranch().branch(branch).assignTo(main).assignAndGet())
            : soft.assertThatThrownBy(
                () -> api.assignBranch().branch(branch).assignTo(main).assign());
    assignConflict
        .isInstanceOf(NessieReferenceConflictException.class)
        .asInstanceOf(type(NessieReferenceConflictException.class))
        .extracting(NessieReferenceConflictException::getErrorDetails)
        .extracting(ReferenceConflicts::conflicts, list(Conflict.class))
        .extracting(Conflict::conflictType)
        .containsExactly(ConflictType.UNEXPECTED_HASH);

    Branch branchAssigned;
    if (isV2()) {
      branchAssigned = api.assignBranch().branch(branch1).assignTo(main).assignAndGet();
      soft.assertThat(branchAssigned).isEqualTo(Branch.of(branch.getName(), main.getHash()));
    } else {
      api.assignBranch().branch(branch1).assignTo(main).assign();
      branchAssigned = Branch.of(branch.getName(), main.getHash());
    }

    // check

    soft.assertThat(api().getAllReferences().get().getReferences())
        .containsExactlyInAnyOrder(main1, tag, branchAssigned);
    soft.assertThat(api().getReference().refName(main.getName()).get()).isEqualTo(main1);
    soft.assertThat(api().getReference().refName(tag.getName()).get()).isEqualTo(tag);
    soft.assertThat(api().getReference().refName(branch.getName()).get()).isEqualTo(branchAssigned);

    // Delete

    if (isV2()) {
      Tag deleted = api().deleteTag().tag(tag).getAndDelete();
      soft.assertThat(deleted).isEqualTo(tag);
    } else {
      api().deleteTag().tag(tag).delete();
    }

    AbstractThrowableAssert<?, ? extends Throwable> deleteConflict =
        isV2()
            ? soft.assertThatThrownBy(() -> api().deleteBranch().branch(branch).getAndDelete())
            : soft.assertThatThrownBy(() -> api().deleteBranch().branch(branch).delete());
    deleteConflict
        .isInstanceOf(NessieReferenceConflictException.class)
        .asInstanceOf(type(NessieReferenceConflictException.class))
        .extracting(NessieReferenceConflictException::getErrorDetails)
        .extracting(ReferenceConflicts::conflicts, list(Conflict.class))
        .extracting(Conflict::conflictType)
        .containsExactly(ConflictType.UNEXPECTED_HASH);

    if (isV2()) {
      Branch deleted = api().deleteBranch().branch(branchAssigned).getAndDelete();
      soft.assertThat(deleted).isEqualTo(branchAssigned);
    } else {
      api().deleteBranch().branch(branchAssigned).delete();
    }

    soft.assertThat(api().getAllReferences().get().getReferences())
        .containsExactlyInAnyOrder(main1);
    soft.assertThat(api().getReference().refName(main.getName()).get()).isEqualTo(main1);

    // Create / null hash

    soft.assertThatThrownBy(() -> createReference(Tag.of("tag2", null), main.getName()))
        .isInstanceOf(NessieBadRequestException.class);

    Reference branch2 = createReference(Branch.of("branch2", null), main.getName());
    soft.assertThat(branch2).isEqualTo(Branch.of("branch2", EMPTY));

    // not exist

    String refName = "does-not-exist";
    soft.assertThatThrownBy(() -> api().getReference().refName(refName).get())
        .isInstanceOf(NessieNotFoundException.class);
    soft.assertThatThrownBy(
            () ->
                api()
                    .assignBranch()
                    .branch(Branch.of(refName, main.getHash()))
                    .assignTo(main)
                    .assign())
        .isInstanceOf(NessieNotFoundException.class);
    soft.assertThatThrownBy(
            () -> api().assignTag().tag(Tag.of(refName, main.getHash())).assignTo(main).assign())
        .isInstanceOf(NessieNotFoundException.class);
    soft.assertThatThrownBy(
            () -> api().deleteBranch().branch(Branch.of(refName, main.getHash())).delete())
        .isInstanceOf(NessieNotFoundException.class);
    soft.assertThatThrownBy(() -> api().deleteTag().tag(Tag.of(refName, main.getHash())).delete())
        .isInstanceOf(NessieNotFoundException.class);
    if (isV2()) {
      soft.assertThatThrownBy(
              () ->
                  api()
                      .assignBranch()
                      .branch(Branch.of(refName, main.getHash()))
                      .assignTo(main)
                      .assignAndGet())
          .isInstanceOf(NessieNotFoundException.class);
      soft.assertThatThrownBy(
              () ->
                  api()
                      .assignTag()
                      .tag(Tag.of(refName, main.getHash()))
                      .assignTo(main)
                      .assignAndGet())
          .isInstanceOf(NessieNotFoundException.class);
      soft.assertThatThrownBy(
              () -> api().deleteBranch().branch(Branch.of(refName, main.getHash())).getAndDelete())
          .isInstanceOf(NessieNotFoundException.class);
      soft.assertThatThrownBy(
              () -> api().deleteTag().tag(Tag.of(refName, main.getHash())).getAndDelete())
          .isInstanceOf(NessieNotFoundException.class);
    }
  }

  @Test
  public void referencesWithLimitInFirstPage() throws Exception {
    assumeFalse(pagingSupported(api().getAllReferences()));
    // Verify that result limiting produces expected errors when paging is not supported
    api().createReference().reference(Branch.of("branch", null)).create();
    assertThatThrownBy(() -> api().getAllReferences().maxRecords(1).get())
        .isInstanceOf(NessieBadRequestException.class)
        .hasMessageContaining("Paging not supported");
  }

  @Test
  public void commitMergeTransplant() throws Exception {
    Branch main = api().getDefaultBranch();

    main =
        prepCommit(
                main,
                "common ancestor",
                dummyPut("unrelated"),
                Put.of(ContentKey.of("a"), Namespace.of("a")),
                Put.of(ContentKey.of("b"), Namespace.of("b")))
            .commit();
    main = prepCommit(main, "common ancestor", Delete.of(ContentKey.of("unrelated"))).commit();

    Branch branch = createReference(Branch.of("branch", main.getHash()), main.getName());

    Branch otherBranch = createReference(Branch.of("other", main.getHash()), main.getName());

    if (isV2()) {
      CommitResponse resp = prepCommit(branch, "one", dummyPut("a", "a")).commitWithResponse();
      branch = resp.getTargetBranch();
      soft.assertThat(resp.getAddedContents()).hasSize(1);
      resp = prepCommit(branch, "two", dummyPut("b", "a"), dummyPut("b", "b")).commitWithResponse();
      branch = resp.getTargetBranch();
      soft.assertThat(resp.getAddedContents())
          .hasSize(2)
          .extracting(AddedContent::getKey)
          .containsExactlyInAnyOrder(ContentKey.of("b", "a"), ContentKey.of("b", "b"));
      soft.assertThat(resp.contentWithAssignedId(ContentKey.of("b", "a"), dummyTable()))
          .extracting(IcebergTable::getId)
          .isNotNull();
      soft.assertThat(resp.contentWithAssignedId(ContentKey.of("b", "b"), dummyTable()))
          .extracting(IcebergTable::getId)
          .isNotNull();
      soft.assertThat(resp.contentWithAssignedId(ContentKey.of("x", "y"), dummyTable()))
          .extracting(IcebergTable::getId)
          .isNull();
    } else {
      branch = prepCommit(branch, "one", dummyPut("a", "a")).commit();
      branch = prepCommit(branch, "two", dummyPut("b", "a"), dummyPut("b", "b")).commit();
    }

    soft.assertThat(api().getCommitLog().refName(branch.getName()).get().getLogEntries())
        .hasSize(4);

    soft.assertThat(api().getEntries().reference(branch).get().getEntries())
        .extracting(Entry::getName)
        .containsExactlyInAnyOrder(
            ContentKey.of("a"),
            ContentKey.of("b"),
            ContentKey.of("a", "a"),
            ContentKey.of("b", "a"),
            ContentKey.of("b", "b"));

    soft.assertThat(api().getCommitLog().refName(main.getName()).get().getLogEntries()).hasSize(2);
    soft.assertThat(api().getEntries().reference(main).get().getEntries())
        .extracting(Entry::getName)
        .containsExactly(ContentKey.of("a"), ContentKey.of("b"));

    Reference main2;
    if (isV2()) {
      api()
          .mergeRefIntoBranch()
          .fromRef(branch)
          .branch(main)
          .message("not the merge message")
          .commitMeta(
              CommitMeta.builder()
                  .message("My custom merge message")
                  .author("NessieHerself")
                  .signedOffBy("Arctic")
                  .authorTime(Instant.EPOCH)
                  .putProperties("property", "value")
                  .build())
          .keepIndividualCommits(false)
          .merge();
      main2 = api().getReference().refName(main.getName()).get();
      List<LogEntry> postMergeLog =
          api().getCommitLog().refName(main.getName()).get().getLogEntries();
      soft.assertThat(postMergeLog)
          .hasSize(3)
          .first()
          .extracting(LogEntry::getCommitMeta)
          .extracting(
              CommitMeta::getMessage,
              CommitMeta::getAllAuthors,
              CommitMeta::getAllSignedOffBy,
              CommitMeta::getAuthorTime,
              CommitMeta::getProperties)
          .containsExactly(
              "My custom merge message",
              singletonList("NessieHerself"),
              singletonList("Arctic"),
              Instant.EPOCH,
              ImmutableMap.of("property", "value", "_merge_parent", branch.getHash()));
    } else {
      api().mergeRefIntoBranch().fromRef(branch).branch(main).keepIndividualCommits(false).merge();
      main2 = api().getReference().refName(main.getName()).get();
      soft.assertThat(api().getCommitLog().refName(main.getName()).get().getLogEntries())
          .hasSize(3);
    }

    soft.assertThat(api().getEntries().reference(main2).get().getEntries())
        .extracting(Entry::getName)
        .containsExactlyInAnyOrder(
            ContentKey.of("a"),
            ContentKey.of("b"),
            ContentKey.of("a", "a"),
            ContentKey.of("b", "a"),
            ContentKey.of("b", "b"));

    soft.assertThat(api().getEntries().reference(otherBranch).get().getEntries())
        .extracting(Entry::getName)
        .containsExactly(ContentKey.of("a"), ContentKey.of("b"));
    api()
        .transplantCommitsIntoBranch()
        .fromRefName(main.getName())
        .hashesToTransplant(singletonList(main2.getHash()))
        .branch(otherBranch)
        .transplant();
    soft.assertThat(api().getEntries().refName(otherBranch.getName()).get().getEntries())
        .extracting(Entry::getName)
        .containsExactlyInAnyOrder(
            ContentKey.of("a"),
            ContentKey.of("b"),
            ContentKey.of("a", "a"),
            ContentKey.of("b", "a"),
            ContentKey.of("b", "b"));

    soft.assertThat(
            api()
                .getContent()
                .key(ContentKey.of("a", "a"))
                .key(ContentKey.of("b", "a"))
                .key(ContentKey.of("b", "b"))
                .refName(main.getName())
                .get())
        .containsKeys(ContentKey.of("a", "a"), ContentKey.of("b", "a"), ContentKey.of("b", "b"));
  }

  @Test
  public void mergeTransplantDryRunWithConflictInResult() throws Exception {
    Branch main0 =
        prepCommit(api().getDefaultBranch(), "common ancestor", dummyPut("common")).commit();
    Branch branch = createReference(Branch.of("branch", main0.getHash()), main0.getName());

    branch =
        prepCommit(branch, "branch", dummyPut("conflictingKey1"), dummyPut("branchKey")).commit();
    Branch main =
        prepCommit(main0, "main", dummyPut("conflictingKey1"), dummyPut("mainKey")).commit();

    ListAssert<ContentKeyDetails> mergeAssert =
        soft.assertThat(
            api()
                .mergeRefIntoBranch()
                .fromRef(branch)
                .branch(main)
                .returnConflictAsResult(true) // adds coverage on top of AbstractMerge tests
                .dryRun(true)
                .merge()
                .getDetails());
    if (isV2()) {
      mergeAssert
          // old model returns "UNKNOWN" for Conflict.conflictType(), new model returns KEY_EXISTS
          .extracting(ContentKeyDetails::getKey, d -> d.getConflict() != null)
          .contains(
              tuple(ContentKey.of("branchKey"), false),
              tuple(ContentKey.of("conflictingKey1"), true));
    } else {
      mergeAssert
          .extracting(ContentKeyDetails::getKey, ContentKeyDetails::getConflictType)
          .contains(
              tuple(ContentKey.of("branchKey"), ContentKeyConflict.NONE),
              tuple(ContentKey.of("conflictingKey1"), ContentKeyConflict.UNRESOLVABLE));
    }

    // Assert no change to the ref HEAD
    soft.assertThat(api().getReference().refName(main.getName()).get()).isEqualTo(main);

    mergeAssert =
        soft.assertThat(
            api()
                .transplantCommitsIntoBranch()
                .fromRefName(branch.getName())
                .hashesToTransplant(singletonList(branch.getHash()))
                .branch(main0)
                .returnConflictAsResult(true) // adds coverage on top of AbstractTransplant tests
                .dryRun(true)
                .transplant()
                .getDetails());
    if (isV2()) {
      mergeAssert
          // old model returns "UNKNOWN" for Conflict.conflictType(), new model returns KEY_EXISTS
          .extracting(ContentKeyDetails::getKey, d -> d.getConflict() != null)
          .contains(
              tuple(ContentKey.of("branchKey"), false),
              tuple(ContentKey.of("conflictingKey1"), true));
    } else {
      mergeAssert
          .extracting(ContentKeyDetails::getKey, ContentKeyDetails::getConflictType)
          .contains(
              tuple(ContentKey.of("branchKey"), ContentKeyConflict.NONE),
              tuple(ContentKey.of("conflictingKey1"), ContentKeyConflict.UNRESOLVABLE));
    }
    // Assert no change to the ref HEAD
    soft.assertThat(api().getReference().refName(main.getName()).get()).isEqualTo(main);
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2)
  public void commitParents() throws Exception {
    Branch main = api().getDefaultBranch();

    Branch initialCommit = prepCommit(main, "common ancestor", dummyPut("initial")).commit();
    main = prepCommit(main, "common ancestor", dummyPut("test1")).commit();

    soft.assertThat(
            api().getCommitLog().refName(main.getName()).maxRecords(1).get().getLogEntries())
        .map(logEntry -> logEntry.getCommitMeta().getParentCommitHashes())
        .first()
        .asInstanceOf(list(String.class))
        .containsExactly(initialCommit.getHash());

    Branch branch = createReference(Branch.of("branch", main.getHash()), main.getName());

    branch =
        prepCommit(branch, "one", Put.of(ContentKey.of("a"), Namespace.of("a")), dummyPut("a", "a"))
            .commit();
    Reference mainParent = api().getReference().refName(main.getName()).get();

    api().mergeRefIntoBranch().fromRef(branch).branch(main).merge();

    soft.assertThat(
            api().getCommitLog().refName(main.getName()).maxRecords(1).get().getLogEntries())
        .map(logEntry -> logEntry.getCommitMeta().getParentCommitHashes())
        .first()
        .asInstanceOf(list(String.class))
        .containsExactly(
            mainParent.getHash(), branch.getHash()); // branch lineage parent, then merge parent
  }

  @Test
  public void diff() throws Exception {
    Branch main = api().getDefaultBranch();
    Branch branch1 = createReference(Branch.of("b1", main.getHash()), main.getName());
    Branch branch2 = createReference(Branch.of("b2", main.getHash()), main.getName());

    ContentKey key1 = ContentKey.of("1");
    ContentKey key3 = ContentKey.of("3");
    ContentKey key4 = ContentKey.of("4");

    branch1 =
        prepCommit(
                branch1,
                "c1",
                Put.of(key1, Namespace.of("1")),
                dummyPut("1", "1"),
                dummyPut("1", "2"),
                dummyPut("1", "3"))
            .commit();
    branch2 =
        prepCommit(
                branch2,
                "c2",
                Put.of(key1, Namespace.of("1")),
                Put.of(key3, Namespace.of("3")),
                Put.of(key4, Namespace.of("4")),
                dummyPut("1", "1"),
                dummyPut("3", "1"),
                dummyPut("4", "1"))
            .commit();

    ContentKey key11 = ContentKey.of("1", "1");
    ContentKey key12 = ContentKey.of("1", "2");
    ContentKey key13 = ContentKey.of("1", "3");
    ContentKey key31 = ContentKey.of("3", "1");
    ContentKey key41 = ContentKey.of("4", "1");
    Map<ContentKey, Content> contents1 =
        api()
            .getContent()
            .reference(branch1)
            .key(key11)
            .key(key12)
            .key(key13)
            .key(key11.getParent())
            .key(key31.getParent())
            .key(key41.getParent())
            .get();
    Map<ContentKey, Content> contents2 =
        api()
            .getContent()
            .reference(branch2)
            .key(key11)
            .key(key31)
            .key(key41)
            .key(key11.getParent())
            .key(key31.getParent())
            .key(key41.getParent())
            .get();

    DiffResponse diff1response = api().getDiff().fromRef(branch1).toRef(branch2).get();
    List<DiffEntry> diff1 = diff1response.getDiffs();

    if (isV2()) {
      soft.assertThat(diff1response.getEffectiveFromReference()).isEqualTo(branch1);
      soft.assertThat(diff1response.getEffectiveToReference()).isEqualTo(branch2);

      // Key filtering
      if (fullPagingSupport()) {
        soft.assertThat(
                api()
                    .getDiff()
                    .fromRef(branch1)
                    .toRef(branch2)
                    .minKey(key12)
                    .maxKey(key31)
                    .get()
                    .getDiffs())
            .extracting(DiffEntry::getKey)
            .containsExactlyInAnyOrder(key12, key13, key3, key31);
        soft.assertThat(
                api().getDiff().fromRef(branch1).toRef(branch2).minKey(key31).get().getDiffs())
            .extracting(DiffEntry::getKey)
            .containsExactlyInAnyOrder(key31, key4, key41);
        soft.assertThat(
                api().getDiff().fromRef(branch1).toRef(branch2).maxKey(key12).get().getDiffs())
            .extracting(DiffEntry::getKey)
            .containsExactlyInAnyOrder(key1, key11, key12);
      }
      soft.assertThat(api().getDiff().fromRef(branch1).toRef(branch2).key(key12).get().getDiffs())
          .extracting(DiffEntry::getKey)
          .containsExactlyInAnyOrder(key12);
      soft.assertThat(
              api()
                  .getDiff()
                  .fromRef(branch1)
                  .toRef(branch2)
                  .key(key31)
                  .key(key12)
                  .get()
                  .getDiffs())
          .extracting(DiffEntry::getKey)
          .containsExactlyInAnyOrder(key12, key31);
      soft.assertThat(
              api()
                  .getDiff()
                  .fromRef(branch1)
                  .toRef(branch2)
                  .filter("key.namespace=='1'")
                  .get()
                  .getDiffs())
          .extracting(DiffEntry::getKey)
          .containsExactlyInAnyOrder(key11, key12, key13);
    }
    soft.assertThat(diff1)
        .containsExactlyInAnyOrder(
            DiffEntry.diffEntry(
                key11.getParent(),
                contents1.get(key11.getParent()),
                contents2.get(key11.getParent())),
            DiffEntry.diffEntry(key11, contents1.get(key11), contents2.get(key11)),
            DiffEntry.diffEntry(key12, contents1.get(key12), null),
            DiffEntry.diffEntry(key13, contents1.get(key13), null),
            DiffEntry.diffEntry(key31, null, contents2.get(key31)),
            DiffEntry.diffEntry(key41, null, contents2.get(key41)),
            DiffEntry.diffEntry(key31.getParent(), null, contents2.get(key31.getParent())),
            DiffEntry.diffEntry(key41.getParent(), null, contents2.get(key41.getParent())));

    List<DiffEntry> diff2 =
        api().getDiff().fromRefName(branch1.getName()).toRef(branch2).get().getDiffs();
    List<DiffEntry> diff3 =
        api().getDiff().fromRef(branch1).toRefName(branch2.getName()).get().getDiffs();
    soft.assertThat(diff1).isEqualTo(diff2).isEqualTo(diff3);

    if (pagingSupported(api().getDiff())) {

      // Paging

      List<DiffEntry> all = new ArrayList<>();
      String token = null;
      for (int i = 0; i < 8; i++) {
        DiffResponse resp =
            api().getDiff().fromRef(branch1).toRef(branch2).maxRecords(1).pageToken(token).get();
        all.addAll(resp.getDiffs());
        token = resp.getToken();
        if (i == 7) {
          soft.assertThat(token).isNull();
        } else {
          soft.assertThat(token).isNotNull();
        }
      }

      soft.assertThat(all).containsExactlyInAnyOrderElementsOf(diff1);

      soft.assertThat(api().getDiff().fromRef(branch1).toRef(branch2).maxRecords(1).stream())
          .containsExactlyInAnyOrderElementsOf(diff1);
    }
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2) // v1 throws on getDiff().maxRecords(1)
  public void diffWithLimitInFirstPage() throws Exception {
    Branch main = api().getDefaultBranch();
    assumeFalse(pagingSupported(api().getDiff()));
    // Verify that result limiting produces expected errors when paging is not supported
    Branch branch1 = createReference(Branch.of("b1", main.getHash()), main.getName());
    Branch branch2 = createReference(Branch.of("b2", main.getHash()), main.getName());

    Branch from = prepCommit(branch1, "c1", dummyPut("1-1")).commit();
    Branch to = prepCommit(branch2, "c2", dummyPut("2-2")).commit();

    assertThatThrownBy(() -> api().getDiff().maxRecords(1).fromRef(from).toRef(to).get())
        .isInstanceOf(NessieBadRequestException.class)
        .hasMessageContaining("Paging not supported");
  }

  @Test
  public void commitLog() throws Exception {
    Branch main = api().getDefaultBranch();
    for (int i = 0; i < 10; i++) {
      if (i == 0) {
        main =
            prepCommit(
                    main,
                    "c-" + i,
                    Put.of(ContentKey.of("c"), Namespace.of("c")),
                    dummyPut("c", Integer.toString(i)))
                .commit();
      } else {
        main = prepCommit(main, "c-" + i, dummyPut("c", Integer.toString(i))).commit();
      }
    }

    List<LogEntry> notPaged = api().getCommitLog().reference(main).get().getLogEntries();
    soft.assertThat(notPaged).hasSize(10);

    List<LogEntry> all = new ArrayList<>();
    String token = null;
    for (int i = 0; i < 10; i++) {
      LogResponse resp = api().getCommitLog().reference(main).maxRecords(1).pageToken(token).get();
      all.addAll(resp.getLogEntries());
      token = resp.getToken();
      if (i == 9) {
        soft.assertThat(token).isNull();
      } else {
        soft.assertThat(token).isNotNull();
      }
    }

    soft.assertThat(all).containsExactlyElementsOf(notPaged);

    soft.assertAll();

    soft.assertThat(api().getCommitLog().reference(main).maxRecords(1).stream())
        .containsExactlyInAnyOrderElementsOf(all);
  }

  @Test
  public void allReferences() throws Exception {
    Branch main = api().getDefaultBranch();

    List<Reference> expect = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      expect.add(createReference(Branch.of("b-" + i, main.getHash()), main.getName()));
      expect.add(createReference(Tag.of("t-" + i, main.getHash()), main.getName()));
    }
    expect.add(main);

    List<Reference> notPaged = api().getAllReferences().get().getReferences();
    soft.assertThat(notPaged).containsExactlyInAnyOrderElementsOf(expect);

    if (pagingSupported(api().getAllReferences())) {
      List<Reference> all = new ArrayList<>();
      String token = null;
      for (int i = 0; i < 11; i++) {
        ReferencesResponse resp = api().getAllReferences().maxRecords(1).pageToken(token).get();
        all.addAll(resp.getReferences());
        token = resp.getToken();
        if (i == 10) {
          soft.assertThat(token).isNull();
        } else {
          soft.assertThat(token).isNotNull();
        }
      }

      soft.assertThat(all).containsExactlyElementsOf(notPaged);

      soft.assertAll();

      soft.assertThat(api().getAllReferences().maxRecords(1).stream())
          .containsExactlyInAnyOrderElementsOf(all);
    }
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V1)
  public void contentsOnDefaultBranch() throws Exception {
    Branch main = api().getDefaultBranch();
    ContentKey key = ContentKey.of("test.key1");
    Branch main1 =
        prepCommit(main, "commit", Put.of(key, IcebergTable.of("loc111", 1, 2, 3, 4))).commit();
    // Note: not specifying a reference name in API v1 means using the HEAD of the default branch.
    IcebergTable created = (IcebergTable) api().getContent().key(key).get().get(key);
    assertThat(created.getMetadataLocation()).isEqualTo("loc111");
    prepCommit(
            main1,
            "commit",
            Put.of(key, IcebergTable.builder().from(created).metadataLocation("loc222").build()))
        .commit();
    IcebergTable table2 = (IcebergTable) api().getContent().key(key).get().get(key);
    assertThat(table2.getMetadataLocation()).isEqualTo("loc222");
    // Note: not specifying a reference name for the default branch, but setting an older hash.
    IcebergTable table1 =
        (IcebergTable) api().getContent().hashOnRef(main1.getHash()).key(key).get().get(key);
    assertThat(table1).isEqualTo(created);
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2)
  public void contents() throws Exception {
    Branch main = api().getDefaultBranch();
    CommitResponse committed =
        prepCommit(
                main,
                "commit",
                Stream.concat(
                        Stream.of(
                            Put.of(ContentKey.of("b.b"), Namespace.of("b.b")),
                            Put.of(ContentKey.of("b.b", "c"), Namespace.of("b.b", "c"))),
                        IntStream.range(0, 8)
                            .mapToObj(i -> dummyPut("b.b", "c", Integer.toString(i))))
                    .toArray(Operation[]::new))
            .commitWithResponse();
    main = committed.getTargetBranch();

    List<ContentKey> allKeys =
        Stream.concat(
                Stream.of(ContentKey.of("b.b"), ContentKey.of("b.b", "c")),
                IntStream.range(0, 8).mapToObj(i -> ContentKey.of("b.b", "c", Integer.toString(i))))
            .collect(Collectors.toList());

    GetMultipleContentsResponse resp =
        api().getContent().refName(main.getName()).keys(allKeys).getWithResponse();

    ContentKey singleKey = ContentKey.of("b.b", "c", "3");
    soft.assertThat(api().getContent().refName(main.getName()).getSingle(singleKey))
        .extracting(ContentResponse::getEffectiveReference, ContentResponse::getContent)
        .containsExactly(
            main,
            IcebergTable.of("foo", 1, 2, 3, 4, committed.toAddedContentsMap().get(singleKey)));

    soft.assertThat(resp.getEffectiveReference()).isEqualTo(main);
    soft.assertThat(resp.toContentsMap()).containsOnlyKeys(allKeys).hasSize(allKeys.size());

    String mainName = main.getName();
    ContentKey key = ContentKey.of("b.b", "c", "1");
    soft.assertThat(api().getContent().refName(mainName).getSingle(key))
        .isEqualTo(
            ContentResponse.of(
                IcebergTable.of("foo", 1, 2, 3, 4, committed.toAddedContentsMap().get(key)), main));

    ContentKey nonExisting = ContentKey.of("not", "there");
    soft.assertThatThrownBy(() -> api().getContent().refName(mainName).getSingle(nonExisting))
        .isInstanceOf(NessieContentNotFoundException.class)
        .asInstanceOf(type(NessieContentNotFoundException.class))
        .extracting(NessieContentNotFoundException::getErrorDetails)
        .extracting(ContentKeyErrorDetails::contentKey)
        .isEqualTo(nonExisting);
  }

  @Test
  public void entries() throws Exception {
    Branch main0 = api().getDefaultBranch();

    Branch main =
        prepCommit(
                main0,
                "commit",
                Stream.concat(
                        Stream.of(Put.of(ContentKey.of("c"), Namespace.of("c"))),
                        IntStream.range(0, 9).mapToObj(i -> dummyPut("c", Integer.toString(i))))
                    .toArray(Put[]::new))
            .commit();

    EntriesResponse response = api().getEntries().reference(main).withContent(isV2()).get();
    List<Entry> notPaged = response.getEntries();
    soft.assertThat(notPaged).hasSize(10);
    if (isV2()) {
      soft.assertThat(response.getEffectiveReference()).isEqualTo(main);
      soft.assertThat(response.getEntries())
          .extracting(Entry::getContent)
          .doesNotContainNull()
          .isNotEmpty();

      if (fullPagingSupport()) {
        soft.assertThat(
                api()
                    .getEntries()
                    .reference(main)
                    .minKey(ContentKey.of("c", "2"))
                    .maxKey(ContentKey.of("c", "4"))
                    .get()
                    .getEntries())
            .extracting(Entry::getName)
            .containsExactlyInAnyOrder(
                ContentKey.of("c", "2"), ContentKey.of("c", "3"), ContentKey.of("c", "4"));
        soft.assertThat(
                api().getEntries().reference(main).prefixKey(ContentKey.of("c")).get().getEntries())
            .extracting(Entry::getName)
            .contains(ContentKey.of("c"))
            .containsAll(
                IntStream.range(0, 9)
                    .mapToObj(i -> ContentKey.of("c", Integer.toString(i)))
                    .collect(Collectors.toList()));
        soft.assertThat(
                api()
                    .getEntries()
                    .reference(main)
                    .key(ContentKey.of("c", "2"))
                    .key(ContentKey.of("c", "4"))
                    .get()
                    .getEntries())
            .extracting(Entry::getName)
            .containsExactlyInAnyOrder(ContentKey.of("c", "2"), ContentKey.of("c", "4"));
        soft.assertThat(
                api()
                    .getEntries()
                    .reference(main)
                    .prefixKey(ContentKey.of("c", "5"))
                    .get()
                    .getEntries())
            .extracting(Entry::getName)
            .containsExactlyInAnyOrder(ContentKey.of("c", "5"));
      }
    }

    if (pagingSupported(api().getEntries())) {
      List<Entry> all = new ArrayList<>();
      String token = null;
      for (int i = 0; i < 10; i++) {
        EntriesResponse resp =
            api()
                .getEntries()
                .withContent(isV2())
                .reference(main)
                .maxRecords(1)
                .pageToken(token)
                .get();
        all.addAll(resp.getEntries());
        token = resp.getToken();
        if (i == 9) {
          soft.assertThat(token).isNull();
        } else {
          soft.assertThat(token).isNotNull();
        }
      }

      soft.assertThat(all).containsExactlyElementsOf(notPaged);

      soft.assertAll();

      soft.assertThat(api().getEntries().withContent(isV2()).reference(main).maxRecords(1).stream())
          .containsExactlyInAnyOrderElementsOf(all);
    }
  }

  @NessieApiVersions(versions = NessieApiVersion.V2)
  @Test
  public void entryContentId() throws Exception {
    Branch main = prepCommit(api().getDefaultBranch(), "commit", dummyPut("test-table")).commit();

    soft.assertThat(api().getEntries().reference(main).stream())
        .isNotEmpty()
        .allSatisfy(e -> assertThat(e.getContentId()).isNotNull());
  }

  @Test
  public void entriesWithLimitInFirstPage() throws Exception {
    assumeFalse(pagingSupported(api().getEntries()));
    // Verify that result limiting produces expected errors when paging is not supported
    Branch main =
        prepCommit(api().getDefaultBranch(), "commit", dummyPut("t1"), dummyPut("t2")).commit();
    assertThatThrownBy(() -> api().getEntries().maxRecords(1).reference(main).get())
        .isInstanceOf(NessieBadRequestException.class)
        .hasMessageContaining("Paging not supported");
  }

  @Test
  public void namespaces() throws Exception {
    Branch main = api().getDefaultBranch();
    String mainName = main.getName();

    soft.assertThat(
            api()
                .getMultipleNamespaces()
                .reference(main)
                .namespace(Namespace.EMPTY)
                .get()
                .getNamespaces())
        .isEmpty();

    Namespace namespace1 = Namespace.of("a");
    Namespace namespace2 = Namespace.of("a", "b.b");
    Namespace namespace3 = Namespace.of("a", "b.bbbb");
    Namespace namespace4 = Namespace.of("a", "b.b", "c");
    Namespace namespace1WithId;
    Namespace namespace2WithId;
    Namespace namespace3WithId;
    Namespace namespace4WithId;

    Function<String, CommitMeta> buildMeta =
        msg ->
            CommitMeta.builder()
                .authorTime(Instant.EPOCH)
                .author("NessieHerself")
                .signedOffBy("Arctic")
                .message(msg + " my namespace with commit meta")
                .putProperties("property", "value")
                .build();
    BiConsumer<Reference, String> checkMeta =
        (ref, msg) -> {
          try (Stream<LogEntry> log = api().getCommitLog().reference(ref).maxRecords(1).stream()) {
            soft.assertThat(log)
                .first()
                .extracting(LogEntry::getCommitMeta)
                .extracting(
                    CommitMeta::getMessage,
                    CommitMeta::getAllAuthors,
                    CommitMeta::getAuthorTime,
                    CommitMeta::getAllSignedOffBy,
                    CommitMeta::getProperties)
                .containsExactly(
                    msg + " my namespace with commit meta",
                    singletonList("NessieHerself"),
                    Instant.EPOCH,
                    singletonList("Arctic"),
                    singletonMap("property", "value"));
          } catch (NessieNotFoundException e) {
            throw new RuntimeException(e);
          }
        };

    if (isV2()) {
      CreateNamespaceResult resp1 =
          api()
              .createNamespace()
              .refName(mainName)
              .namespace(namespace1)
              .commitMeta(buildMeta.apply("Create"))
              .createWithResponse();
      soft.assertThat(resp1.getEffectiveBranch()).isNotNull().isNotEqualTo(main);
      checkMeta.accept(resp1.getEffectiveBranch(), "Create");

      CreateNamespaceResult resp2 =
          api().createNamespace().refName(mainName).namespace(namespace2).createWithResponse();
      soft.assertThat(resp2.getEffectiveBranch())
          .isNotNull()
          .isNotEqualTo(main)
          .isNotEqualTo(resp1.getEffectiveBranch());
      CreateNamespaceResult resp3 =
          api().createNamespace().refName(mainName).namespace(namespace3).createWithResponse();
      soft.assertThat(resp3.getEffectiveBranch())
          .isNotNull()
          .isNotEqualTo(resp1.getEffectiveBranch())
          .isNotEqualTo(resp2.getEffectiveBranch());
      CreateNamespaceResult resp4 =
          api().createNamespace().refName(mainName).namespace(namespace4).createWithResponse();
      soft.assertThat(resp4.getEffectiveBranch())
          .isNotNull()
          .isNotEqualTo(resp2.getEffectiveBranch())
          .isNotEqualTo(resp3.getEffectiveBranch());
      namespace1WithId = resp1.getNamespace();
      namespace2WithId = resp2.getNamespace();
      namespace3WithId = resp3.getNamespace();
      namespace4WithId = resp4.getNamespace();

      for (Map.Entry<Namespace, List<Namespace>> c :
          ImmutableMap.<Namespace, List<Namespace>>of(
                  Namespace.EMPTY,
                  singletonList(namespace1WithId),
                  namespace1,
                  asList(namespace2WithId, namespace3WithId),
                  namespace2,
                  singletonList(namespace4WithId),
                  namespace3,
                  emptyList(),
                  namespace4,
                  emptyList())
              .entrySet()) {
        soft.assertThat(
                api()
                    .getMultipleNamespaces()
                    .refName(mainName)
                    .namespace(c.getKey())
                    .onlyDirectChildren(true)
                    .get()
                    .getNamespaces())
            .describedAs("for namespace %s", c.getKey())
            .containsExactlyInAnyOrderElementsOf(c.getValue());
      }
    } else {
      namespace1WithId = api().createNamespace().refName(mainName).namespace(namespace1).create();
      namespace2WithId = api().createNamespace().refName(mainName).namespace(namespace2).create();
      namespace3WithId = api().createNamespace().refName(mainName).namespace(namespace3).create();
      namespace4WithId = api().createNamespace().refName(mainName).namespace(namespace4).create();
    }

    for (Map.Entry<Namespace, List<Namespace>> c :
        ImmutableMap.of(
                Namespace.EMPTY,
                asList(namespace1WithId, namespace2WithId, namespace3WithId, namespace4WithId),
                namespace1,
                asList(namespace1WithId, namespace2WithId, namespace3WithId, namespace4WithId),
                namespace2,
                asList(namespace2WithId, namespace4WithId),
                namespace3,
                singletonList(namespace3WithId),
                namespace4,
                singletonList(namespace4WithId))
            .entrySet()) {
      soft.assertThat(
              api()
                  .getMultipleNamespaces()
                  .refName(mainName)
                  .namespace(c.getKey())
                  .get()
                  .getNamespaces())
          .describedAs("for namespace %s", c.getKey())
          .containsExactlyInAnyOrderElementsOf(c.getValue());
    }

    GetNamespacesResponse getMultiple =
        api().getMultipleNamespaces().refName(mainName).namespace(Namespace.EMPTY).get();
    if (isV2()) {
      main = (Branch) api().getReference().refName(mainName).get();
      soft.assertThat(getMultiple.getEffectiveReference()).isEqualTo(main);
    }
    soft.assertThat(getMultiple.getNamespaces())
        .containsExactlyInAnyOrder(
            namespace1WithId, namespace2WithId, namespace3WithId, namespace4WithId);
    soft.assertThat(
            api()
                .getMultipleNamespaces()
                .refName(mainName)
                .namespace(namespace1)
                .get()
                .getNamespaces())
        .containsExactlyInAnyOrder(
            namespace1WithId, namespace2WithId, namespace3WithId, namespace4WithId);
    soft.assertThat(
            api()
                .getMultipleNamespaces()
                .refName(mainName)
                .namespace(namespace2)
                .get()
                .getNamespaces())
        .containsExactlyInAnyOrder(namespace2WithId, namespace4WithId);

    soft.assertThat(
            api()
                .getContent()
                .refName(mainName)
                .key(namespace1.toContentKey())
                .key(namespace2.toContentKey())
                .key(namespace3.toContentKey())
                .key(namespace4.toContentKey())
                .get())
        .containsEntry(namespace1.toContentKey(), namespace1WithId)
        .containsEntry(namespace2.toContentKey(), namespace2WithId)
        .containsEntry(namespace3.toContentKey(), namespace3WithId)
        .containsEntry(namespace4.toContentKey(), namespace4WithId);

    soft.assertThat(api().getNamespace().refName(mainName).namespace(namespace1).get())
        .isEqualTo(namespace1WithId);
    soft.assertThat(api().getNamespace().refName(mainName).namespace(namespace2).get())
        .isEqualTo(namespace2WithId);
    soft.assertThat(api().getNamespace().refName(mainName).namespace(namespace3).get())
        .isEqualTo(namespace3WithId);
    soft.assertThat(api().getNamespace().refName(mainName).namespace(namespace4).get())
        .isEqualTo(namespace4WithId);

    if (isV2()) {
      main = (Branch) api().getReference().refName(mainName).get();
      UpdateNamespaceResult update =
          api()
              .updateProperties()
              .refName(mainName)
              .namespace(namespace2)
              .commitMeta(buildMeta.apply("Update"))
              .updateProperty("foo", "bar")
              .updateProperty("bar", "baz")
              .updateWithResponse();
      soft.assertThat(update.getEffectiveBranch()).isNotNull().isNotEqualTo(main);
      checkMeta.accept(update.getEffectiveBranch(), "Update");
    } else {
      api()
          .updateProperties()
          .refName(mainName)
          .namespace(namespace2)
          .updateProperty("foo", "bar")
          .updateProperty("bar", "baz")
          .update();
    }
    Namespace namespace2update =
        (Namespace)
            api()
                .getContent()
                .refName(mainName)
                .key(ContentKey.of("a", "b.b"))
                .get()
                .get(ContentKey.of("a", "b.b"));
    soft.assertThat(api().getNamespace().refName(mainName).namespace(namespace2).get())
        .isEqualTo(namespace2update);

    soft.assertThat(
            api()
                .getMultipleNamespaces()
                .refName(mainName)
                .namespace(Namespace.EMPTY)
                .get()
                .getNamespaces())
        .containsExactlyInAnyOrder(
            namespace1WithId, namespace2update, namespace3WithId, namespace4WithId);

    if (isV2()) {
      UpdateNamespaceResult updateResponse =
          api()
              .updateProperties()
              .refName(mainName)
              .namespace(namespace2)
              .removeProperty("foo")
              .updateWithResponse();
      soft.assertThat(updateResponse.getEffectiveBranch()).isNotEqualTo(main);
      main = updateResponse.getEffectiveBranch();
    } else {
      api()
          .updateProperties()
          .refName(mainName)
          .namespace(namespace2)
          .removeProperty("foo")
          .update();
    }
    Namespace namespace2update2 =
        (Namespace)
            api()
                .getContent()
                .refName(mainName)
                .key(ContentKey.of("a", "b.b"))
                .get()
                .get(ContentKey.of("a", "b.b"));
    soft.assertThat(api().getNamespace().refName(mainName).namespace(namespace2).get())
        .isEqualTo(namespace2update2);

    soft.assertThat(
            api()
                .getMultipleNamespaces()
                .refName(mainName)
                .namespace(Namespace.EMPTY)
                .get()
                .getNamespaces())
        .containsExactlyInAnyOrder(
            namespace1WithId, namespace2update2, namespace3WithId, namespace4WithId);

    if (isV2()) {
      soft.assertThatThrownBy(
              () -> api().deleteNamespace().refName(mainName).namespace(namespace2).delete())
          .isInstanceOf(NessieNamespaceNotEmptyException.class)
          .asInstanceOf(type(NessieNamespaceNotEmptyException.class))
          .extracting(NessieNamespaceNotEmptyException::getErrorDetails)
          .extracting(ContentKeyErrorDetails::contentKey)
          .isEqualTo(namespace2.toContentKey());
    }

    if (isV2()) {
      main = (Branch) api().getReference().refName(mainName).get();
      DeleteNamespaceResult response =
          api()
              .deleteNamespace()
              .refName(mainName)
              .namespace(namespace4)
              .commitMeta(buildMeta.apply("Delete"))
              .deleteWithResponse();
      soft.assertThat(response.getEffectiveBranch()).isNotNull().isNotEqualTo(main);
      checkMeta.accept(response.getEffectiveBranch(), "Delete");
    } else {
      api().deleteNamespace().refName(mainName).namespace(namespace4).delete();
    }

    soft.assertThat(api().getContent().refName(mainName).key(ContentKey.of("a", "b.b", "c")).get())
        .isEmpty();

    soft.assertThatThrownBy(
            () -> api().getNamespace().refName(mainName).namespace(namespace4).get())
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .asInstanceOf(type(NessieNamespaceNotFoundException.class))
        .extracting(NessieNamespaceNotFoundException::getErrorDetails)
        .extracting(ContentKeyErrorDetails::contentKey)
        .isEqualTo(namespace4.toContentKey());

    soft.assertThatThrownBy(
            () -> api().deleteNamespace().refName(mainName).namespace(namespace4).delete())
        .isInstanceOf(NessieNamespaceNotFoundException.class);

    soft.assertThat(
            api()
                .getMultipleNamespaces()
                .refName(mainName)
                .namespace(Namespace.EMPTY)
                .get()
                .getNamespaces())
        .containsExactlyInAnyOrder(namespace1WithId, namespace2update2, namespace3WithId);

    // This one fails, if the namespace-path 'startswith' filter (REST v2) to check for child
    // content is incorrectly implemented.
    soft.assertThatCode(
            () -> api().deleteNamespace().refName(mainName).namespace(namespace2).delete())
        .doesNotThrowAnyException();
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2)
  public void commitLogForNamelessReference() throws BaseNessieClientServerException {
    Branch main = api().getDefaultBranch();
    Branch branch =
        createReference(Branch.of("commitLogForNamelessReference", main.getHash()), main.getName());
    for (int i = 0; i < 5; i++) {
      if (i == 0) {
        branch =
            prepCommit(
                    branch,
                    "c-" + i,
                    Put.of(ContentKey.of("c"), Namespace.of("c")),
                    dummyPut("c", Integer.toString(i)))
                .commit();
      } else {
        branch = prepCommit(branch, "c-" + i, dummyPut("c", Integer.toString(i))).commit();
      }
    }
    List<LogEntry> log =
        api().getCommitLog().hashOnRef(branch.getHash()).stream().collect(Collectors.toList());
    // Verifying size is sufficient to make sure the right log was retrieved
    assertThat(log).hasSize(5);
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2)
  public void testDiffByNamelessReference() throws BaseNessieClientServerException {
    Branch main = api().getDefaultBranch();
    Branch fromRef = createReference(Branch.of("testFrom", main.getHash()), main.getName());
    Branch toRef = createReference(Branch.of("testTo", main.getHash()), main.getName());
    toRef = prepCommit(toRef, "commit", dummyPut("c")).commit();

    soft.assertThat(api().getDiff().fromRef(fromRef).toHashOnRef(toRef.getHash()).get().getDiffs())
        .hasSize(1)
        .allSatisfy(
            diff -> {
              assertThat(diff.getKey()).isNotNull();
              assertThat(diff.getFrom()).isNull();
              assertThat(diff.getTo()).isNotNull();
            });

    // both nameless references
    soft.assertThat(
            api()
                .getDiff()
                .fromHashOnRef(fromRef.getHash())
                .toHashOnRef(toRef.getHash())
                .get()
                .getDiffs())
        .hasSize(1)
        .allSatisfy(
            diff -> {
              assertThat(diff.getKey()).isNotNull();
              assertThat(diff.getFrom()).isNull();
              assertThat(diff.getTo()).isNotNull();
            });

    // reverse to/from
    soft.assertThat(api().getDiff().fromHashOnRef(toRef.getHash()).toRef(fromRef).get().getDiffs())
        .hasSize(1)
        .allSatisfy(
            diff -> {
              assertThat(diff.getKey()).isNotNull();
              assertThat(diff.getFrom()).isNotNull();
              assertThat(diff.getTo()).isNull();
            });
  }

  @Test
  @NessieApiVersions(versions = NessieApiVersion.V2)
  public void fetchEntriesByNamelessReference() throws BaseNessieClientServerException {
    Branch main = api().getDefaultBranch();
    Branch branch =
        createReference(
            Branch.of("fetchEntriesByNamelessReference", main.getHash()), main.getName());
    ContentKey a = ContentKey.of("a");
    ContentKey b = ContentKey.of("b");
    IcebergTable ta = IcebergTable.of("path1", 42, 42, 42, 42);
    IcebergView tb = IcebergView.of("pathx", 1, 1, "select * from table", "Dremio");
    branch =
        api()
            .commitMultipleOperations()
            .branch(branch)
            .operation(Put.of(a, ta))
            .operation(Put.of(b, tb))
            .commitMeta(CommitMeta.fromMessage("commit 1"))
            .commit();
    List<Entry> entries = api().getEntries().hashOnRef(branch.getHash()).get().getEntries();
    soft.assertThat(entries)
        .map(e -> immutableEntry(e.getName(), e.getType()))
        .containsExactlyInAnyOrder(
            immutableEntry(a, Content.Type.ICEBERG_TABLE),
            immutableEntry(b, Content.Type.ICEBERG_VIEW));
  }
}
