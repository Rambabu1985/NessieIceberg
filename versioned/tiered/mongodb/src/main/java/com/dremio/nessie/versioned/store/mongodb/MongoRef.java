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

import static com.dremio.nessie.versioned.store.mongodb.MongoSerDe.deserializeId;
import static com.dremio.nessie.versioned.store.mongodb.MongoSerDe.deserializeIds;
import static com.dremio.nessie.versioned.store.mongodb.MongoSerDe.deserializeKeyMutations;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bson.BsonWriter;
import org.bson.Document;

import com.dremio.nessie.tiered.builder.Ref;
import com.dremio.nessie.versioned.Key;
import com.dremio.nessie.versioned.store.Id;
import com.google.common.primitives.Ints;

final class MongoRef extends MongoBaseValue<Ref> implements Ref, Ref.Tag, Ref.Branch {

  static final String TYPE = "type";
  static final String NAME = "name";
  static final String COMMIT = "commit";
  static final String COMMITS = "commits";
  static final String DELTAS = "deltas";
  static final String PARENT = "parent";
  static final String POSITION = "position";
  static final String NEW_ID = "new";
  static final String OLD_ID = "old";
  static final String REF_TYPE_BRANCH = "b";
  static final String REF_TYPE_TAG = "t";
  static final String TREE = "tree";
  static final String METADATA = "metadata";
  static final String KEY_LIST = "keys";

  static void produce(Document document, Ref v) {
    v = produceBase(document, v)
        .name(document.getString(NAME));
    String t = document.getString(TYPE);
    switch (t) {
      case REF_TYPE_TAG:
        v.tag().commit(deserializeId(document, COMMIT));
        break;
      case REF_TYPE_BRANCH:
        v.branch()
            .metadata(deserializeId(document, METADATA))
            .children(deserializeIds(document, TREE))
            .commits(bc -> deserializeBranchCommits(bc, document));
        break;
      default:
        throw new IllegalArgumentException("Unknown ref-type " + t);
    }
  }

  private boolean tag;
  private boolean branch;

  MongoRef(BsonWriter bsonWriter) {
    super(bsonWriter);
  }

  @Override
  public Tag tag() {
    if (branch) {
      throw new IllegalStateException("branch() has already been called");
    }
    tag = true;
    serializeString(TYPE, REF_TYPE_TAG);
    return this;
  }

  @Override
  public Branch branch() {
    if (tag) {
      throw new IllegalStateException("tag() has already been called");
    }
    branch = true;
    serializeString(TYPE, REF_TYPE_BRANCH);
    return this;
  }

  @Override
  public Ref name(String name) {
    serializeString(NAME, name);
    return this;
  }

  @Override
  public Tag commit(Id commit) {
    serializeId(COMMIT, commit);
    return this;
  }

  @Override
  public Branch metadata(Id metadata) {
    serializeId(METADATA, metadata);
    return this;
  }

  @Override
  public Branch children(Stream<Id> children) {
    serializeIds(TREE, children);
    return this;
  }

  @Override
  public Branch commits(Consumer<BranchCommit> commits) {
    addProperty(COMMITS);
    bsonWriter.writeStartArray(COMMITS);
    commits.accept(new MongoBranchCommit());
    bsonWriter.writeEndArray();
    return this;
  }

  private class MongoBranchCommit implements BranchCommit, SavedCommit, UnsavedCommitDelta, UnsavedCommitMutations {

    int state;

    @Override
    public BranchCommit id(Id id) {
      maybeStart();
      assertState(1);
      MongoSerDe.serializeId(bsonWriter, ID, id);
      return this;
    }

    @Override
    public BranchCommit commit(Id commit) {
      maybeStart();
      assertState(1);
      MongoSerDe.serializeId(bsonWriter, COMMIT, commit);
      return this;
    }

    @Override
    public SavedCommit saved() {
      return this;
    }

    @Override
    public UnsavedCommitDelta unsaved() {
      return this;
    }

