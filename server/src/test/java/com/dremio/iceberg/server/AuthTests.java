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
package com.dremio.iceberg.server;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.dremio.iceberg.client.AlleyClient;
import com.dremio.iceberg.model.Table;

public class AuthTests {

  private static TestAlleyServer server;

  private AlleyClient client;
  private org.apache.hadoop.conf.Configuration hadoopConfig;


  @BeforeClass
  public static void create() throws Exception {
    server = new TestAlleyServer();
    server.start(9993);
  }

  public void getCatalog(String username, String password) {
    hadoopConfig = new org.apache.hadoop.conf.Configuration();
    hadoopConfig.set("iceberg.alley.host", "localhost");
    hadoopConfig.set("iceberg.alley.port", "9993");
    hadoopConfig.set("iceberg.alley.ssl", "false");
    hadoopConfig.set("iceberg.alley.base", "api/v1");
    hadoopConfig.set("iceberg.alley.username", username);
    hadoopConfig.set("iceberg.alley.password", password);
    client = new AlleyClient(hadoopConfig);


  }

  @AfterClass
  public static void destroy() throws IOException {
    server = null;
  }

  public void tryEndpointPass(Runnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      Assert.fail();
    }
  }

  public void tryEndpointFail(Runnable runnable) {
    try {
      runnable.run();
      Assert.fail();
    } catch (ForbiddenException e) {
      return;
    }
    Assert.fail();
  }

  @Test
  public void testLogin() {
    try {
      getCatalog("x", "y");
      Assert.fail();
    } catch (NotAuthorizedException e) {

    } catch (Throwable t) {
      Assert.fail();
    }
  }

  @Test
  public void testAdmin() {
    getCatalog("admin_user", "test123");
    List<Table> tables = client.getTables();
    Assert.assertTrue(tables.isEmpty());
    tryEndpointPass(() -> client.createTable(new Table("x", "x")));
    tryEndpointPass(() -> client.updateTable(new Table("x", "x")));
    tryEndpointPass(() -> client.deleteTable("x", false));
    Table table = client.getTable("x");
    Assert.assertNull(table);
  }

  @Test
  public void testUser() {
    getCatalog("plain_user", "hello123");
    List<Table> tables = client.getTables();
    Assert.assertTrue(tables.isEmpty());
    tryEndpointFail(() -> client.createTable(new Table("x", "x")));
    tryEndpointFail(() -> client.deleteTable("x", false));
    tryEndpointFail(() -> client.updateTable(new Table("x", "x")));
    Table table = client.getTable("x");
    Assert.assertNull(table);
  }
}
