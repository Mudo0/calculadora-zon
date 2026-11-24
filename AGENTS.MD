# ZON development guidelines

## Project context and skills

Before starting any task, read:

- `PLAN-IMPLEMENTACION.md` at the repository root.
- `.agents/skills/git-workflow/SKILL.md` before creating branches, opening Pull Requests, or filing issues.
- `.github/PULL_REQUEST_TEMPLATE.md` whenever preparing a Pull Request.

If additional project skills exist under `.agents/skills/`, check their `SKILL.md` when their scope matches the task.

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

## Boundaries

- Do not modify secrets, credentials, `.env` files, or existing database
  migrations without explicit confirmation.
- Avoid editing shared configuration or files owned by other team members
  unless the task explicitly requires it.
- Do not run destructive git commands (`reset --hard`, `checkout --`,
  `clean -fd`) or push changes without explicit user instruction.

## Dependencies and tooling

- Do not add new external dependencies or change build/dependency versions
  without explicit approval.
- Prefer existing project utilities and approved local/open-source tooling
  over introducing new external services.

## Ambiguity and blockers

- When requirements or package boundaries are ambiguous, ask a concise
  clarifying question or state explicit assumptions before editing code.
- If a build, test, or lint command fails due to an environment issue, report
  the exact error instead of masking or skipping the check.

## Final response

- Respond in Spanish, concisely and directly.
- Include the commands run, their outcomes, any skipped checks, modified
  files, and open assumptions or risks.