    @Override
    public SavedCommit parent(Id parent) {
      maybeStart();
      assertState(1);
      MongoSerDe.serializeId(bsonWriter, PARENT, parent);
      return this;
    }

    @Override
    public UnsavedCommitDelta delta(int position, Id oldId, Id newId) {
      maybeStart();
      if (state == 1) {
        state = 2;
        bsonWriter.writeStartArray(DELTAS);
      }
      assertState(2);
      serializeDelta(bsonWriter, position, oldId, newId);
      return this;
    }

    @Override
    public UnsavedCommitMutations mutations() {
      return this;
    }

    @Override
    public UnsavedCommitMutations keyMutation(Key.Mutation keyMutation) {
      maybeStart();
      if (state == 2) {
        state = 1;
        bsonWriter.writeEndArray();
      }
      if (state == 1) {
        state = 3;
        bsonWriter.writeStartArray(KEY_LIST);
      }
      assertState(3);
      MongoSerDe.serializeKeyMutation(bsonWriter, keyMutation);
      return this;
    }

    private void maybeStart() {
      if (state == 0) {
        bsonWriter.writeStartDocument();
        state = 1;
      }
    }

    private void assertState(int expected) {
      if (state != expected) {
        throw new IllegalStateException(
            "Wrong order or consumer method invocations (" + expected + " != " + state
                + ". See Javadocs.");
      }
    }

    @Override
    public BranchCommit done() {
      if (state == 3 || state == 2) {
        bsonWriter.writeEndArray();
        state = 1;
      }
      if (state == 1) {
        bsonWriter.writeEndDocument();
        state = 0;
      }
      return this;
    }
  }

  @Override
  BsonWriter build() {
    checkPresent(NAME, "name");
    checkPresent(TYPE, "type");

    if (tag) {
      checkPresent(COMMIT, "commit");
      checkNotPresent(COMMITS, "commits");
      checkNotPresent(TREE, "tree");
      checkNotPresent(METADATA, "metadata");
    } else if (branch) {
      checkNotPresent(COMMIT, "commit");
      checkPresent(COMMITS, "commits");
      checkPresent(TREE, "tree");
      checkPresent(METADATA, "metadata");
    } else {
      throw new IllegalStateException("Neither tag() nor branch() has been called");
    }

    return super.build();
  }

  private void serializeDelta(BsonWriter writer, int position, Id oldId, Id newId) {
    writer.writeStartDocument();
    serializeLong(POSITION, position);
    MongoSerDe.serializeId(writer, OLD_ID, oldId);
    MongoSerDe.serializeId(writer, NEW_ID, newId);
    writer.writeEndDocument();
  }

  static void deserializeBranchCommits(BranchCommit bc, Document d) {
    List<Document> lst = (List<Document>) d.get(COMMITS);
    lst.forEach(c -> deserializeBranchCommit(bc, c));
  }

  private static void deserializeBranchCommit(BranchCommit consumer, Document d) {
    consumer = consumer.id(deserializeId(d, ID))
        .commit(deserializeId(d, COMMIT));
    if (d.containsKey(PARENT)) {
      consumer.saved().parent(deserializeId(d, PARENT)).done();
    } else {
      UnsavedCommitDelta unsaved = consumer.unsaved();
      List<Document> deltas = (List<Document>) d.get(DELTAS);
      deltas.forEach(delta -> deserializeUnsavedDelta(unsaved, delta));
      UnsavedCommitMutations mutations = unsaved.mutations();
      deserializeKeyMutations(d, KEY_LIST).forEach(mutations::keyMutation);
      mutations.done();
    }
  }

  private static void deserializeUnsavedDelta(UnsavedCommitDelta consumer, Document d) {
    int position = Ints.saturatedCast(d.getLong(POSITION));
    Id oldId = deserializeId(d, OLD_ID);
    Id newId = deserializeId(d, NEW_ID);
    consumer.delta(position, oldId, newId);
  }
}
