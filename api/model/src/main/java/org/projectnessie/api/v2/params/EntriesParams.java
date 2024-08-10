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
package org.projectnessie.api.v2.params;

import static org.projectnessie.api.v2.doc.ApiDoc.REQUESTED_KEY_PARAMETER_DESCRIPTION;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.immutables.builder.Builder.Constructor;
import org.projectnessie.model.ContentKey;

/**
 * The purpose of this class is to include optional parameters that can be passed to {@code
 * HttpTreeApi#getEntries(String, EntriesParams)}.
 *
 * <p>For easier usage of this class, there is {@link EntriesParams#builder()}, which allows
 * configuring/setting the different parameters.
 */
public class EntriesParams extends KeyRangeParams<EntriesParams> {

  @Parameter(description = REQUESTED_KEY_PARAMETER_DESCRIPTION)
  @QueryParam("key")
  private List<ContentKey> requestedKeys;

  @Nullable
  @Parameter(
      description =
          "A Common Expression Language (CEL) expression. An intro to CEL can be found at https://github.com/google/cel-spec/blob/master/doc/intro.md.\n"
              + "Usable variables within the expression are 'entry.namespace' (string) & 'entry.contentType' (string)",
      examples = {
        @ExampleObject(ref = "expr_by_namespace"),
        @ExampleObject(ref = "expr_by_contentType"),
        @ExampleObject(ref = "expr_by_namespace_and_contentType")
      })
  @QueryParam("filter")
  private String filter;

  @Nullable
  @Parameter(description = "Optionally request to return 'Content' objects for the returned keys.")
  @QueryParam("content")
  private Boolean withContent;

  public EntriesParams() {}

  @Constructor
  EntriesParams(
      @Nullable Integer maxRecords,
      @Nullable String pageToken,
      @Nullable ContentKey minKey,
      @Nullable ContentKey maxKey,
      @Nullable ContentKey prefixKey,
      @Nullable List<ContentKey> requestedKeys,
      @Nullable String filter,
      @Nullable Boolean withContent) {
    super(maxRecords, pageToken, minKey, maxKey, prefixKey);
    this.filter = filter;
    this.withContent = withContent;
    this.requestedKeys = requestedKeys;
  }

  public static EntriesParamsBuilder builder() {
    return new EntriesParamsBuilder();
  }

  public static EntriesParams empty() {
    return builder().build();
  }

  public List<ContentKey> getRequestedKeys() {
    return requestedKeys;
  }

  @Nullable
  public String filter() {
    return filter;
  }

  public boolean withContent() {
    return withContent != null && withContent;
  }

  @Override
  public EntriesParams forNextPage(String pageToken) {
    return new EntriesParams(
        maxRecords(),
        pageToken,
        minKey(),
        maxKey(),
        prefixKey(),
        requestedKeys,
        filter,
        withContent);
  }
}
