# API Design Principles

A well-designed API is a pleasure to use; a poorly designed one creates bugs, frustration, and tech debt. These principles apply to REST APIs, library APIs, and internal module interfaces.

## Consistency

Use consistent naming, parameter ordering, and error formats throughout. If one endpoint returns `{"error": "message"}`, all endpoints should. Inconsistency forces callers to handle special cases everywhere.

## Minimal Surface Area

Expose the minimum necessary. Every public method or endpoint is a promise you must maintain. It's easy to add capabilities; removing them breaks callers. When in doubt, keep it private until there's a clear use case.

## Fail Fast with Clear Errors

Validate inputs early and return actionable error messages. `400 Bad Request: 'email' must be a valid email address` is useful. `500 Internal Server Error` is not. Include the field name and what was wrong, not just that something was wrong.

## Idempotency

Design write operations to be safely retried. An `PUT /orders/{id}` with the same payload should produce the same result whether called once or ten times. This is critical for reliability under network failures.

## Versioning

Anticipate that your API will change. Version from day one (`/v1/`) even if v2 seems far away. Breaking changes in unversioned APIs cause cascading failures across all consumers.

## Documentation as a First-Class Citizen

The best API documentation includes working examples for every operation. A developer should be able to copy-paste an example and have it work immediately against your test environment.
