/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.events.quarkus.collector.catalog;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import java.security.Principal;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.projectnessie.catalog.model.ops.CatalogOperationResult;
import org.projectnessie.events.service.EventSubscribers;
import org.projectnessie.quarkus.providers.RepositoryId;

public class QuarkusCatalogOperationResultCollectorFactory {

  @Produces
  @RequestScoped
  public Consumer<CatalogOperationResult> newCatalogOperationResultCollector(
      @ConfigProperty(name = "nessie.version.store.events.enable", defaultValue = "true")
          boolean enabled,
      EventSubscribers subscribers,
      EventBus bus,
      DeliveryOptions options,
      @RepositoryId Instance<String> repositoryIds,
      @Any Instance<Supplier<Principal>> users,
      @Any Instance<Tracer> tracers,
      @Any Instance<MeterRegistry> registries) {
    if (!enabled) {
      return r -> {};
    }
    Principal principal = users.isResolvable() ? users.get().get() : null;
    String repositoryId = repositoryIds.isResolvable() ? repositoryIds.get() : "";
    Consumer<CatalogOperationResult> collector;
    if (registries.isResolvable()) {
      collector =
          new QuarkusMetricsCatalogOperationResultCollector(
              subscribers, repositoryId, principal, bus, options, registries.get());
    } else {
      collector =
          new QuarkusCatalogOperationResultCollector(
              subscribers, repositoryId, principal, bus, options);
    }
    if (tracers.isResolvable()) {
      String user = principal != null ? principal.getName() : null;
      collector = new QuarkusTracingCatalogOperationResultCollector(collector, user, tracers.get());
    }
    return collector;
  }
}
