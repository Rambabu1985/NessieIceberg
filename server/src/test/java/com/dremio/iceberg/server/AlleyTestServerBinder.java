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

import com.dremio.iceberg.auth.User;
import com.dremio.iceberg.auth.UserService;
import com.dremio.iceberg.backend.Backend;
import com.dremio.iceberg.backend.dynamodb.DynamoDbBackend;
import com.dremio.iceberg.backend.simple.InMemory;
import com.dremio.iceberg.jwt.KeyGenerator;
import com.dremio.iceberg.model.ImmutableUser;
import com.dremio.iceberg.server.ServerConfigurationImpl.ServerDatabaseConfigurationImpl;
import com.dremio.iceberg.server.auth.AlleySecurityContext;
import com.dremio.iceberg.server.auth.PrivateKeyGenerator;
import com.dremio.iceberg.server.jgit.JGitContainer;
import com.dremio.iceberg.server.jgit.JGitContainerImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.SecurityContext;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public class AlleyTestServerBinder extends AbstractBinder {

  public static final TestServerConfigurationImpl settings = new TestServerConfigurationImpl();

  private final String database;

  public AlleyTestServerBinder() {
    database = "in-memory";
  }

  public AlleyTestServerBinder(String database) {
    this.database = database;
  }

  @Override
  protected void configure() {
    bindFactory(TestServerConfigurationFactory.class).to(ServerConfiguration.class);
    if (database.equals("dynamo")) {
      bindFactory(DynamoDbBackendFactory.class).to(Backend.class);
    } else {
      bindFactory(BackendFactory.class).to(Backend.class);
    }
    bind(AlleySecurityContext.class).to(SecurityContext.class);
    bind(TestUserService.class).to(UserService.class);
    bind(PrivateKeyGenerator.class).to(KeyGenerator.class);
    bindFactory(JGitContainerFactory.class).to(JGitContainer.class);
  }

  public static class JGitContainerFactory implements Factory<JGitContainer> {
    private static boolean enabled = false;

    private final Backend backend;

    @Inject
    private JGitContainerFactory(Backend backend) {
      this.backend = backend;
    }

    @Override
    public JGitContainer provide() {
      JGitContainerImpl jgc = new JGitContainerImpl(backend);
      if (enabled) {
        return jgc;
      }
      try {
        jgc.create("master", null, null);
        enabled = true;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return jgc;
    }

    @Override
    public void dispose(JGitContainer instance) {

    }
  }
  public static final class DynamoDbBackendFactory implements Factory<Backend> {

    @Override
    public Backend provide() {
      Map<String, String> dbProps = ImmutableMap.of("region", "us-west-2",
                                                             "endpoint", "http://localhost:8000");
      Backend backend = new DynamoDbBackend.BackendFactory().create(new ServerConfigurationImpl(
        new ServerDatabaseConfigurationImpl(null, dbProps),
        null,
        null,
        "master"
      ));
      return backend;
    }

    @Override
    public void dispose(Backend instance) {
      try {
        instance.close();
      } catch (Exception e) {
        //pass
      }
    }
  }

  public static final class BackendFactory implements Factory<Backend> {
    private static final Backend backend = new InMemory();

    @Override
    public Backend provide() {
      return backend;
    }

    @Override
    public void dispose(Backend instance) {
      try {
        backend.close();
      } catch (Exception e) {
        //pass
      }
    }
  }

  public static class TestUserService implements UserService {

    private static final Map<String, User> userMap = new HashMap<>();

    static {
      userMap.put("normal", new User(getUser(false)));
      userMap.put("test", new User(getUser(true)));
    }

    @Override
    public String authorize(String login, String password) {
      if (userMap.containsKey(login)) {
        return (login.equals("normal")) ? "normal" : "ok";
      }
      throw new NotAuthorizedException("wrong username/password");
    }

    @Override
    public User validate(String token) {
      if (token.equals("normal")) {
        return userMap.get("normal");
      }
      return userMap.get("test");
    }

    @Override
    public Optional<User> fetch(String username) {
      if (userMap.containsKey(username)) {
        return Optional.of(userMap.get(username));
      }
      return Optional.empty();
    }

    @Override
    public List<User> fetchAll() {
      return ImmutableList.copyOf(userMap.values());
    }

    @Override
    public void create(User user) {
      userMap.put(user.getName(), user);
    }

    @Override
    public void update(User user) {
      com.dremio.iceberg.model.User modelUser = AlleyTestServerBinder.unwrap(user);
      userMap.put(user.getName(), new User(modelUser));
    }

    @Override
    public void delete(String user) {
      userMap.remove(user);
    }
  }

  private static class TestServerConfigurationFactory implements Factory<ServerConfiguration> {

    @Override
    public ServerConfiguration provide() {
      return settings;
    }

    @Override
    public void dispose(ServerConfiguration serverConfiguration) {

    }
  }

  private static com.dremio.iceberg.model.User getUser(boolean admin) {
    return ImmutableUser.builder()
                        .id(admin ? "test" : "normal")
                        .password("")
                        .addAllRoles(
                          admin ? ImmutableSet.of("admin", "user") : ImmutableSet.of("user"))
                        .build();
  }

  static com.dremio.iceberg.model.User unwrap(User user) {
    try {
      Method method = User.class.getDeclaredMethod("unwrap");
      method.setAccessible(true);
      return (com.dremio.iceberg.model.User) method.invoke(user);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
