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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import com.dremio.nessie.versioned.ReferenceNotFoundException;
import com.dremio.nessie.versioned.impl.InternalRef;
import com.dremio.nessie.versioned.impl.condition.ConditionExpression;
import com.dremio.nessie.versioned.impl.condition.UpdateExpression;
import com.dremio.nessie.versioned.store.HasId;
import com.dremio.nessie.versioned.store.Id;
import com.dremio.nessie.versioned.store.LoadOp;
import com.dremio.nessie.versioned.store.LoadStep;
import com.dremio.nessie.versioned.store.SaveOp;
import com.dremio.nessie.versioned.store.Store;
import com.dremio.nessie.versioned.store.ValueType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class implements the Store interface that is used by Nessie as a backing store for versioning of it's
 * Git like behaviour.
 * The MongoDbStore connects to an external MongoDB server.
 */
public class MongoDBStore implements Store {
  /**
   * Pair of a collection to the set of IDs to be loaded.
   */
  private static class CollectionLoadIds {
    final MongoCollection<? extends HasId> collection;
    final List<Id> ids;

    CollectionLoadIds(MongoCollection<? extends HasId> collection, List<Id> ops) {
      this.collection = collection;
      this.ids = ops;
    }
  }

  /**
   * Bson implementation for updates, to allow proper encoding of Nessie objects using codecs.
   * @param <T> the entity object type.
   */
  private static class UpdateEntityBson<T> implements Bson {
    private final T value;
    private final Class<T> valueClass;

    public UpdateEntityBson(Class<T> valueClass, T value) {
      this.valueClass = valueClass;
      this.value = value;
    }

    @Override
    public <TDocument> BsonDocument toBsonDocument(Class<TDocument> clazz, CodecRegistry codecRegistry) {
      // Intentionally don't use Updates.setOnInsert() as that will result in issues encoding the value entity,
      // due to codec lookups the MongoDB driver will actually encode the fields of the basic object, not the entity.
      final BsonDocumentWriter writer = new BsonDocumentWriter(new BsonDocument());
      writer.writeStartDocument();
      writer.writeName("$setOnInsert");
      final Codec<T> codec = codecRegistry.get(valueClass);
      codec.encode(writer, value, EncoderContext.builder().build());
      writer.writeEndDocument();
      return writer.getDocument();
    }
  }

  // Mongo has a 16MB limit on documents, which also pertains to the input query. Given that we use IN for loads,
  // restrict the number of IDs to avoid going above that limit, and to take advantage of the async nature of the
  // requests.
  @VisibleForTesting
  static final int LOAD_SIZE = 1_000;
  private static final Map<ValueType, Function<MongoStoreConfig, String>> typeCollections =
      ImmutableMap.<ValueType, Function<MongoStoreConfig, String>>builder()
          .put(ValueType.L1, MongoStoreConfig::getL1TableName)
          .put(ValueType.L2, MongoStoreConfig::getL2TableName)
          .put(ValueType.L3, MongoStoreConfig::getL3TableName)
          .put(ValueType.REF, MongoStoreConfig::getRefTableName)
          .put(ValueType.VALUE, MongoStoreConfig::getValueTableName)
          .put(ValueType.COMMIT_METADATA, MongoStoreConfig::getMetadataTableName)
          .put(ValueType.KEY_FRAGMENT, MongoStoreConfig::getKeyListTableName)
          .build();

  private final MongoStoreConfig config;
  private final MongoClientSettings mongoClientSettings;

  private MongoClient mongoClient;
  private MongoDatabase mongoDatabase;
  private final Duration timeout;
  private final Map<ValueType, MongoCollection<? extends HasId>> collections;

