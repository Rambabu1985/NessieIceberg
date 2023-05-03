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

/** This exception is thrown when the namespace is not empty. */
public class NessieNamespaceNotEmptyException extends NessieConflictException {
  private final ContentKeyErrorDetails contentKeyErrorDetails;

  public NessieNamespaceNotEmptyException(
      ContentKeyErrorDetails contentKeyErrorDetails, String message, Throwable cause) {
    super(message, cause);
    this.contentKeyErrorDetails = contentKeyErrorDetails;
  }

  public NessieNamespaceNotEmptyException(
      ContentKeyErrorDetails contentKeyErrorDetails, String message) {
    super(message);
    this.contentKeyErrorDetails = contentKeyErrorDetails;
  }

  public NessieNamespaceNotEmptyException(NessieError error) {
    super(error);
    this.contentKeyErrorDetails = error.getErrorDetailsAsOrNull(ContentKeyErrorDetails.class);
  }

  @Override
  public ErrorCode getErrorCode() {
    return ErrorCode.NAMESPACE_NOT_EMPTY;
  }

  @Override
  public ContentKeyErrorDetails getErrorDetails() {
    return contentKeyErrorDetails;
  }
}
