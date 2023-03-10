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
package org.projectnessie.tools.contentgenerator.cli;

import java.util.stream.Stream;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Reference;
import picocli.CommandLine.Command;

/** Implementation to read all references. */
@Command(name = "refs", mixinStandardHelpOptions = true, description = "Read references")
public class ReadReferences extends AbstractCommand {

  @Override
  public void execute() throws NessieNotFoundException {
    try (NessieApiV2 api = createNessieApiInstance()) {
      spec.commandLine().getOut().printf("Reading all references\n\n");
      Stream<Reference> references = api.getAllReferences().stream();
      references.forEach(reference -> spec.commandLine().getOut().println(reference));
      spec.commandLine().getOut().printf("\nDone reading all references\n\n");
    }
  }
}
