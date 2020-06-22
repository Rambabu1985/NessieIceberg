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

package com.dremio.nessie.auth;

import java.util.List;
import java.util.Optional;

/**
 * interface to define what a backend auth can/should be able to do.
 */
public interface UserService {

  String authorize(String login, String password);

  User validate(String token);

  Optional<User> fetch(String username);

  List<User> fetchAll();

  void create(User user);

  void update(User user);

  void delete(String user);

}
