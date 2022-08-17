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
package org.projectnessie.tools.contentgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.model.Content;
import org.projectnessie.model.types.ContentTypes;

class ITGenerateContent extends AbstractContentGeneratorTest {

  static List<Content.Type> basicGenerateContentTest() {
    return Arrays.asList(ContentTypes.all());
  }

  @ParameterizedTest
  @MethodSource("basicGenerateContentTest")
  void basicGenerateContentTest(Content.Type contentType) throws Exception {
    Assumptions.assumeTrue(
        !contentType.equals(Content.Type.UNKNOWN) && !contentType.equals(Content.Type.NAMESPACE));

    int numCommits = 20;

    try (NessieApiV1 api = buildNessieApi()) {

      String testCaseBranch = "type_" + contentType.name();

      ProcessResult proc =
          runGeneratorCmd(
              "generate",
              "-n",
              Integer.toString(numCommits),
              "-u",
              NESSIE_API_URI,
              "-D",
              testCaseBranch,
              "--type=" + contentType.name());

      assertThat(proc.getExitCode()).isEqualTo(0);
      assertThat(api.getCommitLog().refName(testCaseBranch).stream()).hasSize(numCommits);
    }
  }
}
