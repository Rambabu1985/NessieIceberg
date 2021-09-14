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
import org.bson.Document;

public class MongoDatabaseClient {

  private static final String GLOBAL_POINTER = "global_pointer";
  private static final String GLOBAL_LOG = "global_log";
  private static final String COMMIT_LOG = "commit_log";
  private static final String KEY_LIST = "key_list";

  private final MongoDatabaseAdapterConfig config;
  private MongoClient mongoClient;
  private MongoCollection<Document> globalPointers;
  private MongoCollection<Document> globalLog;
  private MongoCollection<Document> commitLog;
  private MongoCollection<Document> keyLists;
  private int acquired;

  protected MongoDatabaseClient(MongoDatabaseAdapterConfig config) {
    this.config = config;
  }

  synchronized void acquire() {
    if (acquired++ > 0) {
      return;
    }

    ConnectionString cs = new ConnectionString(config.getConnectionString());
    MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(cs).build();

    mongoClient = MongoClients.create(settings);
    MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
    globalPointers = database.getCollection(GLOBAL_POINTER);
    globalLog = database.getCollection(GLOBAL_LOG);
    commitLog = database.getCollection(COMMIT_LOG);
    keyLists = database.getCollection(KEY_LIST);
  }

  synchronized void release() {
    if (--acquired > 0) {
      return;
    }

    mongoClient.close();
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
}
