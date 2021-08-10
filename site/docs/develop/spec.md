# Nessie Specification

This page documents the complete nessie specification. This includes:

* API and its constraints
* Contract for value objects
* requirements for backend storage implementations

## API contract

!!! warning
    todo

## Contract for Value Objects

### General Contract

#### Content Id

All contents object must have an `id` field. This field is unique to the object and immutable once created. By convention,
it is a [UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier) though this is not enforced by the Specification.
There are several expectations on this field:

1. They are immutable. Once created the object will keep the same `id` for its entire lifetime
1. If the object is moved (eg stored under a different `Key`) it will keep the id
1. Two objects with the same `key` (eg on different branches) will only have the same `id` if the object is the same (eg the same iceberg table or view)

There is no API to look up an object by `id` and the intention of an `id` is not to serve in that capacity. An example usage
of the `id` field might be storing auxiliary data on an object in a local cache and using `id` to look up that auxiliary data.

!!! note
    A note about caching. The `Contents` objects are likely to be cached locally by services using Nessie. There is also likely
    to be auxiliary data (eg schema, partitions etc) stored by services which refer to a specific `Contents` at a specific
    commit or time. The hash of the contents (eg `sha1sum contents` ) uniquely reference an
    object in the Nessie history and is a suitable key to identify an object at a particular point in its history. Note that
    we are not speaking of the java `hashCode` nor any of the hash terminology used in Nessie.  Since the Contents object is
    immutable the hash is stable and since it is disconnected from Nessies version store properties it exists across commits/branches
    and survives GC and other table maintenance operations.

    The commit hash on the other hand makes a poor cache key as the commit hash could be garbage collected or the object could be
    merged/transplanted to a different branch resulting in a different commit hash. **Do not** use the commit hash for any other
    purpose than as a consistency check in the API.

    The object `id` alone is a good cache key for data that isn't tied to a particular commit such as ACLs. This will always match
    the same contents regardless of what `Key` it is stored at or what branch it is on.

### Iceberg Table

An Iceberg table object is exceedingly simple. The Iceberg table object only stores the path to the metadata JSON document that
should be read to describe the Iceberg table at this version.

This model puts a strong restriction on the Iceberg table. All metadata JSON documents must be stored and none of the built-in
iceberg maintenance procedures can be used. There are potentially serious issues regarding schema migrations in this model as well.
Therefore, the Iceberg table spec should be considered subject to change in the near future.

### View

### Delta Lake Table

## Contract for backing database

!!! warning
    todo

## Version Store

There are several expections of a version store. Some are listed below

!!! warning
    still missing some conditions
    
### Conflict Resolution
The API passes an ‘expectedHash’ parameter when it modifies a key. This is the commit that the client thinks is the most up to date (its HEAD). A backend will check to see if the key has been modified since that ‘expectedHash' and if, it will reject the requested modification with FAILED_PRECONDITION. This is basically an optimistic lock that accounts for the fact that the commit hash is global and nessie branch could have moved on from ‘expectedHash’ without modifying the key in question.

The reason for this condition is to behave like a ‘real’ database. You shouldn’t have to update your reference before transacting on tableA because it just happened to update tableB whilst you were preparing your transaction.

## Tiered Version Store

!!! warning
    todo
