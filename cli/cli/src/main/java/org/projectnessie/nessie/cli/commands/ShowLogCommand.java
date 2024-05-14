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
package org.projectnessie.nessie.cli.commands;

import static org.jline.utils.AttributedStyle.YELLOW;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.projectnessie.client.api.GetCommitLogBuilder;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.FetchOption;
import org.projectnessie.model.LogResponse;
import org.projectnessie.nessie.cli.cli.BaseNessieCli;
import org.projectnessie.nessie.cli.cmdspec.ShowLogCommandSpec;
import org.projectnessie.nessie.cli.grammar.Node;
import org.projectnessie.nessie.cli.grammar.Token;

public class ShowLogCommand extends NessieListingCommand<ShowLogCommandSpec> {
  public ShowLogCommand() {}

  @Override
  protected Stream<String> executeListing(BaseNessieCli cli, ShowLogCommandSpec spec)
      throws Exception {

    @SuppressWarnings("resource")
    NessieApiV2 api = cli.mandatoryNessieApi();

    FetchOption fetchOption = FetchOption.MINIMAL;

    GetCommitLogBuilder commitLogBuilder =
        applyReference(cli, spec, api.getCommitLog()).fetch(fetchOption);

    Stream<LogResponse.LogEntry> logStream = commitLogBuilder.stream();

    if (spec.getLimit() != null) {
      logStream = logStream.limit(spec.getLimit());
    }

    AttributedStyle yellow = AttributedStyle.DEFAULT.foreground(YELLOW);
    AttributedStyle faint = AttributedStyle.DEFAULT.faint();

    DateTimeFormatter dateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.LONG)
            .withZone(ZoneId.systemDefault());

    return logStream.flatMap(
        e -> {
          CommitMeta meta = e.getCommitMeta();

          ZonedDateTime authorTimestamp = meta.getAuthorTime().atZone(ZoneId.of("Z"));
          ZonedDateTime commitTimestamp = meta.getCommitTime().atZone(ZoneId.of("Z"));

          Stream<String> header =
              Stream.of(
                  new AttributedString("commit " + meta.getHash(), yellow).toAnsi(cli.terminal()),
                  new AttributedStringBuilder()
                      .append("Author:  ", faint)
                      .append(
                          meta.getAuthor() == null || meta.getAuthor().isEmpty()
                              ? "<no author>"
                              : meta.getAuthor(),
                          meta.getAuthor() == null || meta.getAuthor().isEmpty()
                              ? faint
                              : AttributedStyle.DEFAULT)
                      .toAnsi(cli.terminal()),
                  new AttributedStringBuilder()
                      .append("Date:    ", faint)
                      .append(dateTimeFormatter.format(authorTimestamp))
                      .append(" (committed: ", faint)
                      .append(DateTimeFormatter.ISO_DATE_TIME.format(commitTimestamp), faint)
                      .append(")", faint)
                      .toAnsi(cli.terminal()),
                  new AttributedStringBuilder()
                      .append("Parents: ", faint)
                      .append(String.join(", ", meta.getParentCommitHashes()))
                      .toAnsi(cli.terminal()),
                  "");

          Stream<String> message =
              Arrays.stream(meta.getMessage().split("\n")).map(s -> "    " + s);

          return Stream.concat(header, Stream.concat(message, Stream.of("")));
        });
  }

  public String name() {
    return Token.TokenType.SHOW + " " + Token.TokenType.LOG;
  }

  public String description() {
    return "List commits.";
  }

  @Override
  public List<List<Node.NodeType>> matchesNodeTypes() {
    return List.of(
        List.of(Token.TokenType.SHOW), List.of(Token.TokenType.SHOW, Token.TokenType.LOG));
  }
}
