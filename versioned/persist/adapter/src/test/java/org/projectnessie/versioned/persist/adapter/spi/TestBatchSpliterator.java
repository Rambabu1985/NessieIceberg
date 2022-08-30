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
package org.projectnessie.versioned.persist.adapter.spi;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

public class TestBatchSpliterator {

  static class IntMapper implements Function<List<Integer>, Spliterator<String>> {
    final List<List<Integer>> inputs = new ArrayList<>();
    final Set<Integer> results;

    IntMapper(Set<Integer> results) {
      this.results = results;
    }

    @Override
    public Spliterator<String> apply(List<Integer> integers) {
      inputs.add(new ArrayList<>(integers));
      return integers.stream()
          .filter(results::contains)
          .map(Objects::toString)
          .collect(Collectors.toList())
          .spliterator();
    }
  }

  @Test
  public void emptyEmpty() {
    IntMapper intMapper = new IntMapper(Collections.emptySet());

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(0, 0).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).isEmpty();
    assertThat(intMapper.inputs).isEmpty();
  }

  @Test
  public void absolutelyNoResults() {
    IntMapper intMapper = new IntMapper(Collections.emptySet());

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).isEmpty();
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void oneResultFirst() {
    IntMapper intMapper = new IntMapper(Collections.singleton(10));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).containsExactly("10");
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void oneResultLast() {
    IntMapper intMapper = new IntMapper(Collections.singleton(19));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).containsExactly("19");
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void oneResultMiddle() {
    IntMapper intMapper = new IntMapper(Collections.singleton(11));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).containsExactly("11");
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void oneResultSecondPageFirst() {
    IntMapper intMapper = new IntMapper(Collections.singleton(13));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).containsExactly("13");
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void oneResultSecondPageLast() {
    IntMapper intMapper = new IntMapper(Collections.singleton(15));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false)).containsExactly("15");
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void allResults() {
    IntMapper intMapper =
        new IntMapper(IntStream.range(10, 20).boxed().collect(Collectors.toSet()));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false))
        .containsExactlyElementsOf(
            IntStream.range(10, 20).mapToObj(Integer::toString).collect(Collectors.toList()));
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void manyResults() {
    IntMapper intMapper =
        new IntMapper(IntStream.range(13, 18).boxed().collect(Collectors.toSet()));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed(),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false))
        .containsExactlyElementsOf(
            IntStream.range(13, 18).mapToObj(Integer::toString).collect(Collectors.toList()));
    assertThat(intMapper.inputs)
        .containsExactly(asList(10, 11, 12), asList(13, 14, 15), asList(16, 17, 18), asList(19));
  }

  @Test
  public void skipSome() {
    IntMapper intMapper =
        new IntMapper(IntStream.range(10, 20).boxed().collect(Collectors.toSet()));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed().skip(4),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false))
        .containsExactlyElementsOf(
            IntStream.range(14, 20).mapToObj(Integer::toString).collect(Collectors.toList()));
    assertThat(intMapper.inputs).containsExactly(asList(14, 15, 16), asList(17, 18, 19));
  }

  @Test
  public void skipSomeAndLimit() {
    IntMapper intMapper =
        new IntMapper(IntStream.range(10, 20).boxed().collect(Collectors.toSet()));

    BatchSpliterator<Integer, String> batchSpliterator =
        new BatchSpliterator<>(
            3,
            IntStream.range(10, 20).boxed().skip(4).limit(5),
            intMapper,
            Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.IMMUTABLE);

    assertThat(StreamSupport.stream(batchSpliterator, false))
        .containsExactlyElementsOf(
            IntStream.range(14, 19).mapToObj(Integer::toString).collect(Collectors.toList()));
    assertThat(intMapper.inputs).containsExactly(asList(14, 15, 16), asList(17, 18));
  }
}
