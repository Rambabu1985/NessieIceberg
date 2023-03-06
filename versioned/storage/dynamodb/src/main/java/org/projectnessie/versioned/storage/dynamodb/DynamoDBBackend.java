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
package org.projectnessie.versioned.storage.dynamodb;

import static org.projectnessie.versioned.storage.dynamodb.DynamoDBConstants.KEY_NAME;
import static org.projectnessie.versioned.storage.dynamodb.DynamoDBConstants.TABLE_OBJS;
import static org.projectnessie.versioned.storage.dynamodb.DynamoDBConstants.TABLE_REFS;

import java.util.List;
import javax.annotation.Nonnull;
import org.projectnessie.versioned.storage.common.persist.Backend;
import org.projectnessie.versioned.storage.common.persist.PersistFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

final class DynamoDBBackend implements Backend {
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBBackend.class);

  private final DynamoDbClient client;
  private final boolean closeClient;

  DynamoDBBackend(@Nonnull @jakarta.annotation.Nonnull DynamoDbClient client, boolean closeClient) {
    this.client = client;
    this.closeClient = closeClient;
  }

  @Nonnull
  @jakarta.annotation.Nonnull
  DynamoDbClient client() {
    return client;
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public PersistFactory createFactory() {
    return new DynamoDBPersistFactory(this);
  }

  @Override
  public void close() {
    if (closeClient) {
      client.close();
    }
  }

  @Override
  public void setupSchema() {
    createIfMissing(TABLE_REFS);
    createIfMissing(TABLE_OBJS);
  }

  private void createIfMissing(String name) {
    if (!tableExists(name)) {
      createTable(name);
    }
  }

  private boolean tableExists(String name) {
    try {
      DescribeTableResponse table =
          client.describeTable(DescribeTableRequest.builder().tableName(name).build());
      verifyKeySchema(table.table());
      return true;
    } catch (ResourceNotFoundException e) {
      LOGGER.debug("Didn't find table '{}', going to create one.", name, e);
      return false;
    }
  }

  private void createTable(String name) {
    client.createTable(
        b ->
            b.tableName(name)
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName(KEY_NAME)
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName(KEY_NAME)
                        .keyType(KeyType.HASH)
                        .build()));
  }

  private static void verifyKeySchema(TableDescription description) {
    List<KeySchemaElement> elements = description.keySchema();

    if (elements.size() == 1) {
      KeySchemaElement key = elements.get(0);
      if (key.attributeName().equals(KEY_NAME)) {
        if (key.keyType() == KeyType.HASH) {
          return;
        }
      }
    }
    throw new IllegalStateException(
        String.format(
            "Invalid key schema for table: %s. Key schema should be a hash partitioned "
                + "attribute with the name 'id'.",
            description.tableName()));
  }

  @Override
  public String configInfo() {
    return "";
  }
}
