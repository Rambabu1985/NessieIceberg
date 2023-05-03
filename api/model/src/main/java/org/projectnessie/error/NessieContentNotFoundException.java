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

import static org.projectnessie.error.ContentKeyErrorDetails.contentKeyErrorDetails;

import org.projectnessie.model.ContentKey;

/** This exception is thrown when the requested content object is not present in the store. */
public class NessieContentNotFoundException extends NessieNotFoundException {
  private final ContentKeyErrorDetails contentKeyErrorDetails;

  public NessieContentNotFoundException(ContentKey key, String ref) {
    super(String.format("Could not find content for key '%s' in reference '%s'.", key, ref));
    this.contentKeyErrorDetails = contentKeyErrorDetails(key);
  }

  public NessieContentNotFoundException(NessieError error) {
    super(error);
    this.contentKeyErrorDetails = error.getErrorDetailsAsOrNull(ContentKeyErrorDetails.class);
  }

  @Override
  public ErrorCode getErrorCode() {
    return ErrorCode.CONTENT_NOT_FOUND;
  }

  @Override
  public ContentKeyErrorDetails getErrorDetails() {
    return contentKeyErrorDetails;
  }
}
