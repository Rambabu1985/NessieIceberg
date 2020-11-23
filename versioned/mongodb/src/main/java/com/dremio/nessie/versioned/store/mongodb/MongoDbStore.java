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
package com.dremio.nessie.versioned.store.mongodb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dremio.nessie.versioned.ReferenceNotFoundException;
import com.dremio.nessie.versioned.impl.Fragment;
import com.dremio.nessie.versioned.impl.InternalCommitMetadata;
import com.dremio.nessie.versioned.impl.InternalRef;
import com.dremio.nessie.versioned.impl.InternalValue;
import com.dremio.nessie.versioned.impl.L1;
import com.dremio.nessie.versioned.impl.L2;
import com.dremio.nessie.versioned.impl.L3;
import com.dremio.nessie.versioned.impl.condition.ConditionExpression;
import com.dremio.nessie.versioned.impl.condition.UpdateExpression;
import com.dremio.nessie.versioned.store.Id;
import com.dremio.nessie.versioned.store.LoadStep;
import com.dremio.nessie.versioned.store.SaveOp;
import com.dremio.nessie.versioned.store.Store;
import com.dremio.nessie.versioned.store.ValueType;
import com.dremio.nessie.versioned.store.mongodb.codecs.CodecProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;

/**
 * This class implements the Store interface that is used by Nessie as a backing store for versioning of it's
 * Git like behaviour.
 * The MongoDbStore connects to an external MongoDB server.
 */
public class MongoDbStore implements Store {
  private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbStore.class);

  private final String databaseName;
  private final MongoClientSettings mongoClientSettings;

  // The client that connects to the MongoDB server.
  protected MongoClient mongoClient;

  // The database hosted by the MongoDB server.
  private MongoDatabase mongoDatabase;
  protected final Map<ValueType, MongoCollection<?>> collections;

  /**
   * Creates a store ready for connection to a MongoDB instance.
   * @param connectionString the string to use to connect to MongoDB.
   * @param databaseName the name of the database to retrieve
   */
  public MongoDbStore(ConnectionString connectionString, String databaseName) {
    this.collections = new HashMap<>();
    this.databaseName = databaseName;
    final CodecRegistry codecRegistry = CodecRegistries.fromProviders(
        new CodecProvider(),
        PojoCodecProvider.builder().automatic(true).build(),
        MongoClientSettings.getDefaultCodecRegistry());
    this.mongoClientSettings = MongoClientSettings.builder()
      .applyConnectionString(connectionString)
      .codecRegistry(codecRegistry)
      .build();
  }

  /**
   * Gets a handle to an existing database or get a handle to a MongoDatabase instance if it does not exist. The new
   * database will be lazily created.
   * Since MongoDB creates databases and collections if they do not exist, there is no need to validate the presence of
   * either before they are used. This creates or retrieves collections that map 1:1 to the enumerates in
   * {@link com.dremio.nessie.versioned.store.ValueType}
   */
  @Override
  public void start() {
    mongoClient = MongoClients.create(mongoClientSettings);
    mongoDatabase = mongoClient.getDatabase(databaseName);

    // Initialise collections for each ValueType.
    collections.put(ValueType.L1, mongoDatabase.getCollection(MongoDbConstants.L1_COLLECTION, L1.class));
    collections.put(ValueType.L2, mongoDatabase.getCollection(MongoDbConstants.L2_COLLECTION, L2.class));
    collections.put(ValueType.L3, mongoDatabase.getCollection(MongoDbConstants.L3_COLLECTION, L3.class));
    collections.put(ValueType.COMMIT_METADATA,
        mongoDatabase.getCollection(MongoDbConstants.COMMIT_METADATA_COLLECTION, InternalCommitMetadata.class));
    collections.put(ValueType.KEY_FRAGMENT,
        mongoDatabase.getCollection(MongoDbConstants.KEY_FRAGMENT_COLLECTION, Fragment.class));
    collections.put(ValueType.REF, mongoDatabase.getCollection(MongoDbConstants.REF_COLLECTION, InternalRef.class));
    collections.put(ValueType.VALUE,
        mongoDatabase.getCollection(MongoDbConstants.VALUE_COLLECTION, InternalValue.class));
  }

  /**
   * Closes the connection this manager creates to a database. If the connection is already closed this method has
   * no effect.
   */
  @Override
  public void close() {
    if (null != mongoClient) {
      mongoClient.close();
    }
  }

  @Override
  public void load(LoadStep loadstep) throws ReferenceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <V> boolean putIfAbsent(ValueType type, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <V> void put(ValueType type, V value, Optional<ConditionExpression> conditionUnAliased) {
    Preconditions.checkArgument(type.getObjectClass().isAssignableFrom(value.getClass()),
        "ValueType %s doesn't extend expected type %s.", value.getClass().getName(), type.getObjectClass().getName());

    LOGGER.info("ValueType: {}, Value: {}", type.toString(), value.toString());
    final MongoCollection collection = collections.get(type);
    if (null == collection) {
      throw new UnsupportedOperationException(String.format("Unsupported Entity type: %s", type.name()));
    }
    collection.insertOne(value);
  }

  @Override
  public boolean delete(ValueType type, Id id, Optional<ConditionExpression> condition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void save(List<SaveOp<?>> ops) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <V> V loadSingle(ValueType valueType, Id id) {
    final MongoCollection collection = collections.get(valueType);
    if (null == collection) {
      throw new UnsupportedOperationException(String.format("Unsupported Entity type: %s", valueType.name()));
    }
    BasicDBObject query = new BasicDBObject(Store.KEY_NAME,
        new BasicDBObject("$eq", id));
    return (V)Iterables.get(collection.find(query), 0);
  }

  @Override
  public <V> Optional<V> update(ValueType type, Id id, UpdateExpression update, Optional<ConditionExpression> condition)
      throws ReferenceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<InternalRef> getRefs() {
    throw new UnsupportedOperationException();
  }

  @VisibleForTesting
  public MongoDatabase getMongoDatabase() {
    return mongoDatabase;
  }
}
