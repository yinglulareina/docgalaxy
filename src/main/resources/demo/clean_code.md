# Clean Code Principles

Clean code is code that is easy to read, easy to understand, and easy to change. Robert Martin's "Clean Code" formalized many principles that experienced programmers had accumulated over decades.

## Naming

Use names that reveal intent. A variable named `d` (elapsed time in days) is far worse than `elapsedDays`. Names should say *what* a thing is, not *how* it is implemented. Avoid abbreviations except for universally understood ones (e.g., `id`, `url`).

## Functions Should Do One Thing

A function that validates input, queries a database, formats a result, and logs the outcome does four things. Extract each into its own function. Small, focused functions are easier to test, name, and reuse.

## The DRY Principle

Don't Repeat Yourself. Every piece of knowledge should have a single, unambiguous representation. Duplication leads to divergence: two copies of the same logic will eventually disagree, and finding both copies when a bug is fixed is error-prone.

## Comments vs. Self-Documenting Code

A comment is an apology for failing to express intent in code. If you need a comment to explain *what* code does, rewrite the code. Reserve comments for explaining *why* – the business reason behind an unintuitive decision.

## Error Handling

Use exceptions rather than return codes. Exceptions separate the happy path from error handling, making both clearer. Catch exceptions at the boundary where you can meaningfully handle them, not at every call site.
