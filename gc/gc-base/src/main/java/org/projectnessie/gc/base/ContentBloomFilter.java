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
package org.projectnessie.gc.base;

import static org.projectnessie.model.Content.Type.ICEBERG_TABLE;
import static org.projectnessie.model.Content.Type.ICEBERG_VIEW;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.io.Serializable;
import java.nio.charset.Charset;
import org.projectnessie.model.Content;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.IcebergView;

/** A utility class wrapping bloom filter functionality. */
public class ContentBloomFilter implements Serializable {

  private static final long serialVersionUID = -693336833916979221L;

  // track iceberg table/view contents only using the snapshot/version id
  // as the consumer of GC results needs only this info for clean up.
  // String bloom filter with content type prefix + snapshot/version id.
  private final BloomFilter<String> icebergContentBloomFilter;

  public ContentBloomFilter(GCParams gcParams, long totalCommitsInDefaultReference) {
    long expectedEntries =
        gcParams.getBloomFilterExpectedEntries() == null
            ? totalCommitsInDefaultReference
            : gcParams.getBloomFilterExpectedEntries();
    double fpp = gcParams.getBloomFilterFpp() == null ? 0.03D : gcParams.getBloomFilterFpp();
    this.icebergContentBloomFilter =
        BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedEntries, fpp);
  }

  public void put(Content content) {
    switch (content.getType()) {
      case ICEBERG_TABLE:
        icebergContentBloomFilter.put(
            ICEBERG_TABLE.name() + ((IcebergTable) content).getSnapshotId());
        break;
      case ICEBERG_VIEW:
        icebergContentBloomFilter.put(ICEBERG_VIEW.name() + ((IcebergView) content).getVersionId());
        break;
      default:
        throw new RuntimeException("Unsupported type " + content.getType());
    }
  }

  public boolean mightContain(Content content) {
    switch (content.getType()) {
      case ICEBERG_TABLE:
        return icebergContentBloomFilter.mightContain(
            ICEBERG_TABLE.name() + ((IcebergTable) content).getSnapshotId());
      case ICEBERG_VIEW:
        return icebergContentBloomFilter.mightContain(
            ICEBERG_VIEW.name() + ((IcebergView) content).getVersionId());
      default:
        throw new RuntimeException("Unsupported type " + content.getType());
    }
  }

  public ContentBloomFilter merge(ContentBloomFilter filter) {
    if (filter.icebergContentBloomFilter != null) {
      icebergContentBloomFilter.putAll(filter.icebergContentBloomFilter);
    }
    return this;
  }
}
