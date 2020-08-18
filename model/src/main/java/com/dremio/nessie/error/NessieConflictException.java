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

package com.dremio.nessie.error;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("MissingJavadocMethod")
public class NessieConflictException extends RuntimeException {

  private final List<String> conflictTables;

  public NessieConflictException() {
    this(Collections.emptyList());
  }

  public NessieConflictException(List<String> conflictTables) {
    this.conflictTables = tables(conflictTables);
  }

  public NessieConflictException(List<String> conflictTables, String message) {
    super(message);
    this.conflictTables = tables(conflictTables);
  }

  public NessieConflictException(List<String> conflictTables, String message, Throwable cause) {
    super(message, cause);
    this.conflictTables = tables(conflictTables);
  }

  public NessieConflictException(List<String> conflictTables, Throwable cause) {
    super(cause);
    this.conflictTables = tables(conflictTables);
  }

  public NessieConflictException(List<String> conflictTables,
                                 String message,
                                 Throwable cause,
                                 boolean enableSuppression,
                                 boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.conflictTables = tables(conflictTables);
  }

  public List<String> getConflictTables() {
    return conflictTables;
  }

  private static List<String> tables(List<String> conflictTables) {
    return conflictTables == null ? Collections.emptyList() : Collections.unmodifiableList(conflictTables);
  }
}
