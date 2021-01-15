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
package com.dremio.nessie.versioned.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.dremio.nessie.tiered.builder.BaseConsumer;
import com.dremio.nessie.tiered.builder.L1Consumer;
import com.dremio.nessie.versioned.impl.InternalBranch.UpdateState;
import com.dremio.nessie.versioned.impl.condition.ConditionExpression;
import com.dremio.nessie.versioned.impl.condition.UpdateExpression;
import com.dremio.nessie.versioned.store.Id;
import com.dremio.nessie.versioned.store.LoadStep;
import com.dremio.nessie.versioned.store.NotFoundException;
import com.dremio.nessie.versioned.store.SaveOp;
import com.dremio.nessie.versioned.store.Store;
import com.dremio.nessie.versioned.store.ValueType;

class TestVersionEquality {

  /**
   * Make sure that a new branch has the L1.EMPTY_ID identifier.
   */
  @Test
  void internalBranchL1IdEqualsEmpty() {
    InternalBranch b = new InternalBranch("n/a");
    Store store = new MockStore() {
      @Override
      public <C extends BaseConsumer<C>> void loadSingle(ValueType<C> type, Id id, C consumer) {
        L1.EMPTY.applyToConsumer((L1Consumer) consumer);
      }

    };
    UpdateState us = b.getUpdateState(store);
    us.ensureAvailable(null, null, 1, true);
    assertEquals(L1.EMPTY_ID, us.getL1().getId());
  }

  @Test
  void correctSize() {
    assertEquals(0, L1.EMPTY.size());
    assertEquals(0, L2.EMPTY.size());
    assertEquals(0, L3.EMPTY.size());
  }

  private static class MockStore implements Store {
    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    @Override
    public void load(LoadStep loadstep) {
    }

    @Override
    public <C extends BaseConsumer<C>> boolean putIfAbsent(SaveOp<C> saveOp) {
      return false;
    }

    @Override
    public <C extends BaseConsumer<C>> void put(SaveOp<C> saveOp,
        Optional<ConditionExpression> condition) {
    }

    @Override
    public <C extends BaseConsumer<C>> boolean delete(ValueType<C> type, Id id, Optional<ConditionExpression> condition) {
      return false;
    }

    @Override
    public void save(List<SaveOp<?>> ops) {
    }

    @Override
    public <C extends BaseConsumer<C>> void loadSingle(ValueType<C> type, Id id, C consumer) {
    }

    @Override
    public <C extends BaseConsumer<C>> boolean update(ValueType<C> type, Id id,
        UpdateExpression update, Optional<ConditionExpression> condition,
        Optional<BaseConsumer<C>> consumer) throws NotFoundException {
      return false;
    }

    @Override
    public <C extends BaseConsumer<C>> Stream<Acceptor<C>> getValues(ValueType<C> type) {
      return null;
    }
  }
}
