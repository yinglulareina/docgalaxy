# Design Patterns

Design patterns are reusable solutions to commonly recurring problems in software design. The "Gang of Four" (GoF) book catalogued 23 patterns across three categories.

## Creational Patterns

**Singleton** – ensures only one instance of a class exists. Controversial because it introduces global state, making testing harder.

**Factory Method** – defines an interface for creating an object but lets subclasses decide which class to instantiate. Useful when the exact type isn't known until runtime.

**Builder** – separates the construction of a complex object from its representation. Enables fluent APIs: `new Pizza.Builder().crust("thin").topping("cheese").build()`.

## Structural Patterns

**Decorator** – attaches additional responsibilities to an object dynamically. Java's `BufferedInputStream` decorates `FileInputStream` without modifying it.

**Adapter** – converts the interface of a class into another interface clients expect. Bridges incompatible APIs.

**Composite** – composes objects into tree structures. Both leaf and branch nodes implement the same interface. Used in UI frameworks for the component hierarchy.

## Behavioral Patterns

**Observer** – defines a one-to-many dependency. When one object changes state, all dependents are notified. The foundation of event-driven architectures.

**Strategy** – defines a family of algorithms, encapsulates each, and makes them interchangeable. Switch sort algorithms without changing client code.

**Command** – encapsulates a request as an object. Supports undo/redo, queuing, logging.

## When to Use Patterns

Patterns are tools, not goals. Apply them when they solve a real problem you have, not to demonstrate knowledge or future-proof against imagined requirements.
