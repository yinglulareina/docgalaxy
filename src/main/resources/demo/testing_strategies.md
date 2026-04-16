# Testing Strategies

Automated tests are the only reliable way to know that code works. They enable confident refactoring, catch regressions early, and serve as executable documentation of intended behavior.

## The Testing Pyramid

**Unit tests** form the base. They test a single function or class in isolation, with dependencies mocked or stubbed. They run in milliseconds and should cover every meaningful code path. A healthy codebase has hundreds or thousands of them.

**Integration tests** sit in the middle. They test that multiple components work together correctly – a service and its database, an HTTP handler and its middleware chain. They're slower but catch a different class of bugs.

**End-to-end tests** sit at the top. They exercise the full system from the user's perspective. They're slow, brittle, and expensive to maintain. Keep them few and focused on critical user journeys.

## Test-Driven Development (TDD)

Write the failing test before writing the implementation. This forces you to think about the interface before the internals, and ensures every line of code is covered by at least one test. Red → Green → Refactor.

## What to Test

Focus on behavior, not implementation. Test *what* the code does, not *how* it does it. Brittle tests that break when you rename an internal method indicate over-specification.

Test edge cases: empty input, maximum values, concurrent access, null values, error paths. Happy-path tests catch obvious bugs; edge-case tests catch subtle ones.

## Mutation Testing

Run a tool that introduces deliberate bugs (mutants) into your code and checks whether your tests catch them. Mutation score – the percentage of mutants killed – is a rigorous measure of test quality, unlike raw coverage.
