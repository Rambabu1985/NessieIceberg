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
package org.projectnessie.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Execution(ExecutionMode.CONCURRENT)
class TestErrorCode {

  private Optional<Exception> ex(ErrorCode errorCode) {
    NessieError error =
        ImmutableNessieError.builder().reason("test").status(1).errorCode(errorCode).build();
    return ErrorCode.asException(error);
  }

  @Test
  void testUnknown() {
    assertThat(ex(ErrorCode.UNKNOWN)).isNotPresent();
  }

  @ParameterizedTest
  @EnumSource(value = ErrorCode.class, mode = EnumSource.Mode.EXCLUDE, names = "UNKNOWN")
  public void testConversion(ErrorCode errorCode) {
    Optional<Exception> ex = ex(errorCode);
    assertThat(ex).isPresent();
    assertThat(ex.get())
        .isInstanceOf(ErrorCodeAware.class)
        .extracting(e -> ((ErrorCodeAware) e).getErrorCode())
        .isEqualTo(errorCode);
  }
}
