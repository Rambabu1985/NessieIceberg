/*
 * Copyright (C) 2020 Dremio
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
package org.projectnessie.versioned.persist.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Objects;
import java.util.stream.Stream;
import org.bson.Document;
import org.projectnessie.versioned.persist.adapter.DatabaseConnectionProvider;

public class MongoDatabaseClient implements DatabaseConnectionProvider<MongoClientConfig> {

  private static final String REPO_DESC = "repo_desc";
  private static final String GLOBAL_POINTER = "global_pointer";
  private static final String GLOBAL_LOG = "global_log";
  private static final String COMMIT_LOG = "commit_log";
  private static final String KEY_LIST = "key_list";
  private static final String REF_LOG = "ref_log";
  static final String TABLE_REF_HEADS = "ref_heads";
  static final String TABLE_REF_NAMES = "ref_names";
  static final String TABLE_REF_LOG_HEADS = "ref_log_heads";
  private static final String ATTACHMENTS = "attachments";
  private static final String ATTACHMENT_KEYS = "attachment_keys";

  private MongoClientConfig config;
  private MongoClient managedClient;
  private MongoCollection<Document> repoDesc;
  private MongoCollection<Document> globalPointers;
  private MongoCollection<Document> globalLog;
  private MongoCollection<Document> commitLog;
  private MongoCollection<Document> keyLists;
  private MongoCollection<Document> refLog;
  private MongoCollection<Document> refHeads;
  private MongoCollection<Document> refNames;
  private MongoCollection<Document> refLogHeads;
  private MongoCollection<Document> attachments;
  private MongoCollection<Document> attachmentKeys;

  @Override
  public void configure(MongoClientConfig config) {
    this.config = config;
  }

  @Override
  public void close() {
    if (managedClient != null) {
      try {
        managedClient.close();
      } finally {
        managedClient = null;
      }
    }
  }

  @Override
  public void initialize() {
    MongoClient mongoClient = config.getClient();
    if (mongoClient == null) {
      ConnectionString cs =
          new ConnectionString(
              Objects.requireNonNull(
                  config.getConnectionString(), "Connection string must be set"));
      MongoClientSettings settings =
          MongoClientSettings.builder().applyConnectionString(cs).build();

      managedClient = MongoClients.create(settings);
      mongoClient = managedClient;
    }

    MongoDatabase database =
        mongoClient.getDatabase(
            Objects.requireNonNull(config.getDatabaseName(), "Database name must be set"));
    repoDesc = database.getCollection(REPO_DESC);
    globalPointers = database.getCollection(GLOBAL_POINTER);
    globalLog = database.getCollection(GLOBAL_LOG);
    commitLog = database.getCollection(COMMIT_LOG);
    keyLists = database.getCollection(KEY_LIST);
    refLog = database.getCollection(REF_LOG);
    refHeads = database.getCollection(TABLE_REF_HEADS);
    refNames = database.getCollection(TABLE_REF_NAMES);
    refLogHeads = database.getCollection(TABLE_REF_LOG_HEADS);
    attachments = database.getCollection(ATTACHMENTS);
    attachmentKeys = database.getCollection(ATTACHMENT_KEYS);
  }

  public MongoCollection<Document> getRepoDesc() {
    return repoDesc;
  }

  public MongoCollection<Document> getGlobalPointers() {
    return globalPointers;
  }

  public MongoCollection<Document> getGlobalLog() {
    return globalLog;
  }

  public MongoCollection<Document> getCommitLog() {
    return commitLog;
  }

  public MongoCollection<Document> getKeyLists() {
    return keyLists;
  }

  public MongoCollection<Document> getRefLog() {
    return refLog;
  }

  public MongoCollection<Document> getRefHeads() {
    return refHeads;
  }

  public MongoCollection<Document> getRefNames() {
    return refNames;
  }

  public MongoCollection<Document> getRefLogHeads() {
    return refLogHeads;
  }

  public MongoCollection<Document> getAttachments() {
    return attachments;
  }

  public MongoCollection<Document> getAttachmentKeys() {
    return attachmentKeys;
  }

  public Stream<MongoCollection<Document>> allWithCompositeId() {
    return Stream.of(
        globalLog,
        commitLog,
        keyLists,
        refLog,
        refHeads,
        refNames,
        refLogHeads,
        attachments,
        attachmentKeys);
  }
}
