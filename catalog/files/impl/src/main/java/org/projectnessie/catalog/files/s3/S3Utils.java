/*
 * Copyright (C) 2024 Dremio
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
package org.projectnessie.catalog.files.s3;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class S3Utils {

  private static final Pattern S3_HOST_PATTERN =
      Pattern.compile("^((.+)\\.)?s3[.-]([a-z0-9-]+)\\..*");
  private static final Pattern S3_PATH_PATTERN = Pattern.compile("^/([^/]+)/.*");

  private S3Utils() {}

  public static Optional<String> extractBucketName(URI uri) {
    String scheme = requireNonNull(uri).getScheme();
    switch (scheme) {
      case "s3":
      case "s3a":
      case "s3n":
        return extractBucketFromS3Uri(uri);
      case "http":
      case "https":
        return extractBucketFromHttpUri(uri);
      default:
        throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
    }
  }

  public static boolean isS3scheme(String scheme) {
    if (scheme == null) {
      return false;
    }
    switch (scheme) {
      case "s3":
      case "s3a":
      case "s3n":
        return true;
      default:
        return false;
    }
  }

  public static String normalizeS3Scheme(String uri) {
    if (uri.startsWith("s3a://")) {
      return uri.replaceFirst("s3a://", "s3://");
    }
    if (uri.startsWith("s3n://")) {
      return uri.replaceFirst("s3n://", "s3://");
    }
    return uri;
  }

  private static Optional<String> extractBucketFromHttpUri(URI uri) {
    String host = uri.getHost();
    checkArgument(host != null, "No host in non-s3 scheme URI: '%s'", uri);

    Matcher matcher = S3_HOST_PATTERN.matcher(host);
    if (matcher.matches()) {
      // A host with a URI using either "s3-region" or "s3.region" syntax
      String bucket = matcher.group(2);
      if (bucket != null) {
        return Optional.of(bucket);
      }
    }

    String path = uri.getPath();
    matcher = S3_PATH_PATTERN.matcher(path);
    return Optional.ofNullable(matcher.matches() ? matcher.group(1) : null);
  }

  private static Optional<String> extractBucketFromS3Uri(URI uri) {
    String auth = uri.getAuthority();
    return Optional.ofNullable(auth);
  }

  public static String asS3Location(String uri) {
    URI httpUri = URI.create(uri);
    checkArgument(httpUri.getScheme() != null, "No scheme in URI: '%s'", httpUri);
    checkArgument(httpUri.getHost() != null, "No host in URI: '%s'", httpUri);
    checkArgument(
        httpUri.getScheme().matches("http|https"),
        "Unsupported URI scheme: '%s'",
        httpUri.getScheme());
    String bucket;
    String key;
    Matcher matcher = S3_HOST_PATTERN.matcher(httpUri.getHost());
    if (matcher.matches() && matcher.group(2) != null) {
      // virtual-hosted style
      bucket = matcher.group(2);
      key = httpUri.getPath();
    } else {
      // path-style access style
      matcher = S3_PATH_PATTERN.matcher(httpUri.getPath());
      checkArgument(matcher.matches(), "Invalid S3 URI: '%s'", httpUri);
      bucket = matcher.group(1);
      key = httpUri.getPath().substring(bucket.length() + 1);
    }
    return "s3://" + bucket + key;
  }
}
