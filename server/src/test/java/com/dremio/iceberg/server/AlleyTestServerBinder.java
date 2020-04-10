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

import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.SecurityContext;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import com.dremio.iceberg.backend.simple.InMemory;
import com.dremio.iceberg.server.auth.AlleySecurityContext;
import com.dremio.iceberg.server.auth.BasicKeyGenerator;
import com.dremio.iceberg.server.auth.KeyGenerator;
import com.dremio.iceberg.auth.User;
import com.dremio.iceberg.auth.UserService;
import com.dremio.iceberg.backend.Backend;
import com.google.common.collect.ImmutableList;

public class AlleyTestServerBinder extends AbstractBinder {
  @Override
  protected void configure() {
    bind(ServerConfigurationImpl.class).to(ServerConfiguration.class);
    bind(InMemory.class).to(Backend.class);
    bind(AlleySecurityContext.class).to(SecurityContext.class);
    bind(TestUserService.class).to(UserService.class);
    bind(BasicKeyGenerator.class).to(KeyGenerator.class);
  }

  public static class TestUserService implements UserService {

    @Override
    public String authorize(String login, String password) {
      return "ok";
    }

    @Override
    public User validate(String token) {
      return new User("test", "admin,user");
    }

    @Override
    public Optional<User> fetch(String username) {
      return Optional.of(new User("test", "admin,user"));
    }

    @Override
    public List<User> fetchAll() {
      return ImmutableList.of(new User("test", "admin.user"));
    }

    @Override
    public void create(User user) {

    }
  }
}
