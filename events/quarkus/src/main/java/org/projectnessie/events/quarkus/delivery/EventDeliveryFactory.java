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
package org.projectnessie.events.quarkus.delivery;

import static org.projectnessie.events.quarkus.config.VersionStoreConfigConstants.NESSIE_VERSION_STORE_METRICS_ENABLE;
import static org.projectnessie.events.quarkus.config.VersionStoreConfigConstants.NESSIE_VERSION_STORE_TRACE_ENABLE;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.Vertx;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.projectnessie.events.api.Event;
import org.projectnessie.events.quarkus.config.QuarkusEventConfig;
import org.projectnessie.events.spi.EventSubscriber;
import org.projectnessie.events.spi.EventSubscription;

@Dependent
public class EventDeliveryFactory {

  private final QuarkusEventConfig config;
  private final Vertx vertx;
  private final Tracer tracer;
  private final MeterRegistry registry;
  private final io.micrometer.core.instrument.Clock clock;

  @Inject
  public EventDeliveryFactory(
      QuarkusEventConfig config,
      @SuppressWarnings("CdiInjectionPointsInspection") Vertx vertx,
      @ConfigProperty(name = NESSIE_VERSION_STORE_TRACE_ENABLE, defaultValue = "false")
          boolean tracingEnabled,
      @ConfigProperty(name = NESSIE_VERSION_STORE_METRICS_ENABLE, defaultValue = "false")
          boolean metricsEnabled,
      @Any Instance<Tracer> tracers,
      @Any Instance<MeterRegistry> registries) {
    this.config = config;
    this.vertx = vertx;
    this.tracer = extractInstance(tracingEnabled, tracers);
    this.registry = extractInstance(metricsEnabled, registries);
    this.clock = registry == null ? null : new MicrometerClockAdapter(config.getClock());
  }

  private static <T> T extractInstance(boolean enabled, Instance<T> instances) {
    return enabled && instances != null && instances.isResolvable() ? instances.get() : null;
  }

  public EventDelivery create(
      Event event, EventSubscriber subscriber, EventSubscription subscription) {
    RetriableEventDelivery delivery =
        subscriber.isBlocking()
            ? new BlockingEventDelivery(event, subscriber, config.getRetryConfig(), vertx)
            : new StandardEventDelivery(event, subscriber, config.getRetryConfig(), vertx);
    if (LoggingEventDelivery.isLoggingEnabled()) {
      delivery = new LoggingEventDelivery(delivery, event, subscription);
    }
    if (registry != null) {
      delivery = new MetricsEventDelivery(delivery, event, registry, clock);
    }
    if (tracer != null) {
      delivery = new TracingEventDelivery(delivery, event, subscription, config, tracer);
    }
    return delivery;
  }

  private static class MicrometerClockAdapter implements io.micrometer.core.instrument.Clock {

    private final java.time.Clock clock;

    MicrometerClockAdapter(java.time.Clock clock) {
      this.clock = clock;
    }

    @Override
    public long wallTime() {
      return clock.millis();
    }

    @Override
    public long monotonicTime() {
      return System.nanoTime();
    }
  }
}
