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

package com.dremio.nessie.server;

import com.dremio.nessie.json.ObjectMapperContextResolver;
import com.dremio.nessie.model.Branch;
import com.dremio.nessie.model.ImmutableBranch;
import com.dremio.nessie.model.ImmutableTable;
import com.dremio.nessie.model.Table;
import com.dremio.nessie.server.rest.TableBranchOperations;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.jgit.lib.Constants;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class RestGitTest extends JerseyTest {

  private String authHeader = "";

  @Override
  protected Application configure() {
    ResourceConfig rc = new ResourceConfig(TableBranchOperations.class);
    rc.register(new NessieTestServerBinder());
    rc.register(ObjectMapperContextResolver.class);
    return rc;
  }

  @Override
  protected void configureClient(ClientConfig config) {
    super.configureClient(config);
    config.register(ObjectMapperContextResolver.class);
  }

  @Test
  public void testBasic() {
    Branch[] branches = get().get(Branch[].class);
    Assertions.assertEquals(1, branches.length);
    Assertions.assertEquals(Constants.MASTER, branches[0].getName());

    Branch master = get("objects/master").get(Branch.class);
    Assertions.assertEquals(branches[0], master);

    Branch test = ImmutableBranch.builder()
                                 .id("master")
                                 .name("test")
                                 .build();
    Response res = get("objects/test").post(Entity.entity(test, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(201, res.getStatus());
    Branch testReturn = get("objects/test").get(Branch.class);
    Assertions.assertEquals("test", testReturn.getName());

    Table table = ImmutableTable.builder()
                                .id("xxx.test")
                                .name("test")
                                .namespace("xxx")
                                .metadataLocation("/the/directory/over/there")
                                .build();
    res = get("objects/test").get();
    res = get("objects/test/xxx.test").header("If-Match", res.getHeaders().getFirst("ETag"))
                                      .post(Entity.entity(table, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(201, res.getStatus());

    Table[] updates = new Table[11];
    for (int i = 0; i < 10; i++) {
      updates[i] = ImmutableTable.builder()
                                 .id("xxx.test" + i)
                                 .name("test" + i)
                                 .namespace("xxx")
                                 .metadataLocation("/the/directory/over/there/" + i)
                                 .build();
    }
    updates[10] = ImmutableTable.builder()
                                .id("xxx.test")
                                .name("test")
                                .namespace("xxx")
                                .metadataLocation(
                                        "/the/directory/over/there/has/been/moved")
                                .build();
    res = get("objects/test").get();
    res = get("objects/test").header("If-Match", res.getHeaders().getFirst("ETag"))
                             .put(Entity.entity(updates, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(200, res.getStatus());
    table = ImmutableTable.builder()
                          .id("xxx.test")
                          .name("test")
                          .namespace("xxx")
                          .metadataLocation(
                                  "/the/directory/over/there/has/been/moved/again")
                          .build();
    res = get("objects/test/xxx.test").get();
    Assertions.assertEquals(updates[10], res.readEntity(Table.class));
    res = get("objects/test").get();
    res = get("objects/test/xxx.test").header("If-Match", res.getHeaders().getFirst("ETag"))
                                      .put(Entity.entity(table, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(200, res.getStatus());
    res = get("objects/test/xxx.test").get();
    Assertions.assertEquals(table, res.readEntity(Table.class));
  }

  @Test
  public void testNewBranch() {
    Table[] updates = new Table[10];
    for (int i = 0; i < 10; i++) {
      updates[i] = ImmutableTable.builder()
                                 .id("xxx.test" + i)
                                 .name("test" + i)
                                 .namespace("xxx")
                                 .metadataLocation("/the/directory/over/there/" + i)
                                 .build();
    }
    Response res = get("objects/master").get();
    res = get("objects/master").header("If-Match", res.getHeaders().getFirst("ETag"))
                               .put(Entity.entity(updates, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(200, res.getStatus());
    Branch test = ImmutableBranch.builder()
                                 .id("master")
                                 .name("test2")
                                 .build();
    res = get("objects/test2").post(Entity.entity(test, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(201, res.getStatus());

    for (int i = 0; i < 10; i++) {
      Table bt = updates[i];
      res = get("objects/test2/" + bt.getId()).get();
      Assertions.assertEquals(200, res.getStatus());
      Assertions.assertEquals(bt, res.readEntity(Table.class));
    }

    res = get("objects/test2").get();
    res = get("objects/test2/" + updates[0].getId()).header("If-Match",
                                                            res.getHeaders().getFirst("ETag"))
                                                    .delete();
    Assertions.assertEquals(200, res.getStatus());
    res = get("objects/test2/" + updates[0].getId()).get();
    Assertions.assertEquals(404, res.getStatus());
    res = get("objects/test2").get();
    res = get("objects/test2").header("If-Match", res.getHeaders().getFirst("ETag"))
                              .delete();
    Assertions.assertEquals(200, res.getStatus());
    res = get("objects/test2").get();
    Assertions.assertEquals(404, res.getStatus());

  }

  @SuppressWarnings("LocalVariableName")
  @Test
  public void testOptimisticLocking() {
    Branch test = ImmutableBranch.builder()
                                 .id("master")
                                 .name("test3")
                                 .build();
    Response res = get("objects/test3").post(Entity.entity(test, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(201, res.getStatus());
    Table table = ImmutableTable.builder()
                                .id("xxx.test")
                                .name("test")
                                .namespace("xxx")
                                .metadataLocation("/the/directory/over/there")
                                .build();
    res = get("objects/test3").get();
    res = get("objects/test3/xxx.test").header("If-Match", res.getHeaders().getFirst("ETag"))
                                       .post(Entity.entity(table, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(201, res.getStatus());

    Object eTagStart = get("objects/test3").get().getHeaders().getFirst("ETag");
    table = ImmutableTable.builder()
                          .id("xxx.test")
                          .name("test")
                          .namespace("xxx")
                          .metadataLocation("/the/directory/over/there/has/been/moved")
                          .build();
    res = get("objects/test3/xxx.test").header("If-Match", eTagStart)
                                       .put(Entity.entity(table, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(200, res.getStatus());
    table = ImmutableTable.builder()
                          .id("xxx.test")
                          .name("test")
                          .namespace("xxx")
                          .metadataLocation("/the/directory/over/there/has/been/moved/again")
                          .build();
    res = get("objects/test3/xxx.test").header("If-Match", eTagStart)
                                       .put(Entity.entity(table, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(412, res.getStatus());
    Object eTagNew = get("objects/test3").get().getHeaders().getFirst("ETag");
    res = get("objects/test3/xxx.test").header("If-Match", eTagNew)
                                       .put(Entity.entity(table, MediaType.APPLICATION_JSON_TYPE));
    Assertions.assertEquals(200, res.getStatus());
  }

  private Builder get() {
    return get("objects");
  }

  private Builder get(String endpoint) {
    return target(endpoint).request(MediaType.APPLICATION_JSON_TYPE)
                           .header(HttpHeaders.AUTHORIZATION, authHeader);
  }
}
