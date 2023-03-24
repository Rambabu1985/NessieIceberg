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
package org.projectnessie.versioned.storage.telemetry;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.projectnessie.versioned.storage.common.config.StoreConfig;
import org.projectnessie.versioned.storage.common.exceptions.ObjNotFoundException;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.exceptions.RefAlreadyExistsException;
import org.projectnessie.versioned.storage.common.exceptions.RefConditionFailedException;
import org.projectnessie.versioned.storage.common.exceptions.RefNotFoundException;
import org.projectnessie.versioned.storage.common.persist.CloseableIterator;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.ObjType;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.Reference;

final class TelemetryPersist implements Persist {

  final Persist persist;
  final Function<String, Traced> tracerSupplier;

  TelemetryPersist(Persist persist, Function<String, Traced> tracerSupplier) {
    this.persist = persist;
    this.tracerSupplier = tracerSupplier;
  }

  @SuppressWarnings("resource")
  Traced traced(String spanName) {
    String repo = persist.config().repositoryId();
    Traced traced = tracerSupplier.apply(spanName);
    return repo != null && !repo.isEmpty() ? traced.attribute("repo", repo) : traced;
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference addReference(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefAlreadyExistsException {
    try (Traced trace = traced("addReference")) {
      try {
        return persist.addReference(reference);
      } catch (RefAlreadyExistsException e) {
        trace.attribute("error", "already exists");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference markReferenceAsDeleted(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefNotFoundException, RefConditionFailedException {
    try (Traced trace = traced("markReferenceAsDeleted")) {
      try {
        return persist.markReferenceAsDeleted(reference);
      } catch (RefNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RefConditionFailedException e) {
        trace.attribute("error", "conditional update failed");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public void purgeReference(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefNotFoundException, RefConditionFailedException {
    try (Traced trace = traced("purgeReference")) {
      try {
        persist.purgeReference(reference);
      } catch (RefNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RefConditionFailedException e) {
        trace.attribute("error", "conditional update failed");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference updateReferencePointer(
      @Nonnull @jakarta.annotation.Nonnull Reference reference,
      @Nonnull @jakarta.annotation.Nonnull ObjId newPointer)
      throws RefNotFoundException, RefConditionFailedException {
    try (Traced trace = traced("updateReferencePointer")) {
      try {
        return persist.updateReferencePointer(reference, newPointer);
      } catch (RefNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RefConditionFailedException e) {
        trace.attribute("error", "conditional update failed");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public Reference fetchReference(@Nonnull @jakarta.annotation.Nonnull String name) {
    try (Traced trace = traced("fetchReference")) {
      try {
        Reference result = persist.fetchReference(name);
        trace.attribute("found", result != null);
        return result;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference[] fetchReferences(@Nonnull @jakarta.annotation.Nonnull String[] names) {
    try (Traced trace = traced("fetchReferences").attribute("names.length", names.length)) {
      try {
        Reference[] result = persist.fetchReferences(names);
        trace.attribute("result.length", stream(result).filter(Objects::nonNull).count());
        return result;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Obj fetchObj(@Nonnull @jakarta.annotation.Nonnull ObjId id) throws ObjNotFoundException {
    try (Traced trace = traced("fetchObj")) {
      try {
        Obj o = persist.fetchObj(id);
        trace.attribute("type", o.type().name());
        return o;
      } catch (ObjNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public <T extends Obj> T fetchTypedObj(
      @Nonnull @jakarta.annotation.Nonnull ObjId id, ObjType type, Class<T> typeClass)
      throws ObjNotFoundException {
    try (Traced trace = traced("fetchTypedObj").attribute("type", type.name())) {
      try {
        return persist.fetchTypedObj(id, type, typeClass);
      } catch (ObjNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public ObjType fetchObjType(@Nonnull @jakarta.annotation.Nonnull ObjId id)
      throws ObjNotFoundException {
    try (Traced trace = traced("fetchObjType")) {
      try {
        return persist.fetchObjType(id);
      } catch (ObjNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Obj[] fetchObjs(@Nonnull @jakarta.annotation.Nonnull ObjId[] ids)
      throws ObjNotFoundException {
    try (Traced trace = traced("fetchObjs").attribute("ids.length", ids.length)) {
      try {
        Obj[] objs = persist.fetchObjs(ids);
        stream(objs)
            .filter(Objects::nonNull)
            .collect(groupingBy(Obj::type, counting()))
            .forEach((t, c) -> trace.attribute("type." + t.name() + ".count", c));
        return objs;
      } catch (ObjNotFoundException e) {
        trace.attribute("error", "not found");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public boolean storeObj(
      @Nonnull @jakarta.annotation.Nonnull Obj obj, boolean ignoreSoftSizeRestrictions)
      throws ObjTooLargeException {
    try (Traced trace = traced("storeObj").attribute("type", obj.type().name())) {
      try {
        return persist.storeObj(obj, ignoreSoftSizeRestrictions);
      } catch (ObjTooLargeException e) {
        trace.attribute("error", "too large");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public boolean[] storeObjs(@Nonnull @jakarta.annotation.Nonnull Obj[] objs)
      throws ObjTooLargeException {
    try (Traced trace = traced("storeObjs").attribute("objs.length", objs.length)) {
      stream(objs)
          .collect(groupingBy(Obj::type, counting()))
          .forEach((t, c) -> trace.attribute("type." + t.name() + ".count", c));
      try {
        boolean[] result = persist.storeObjs(objs);
        int successes = 0;
        for (boolean b : result) {
          if (b) {
            successes++;
          }
        }
        trace.attribute("created.count", successes);
        return result;
      } catch (ObjTooLargeException e) {
        trace.attribute("error", "too large");
        throw e;
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public void deleteObj(@Nonnull @jakarta.annotation.Nonnull ObjId id) {
    try (Traced trace = traced("deleteObj")) {
      try {
        persist.deleteObj(id);
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public void deleteObjs(@Nonnull @jakarta.annotation.Nonnull ObjId[] ids) {
    try (Traced trace = traced("deleteObjs").attribute("ids.length", ids.length)) {
      try {
        persist.deleteObjs(ids);
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public void upsertObj(@Nonnull @jakarta.annotation.Nonnull Obj obj) throws ObjTooLargeException {
    try (Traced trace = traced("upsertObj")) {
      try {
        persist.upsertObj(obj);
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public void upsertObjs(@Nonnull @jakarta.annotation.Nonnull Obj[] objs)
      throws ObjTooLargeException {
    try (Traced trace = traced("upsertObjs")) {
      try {
        persist.upsertObjs(objs);
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public CloseableIterator<Obj> scanAllObjects(
      @Nonnull @jakarta.annotation.Nonnull Set<ObjType> returnedObjTypes) {
    try (Traced trace = traced("scanAllObjects")) {
      try {
        return persist.scanAllObjects(returnedObjTypes);
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  @Override
  public void erase() {
    try (Traced trace = traced("erase")) {
      try {
        persist.erase();
      } catch (RuntimeException e) {
        throw trace.unhandledError(e);
      }
    }
  }

  // Simple delegates

  @Override
  public int hardObjectSizeLimit() {
    return persist.hardObjectSizeLimit();
  }

  @Override
  public int effectiveIndexSegmentSizeLimit() {
    return persist.effectiveIndexSegmentSizeLimit();
  }

  @Override
  public int effectiveIncrementalIndexSizeLimit() {
    return persist.effectiveIncrementalIndexSizeLimit();
  }

  @Override
  @Nonnull
  public StoreConfig config() {
    return persist.config();
  }

  @Override
  @Nonnull
  public String name() {
    return persist.name();
  }
}
