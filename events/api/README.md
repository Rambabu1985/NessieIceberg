# Nessie Events API

This module contains the API for the Nessie events notification system.

## Overview

The `org.projectnessie.events.api` package contains the API classes for the Nessie events
notification system.

The main entry point of the API is the `Event` interface. Instances of `Event` are created and
published by the Nessie server and consumed by registered event subscribers.

The `Event` interface has a `getType()` method that returns the type of the event. The type is an
enum that uniquely identifies the event type. This enum serves as a discriminator for the
event type and allows subscribers to filter events by type. It also allows subscribers to
easily serialize and deserialize events to and from JSON.

The following concrete event implementations are defined:

* `CommitEvent`: This event is published when a commit is performed.
* `MergeEvent`: This event is published when a merge is performed.
* `TransplantEvent`: This event is published when a transplant is performed.
* `ReferenceCreatedEvent`: This event is published when a reference is created.
* `ReferenceUpdatedEvent`: This event is published when a reference is updated.
* `ReferenceDeletedEvent`: This event is published when a reference is deleted.
* `ContentStoredEvent`: This event is published when a content is stored (PUT).
* `ContentRemovedEvent`: This event is published when a content is removed (DELETE).
* `GenericEvent`: a catch-all event type that will be used to serialize and deserialize any event 
  type that is not supported by the other event types above.

## Contents

Contents in Nessie form a pluggable system. The `org.projectnessie.model.Content` class is the base
class for all content types, but serialization and deserialization of content is done in a complex
way. The `Content` interface in this package, on the other hand, is a simple interface that allows
for easy serialization and deserialization of content.

It can handle the following content types:

* `IcebergTable`: Iceberg table content.
* `IcebergView`: Iceberg view content.
* `DeltaLakeTable`: Delta Lake table content.
* `Namespace`: Namespace content.
* `GenericContent`: a catch-all content type that will be used to serialize and deserialize any 
  content type that is not supported by the other content types above.

## JSON serialization & deserialization

The API exposes `Instant` and `Optional` types. Proper JSON serialization of the event types 
requires the addition of 2 Jackson modules:

* `com.fasterxml.jackson.datatype:jackson-datatype-jdk8`
* `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`

The following code snippet shows how to register the modules with an `ObjectMapper`:

```
ObjectMapper mapper = new ObjectMapper()
  .registerModule(new JavaTimeModule())
  .registerModule(new Jdk8Module());
```

Serializing and deserializing unknown event and content types is done using the `GenericEvent`
and `GenericContent` types. 

For example, the following JSON payload:

```json
{
  "type": "UNKNOWN_EVENT",
  "id": "e1b0a7a8-4f8e-4f1e-8a2e-2d9b8d9b9e5e",
  "repositoryId": "repo1",
  "eventCreationTimestamp": "2021-03-01T14:00:00Z",
  "eventInitiator": "user1",
  "custom-payload": {
    "foo": "bar"
  }
}
```

... would be deserialized into a `GenericEvent` instance:

```java
Event event = mapper.readValue(json, Event.class);
assert event instanceof GenericEvent;
GenericEvent genericEvent = (GenericEvent) event;
assert genericEvent.getType() == EventType.GENERIC;
assert genericEvent.getGenericType().equals("UNKNOWN_EVENT");
assert genericEvent.getRepositoryId().equals("repo1");
assert genericEvent.getEventCreationTimestamp().equals(Instant.parse("2021-03-01T14:00:00Z"));
assert genericEvent.getEventInitiator().get().equals("user1");
assert genericEvent.getProperties().containsKey("custom-payload");
```

Any custom properties that are not part of the `Event` interface will be stored in the event's 
`properties` map, like the `custom-payload` property in the example above.