  /**
   * Creates a store ready for connection to a MongoDB instance.
   * @param config the configuration for the store.
   */
  public MongoDBStore(MongoStoreConfig config) {
    this.config = config;
    this.timeout = Duration.of(config.getTimeoutMs(), ChronoUnit.MILLIS);
    this.collections = new HashMap<>();
    final CodecRegistry codecRegistry = CodecRegistries.fromProviders(
        new CodecProvider(),
        PojoCodecProvider.builder().automatic(true).build(),
        MongoClientSettings.getDefaultCodecRegistry());
    this.mongoClientSettings = MongoClientSettings.builder()
      .applyConnectionString(new ConnectionString(config.getConnectionString()))
      .codecRegistry(codecRegistry)
      .writeConcern(WriteConcern.MAJORITY)
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
    mongoDatabase = mongoClient.getDatabase(config.getDatabaseName());

    // Initialise collections for each ValueType.
    typeCollections.forEach((k, v) ->
        collections.put(k, (MongoCollection<? extends HasId>)mongoDatabase.getCollection(v.apply(config), k.getObjectClass())));
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
    for (LoadStep step = loadstep; step != null; step = step.getNext().orElse(null)) {
      final Map<Id, LoadOp<?>> idLoadOps = step.getOps().collect(Collectors.toMap(LoadOp::getId, Function.identity()));

      Flux.fromStream(step.getOps())
        .groupBy(op -> collections.get(op.getValueType()))
        .flatMap(entry -> entry.map(LoadOp::getId).buffer(LOAD_SIZE).map(l -> new CollectionLoadIds(entry.key(), l)))
        .flatMap(entry -> entry.collection.find(Filters.in(KEY_NAME, entry.ids)))
        .handle((op, sink) -> {
          // Process each of the loaded entries.
          final LoadOp<?> loadOp = idLoadOps.remove(op.getId());
          if (null == loadOp) {
            sink.error(new ReferenceNotFoundException(String.format("Retrieved unexpected object with ID: %s", op.getId())));
          } else {
            final ValueType type = loadOp.getValueType();
            loadOp.loaded(type.addType(type.getSchema().itemToMap(op, true)));
            sink.next(op);
          }
        })
          .blockLast(timeout);

      // Check if there were any missed ops.
      final Collection<String> missedIds = idLoadOps.values().stream()
          .map(e -> e.getId().toString())
          .collect(Collectors.toList());
      if (!missedIds.isEmpty()) {
        throw new ReferenceNotFoundException(String.format("Requested object IDs missing: %s", String.join(", ", missedIds)));
      }
    }
  }

  @Override
  public <V> boolean putIfAbsent(ValueType type, V value) {
    final MongoCollection<V> collection = getCollection(type);

    // Use upsert so that a document is created if the filter does not match. The update operator is only $setOnInsert
    // so no action is triggered on a simple update, only on insert.
    final UpdateResult result = Mono.from(collection.updateOne(
        Filters.eq(Store.KEY_NAME, ((HasId)value).getId()),
        new UpdateEntityBson<>((Class<V>)type.getObjectClass(), value),
        new UpdateOptions().upsert(true))).block(timeout);
    return result.getUpsertedId() != null;
  }

  @Override
  public <V> void put(ValueType type, V value, Optional<ConditionExpression> conditionUnAliased) {
    Preconditions.checkArgument(type.getObjectClass().isAssignableFrom(value.getClass()),
        "ValueType %s doesn't extend expected type %s.", value.getClass().getName(), type.getObjectClass().getName());

    // TODO: Handle ConditionExpressions.
    if (conditionUnAliased.isPresent()) {
      throw new UnsupportedOperationException("ConditionExpressions are not supported with MongoDB yet.");
    }

    final MongoCollection<V> collection = getCollection(type);

    // Use upsert so that if an item does not exist, it will be insert.
    Mono.from(collection.replaceOne(Filters.eq(Store.KEY_NAME, ((HasId)value).getId()), value, new ReplaceOptions().upsert(true)))
        .block(timeout);
  }

  @Override
  public boolean delete(ValueType type, Id id, Optional<ConditionExpression> condition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void save(List<SaveOp<?>> ops) {
    final ListMultimap<MongoCollection<?>, SaveOp<?>> mm = Multimaps.index(ops, l -> collections.get(l.getType()));
    final ListMultimap<MongoCollection<?>, HasId> collectionWrites = Multimaps.transformValues(mm, SaveOp::getValue);

    Flux.fromIterable(Multimaps.asMap(collectionWrites).entrySet())
      .flatMap(entry -> {
        final MongoCollection collection = entry.getKey();

        // Ordering of the inserts doesn't matter, so set to unordered to give potential performance improvements.
        return collection.insertMany(entry.getValue(), new InsertManyOptions().ordered(false));
      })
        .blockLast(timeout);
  }

  @Override
  public <V> V loadSingle(ValueType valueType, Id id) {
    final MongoCollection<V> collection = getCollection(valueType);

    final V value = Mono.from(collection.find(Filters.eq(Store.KEY_NAME, id))).block(timeout);
    if (null == value) {
      throw new RuntimeException("Unable to load item with ID: " + id);
    }
    return value;
  }

  @Override
  public <V> Optional<V> update(ValueType type, Id id, UpdateExpression update, Optional<ConditionExpression> condition)
      throws ReferenceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<InternalRef> getRefs() {
    final MongoCollection<InternalRef> collection = getCollection(ValueType.REF);
    return Flux.from(collection.find()).toStream();
  }

  /**
   * Clear the contents of all the Nessie collections. Only for testing purposes.
   */
  @VisibleForTesting
  void resetCollections() {
    Flux.fromIterable(collections.values()).flatMap(collection -> collection.deleteMany(Filters.ne("_id", "s"))).blockLast(timeout);
  }

  private <T> MongoCollection<T> getCollection(ValueType valueType) {
    final MongoCollection<? extends HasId> collection = collections.get(valueType);
    if (null == collection) {
      throw new UnsupportedOperationException(String.format("Unsupported Entity type: %s", valueType.name()));
    }
    return (MongoCollection<T>) collection;
  }
}
