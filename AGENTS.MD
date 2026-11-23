# ZON development guidelines

## Implementation plan

- Always read the root `PLAN-IMPLEMENTACION.md` before starting any task in
  this repository. Use it to identify the relevant stage, package boundaries,
  conventions, and acceptance checks for the requested work.

## Language

- Use English for new Java identifiers and test names.
- Write code comments and Javadoc in Spanish.
- Follow the project's localization conventions for user-facing text.
- Preserve existing public names unless the task calls for renaming them.

## Java conventions

- Use PascalCase for classes and interfaces, camelCase for methods and
  variables, and UPPER_SNAKE_CASE for constants.
- Follow the repository's package structure, formatter, and static analysis
  configuration. Use nearby code as a guide when there is no configured rule.
- Keep changes focused on the task and reuse existing project APIs where practical.

## Build and tests

- Use the Java version and build tool specified by the repository.
- Use the repository's Maven or Gradle wrapper when it is available.
- Follow the existing test framework and conventions. Add or update tests
  when changing behavior.
- Run the tests relevant to the change. In the final response, report the
  commands you ran, their results, and any checks you could not run.

## Commits

- Keep each commit focused on one change.
- Write a short Spanish subject in the form `type: description`. Start the
  description with an imperative verb and omit the final period.
- Use `feat` for new functionality, `fix` for bug fixes, `docs` for
  documentation, `refactor` for code restructuring, `test` for test changes,
  and `chore` for routine maintenance.
- Add a body in Spanish when the reason for the change needs context.
- Example: `fix: redondea los totales de impuestos`.
