# Stage 2 - End-to-end CSV flow, offline
Branch: stage-2-csv-offline | Base: 52799d0 | Delivery: work-unit commits on the feature branch; PR chain strategy deferred until the PR is requested.
Test runner: `mvn test` (unit), `mvn verify` (integration). JDK 21 required.
Locator: odd/tasks/stage-2-csv-offline.md (repository-relative).

## Specs

S1 - Stage 2 scope (verbatim from PLAN-IMPLEMENTACION.md):
1. `CsvRowReader` (FastCSV) with dynamic mapping, delimiter/charset detection, empty-row skipping.
2. `CsvReportWriter` with es-AR defaults, BOM, injection guard, temp file + rename, `--force`.
3. `CoordinatesGeocoder` and `FileGeocoder`; `FakeGeocoder`/`FakeRouteCalculator` for tests.
4. `CalculateSurveyBatchUseCase` with `distance.mode=HAVERSINE`.
5. Minimal CLI: all arguments in 13, progress to stderr, console summary, exit codes.
6. Synthetic `samples/input-sample.csv`.

S2 - Try it (verbatim):
```bash
java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar \
  --input samples/input-sample.csv --output out/report.csv \
  --config config/application.properties --destination "-31.4201,-64.1888"
```
(with `geocoder.mode=COORDINATES`, `distance.mode=HAVERSINE`, and coordinates in `origen`.)
Done when (verbatim): the sample runs offline, the report opens correctly in a spreadsheet, exit codes behave as in 13.

S3 - TDD (verbatim): 1. Red: write a failing test for the smallest expected behavior. 2. Green: minimum code to pass. 3. Refactor: keep tests green. No production behavior without a test that first failed for it.

S4 - CLI contract (verbatim section 13): `--input` yes, `--output` yes, `--config` yes; `--destination`, `--event`, `--services`, `--summary`, `--force`, `--log-level`, `--help`, `--version` optional. Exit codes: `0`: batch completed with no row errors (skipped rows and fallback rows are fine; they appear in the summary). `1`: batch completed with at least one row `ERROR`. `2`: fatal error (invalid config, unreadable input, output not writable or already exists, destination not resolved, services unavailable, bundled services failed to start, incomplete bundle). The config does not define input/output paths; CLI arguments are the only source.

S5 - Output contract: fixed 30-column report from section 12.1; CSV formula-injection guard: text cells coming from the input that start with `=`, `+`, `-`, `@`, tab or CR are prefixed with `'` when written to CSV. Output is written to a temporary file and renamed on success. If the output exists, fail with `OUTPUT_EXISTS` unless `--force`. Config defaults: `output.csv.delimiter=;`, `output.csv.decimal=,`, `output.csv.bom=true`, `output.csv.charset=UTF-8`.

S6 - Input contract: BOM handled; delimiter and charset auto-detected unless configured. Fully empty rows are ignored silently and not counted. Header matching after normalization (trim, lower-case, accents removed, repeated spaces collapsed) on both config alias and file header. `personas` text `2`, `2,0` must produce the same integer.

S7 - Integration tests (verbatim from 15.3, Stage 2 subset): CSV input -> CSV report with fakes for geocoder/router; CSV edge cases: BOM, `;` and `,` delimiters, windows-1252, decimal comma, accents; Route failure fallback per row; fallback disabled => error; Bike/walk skipped; Destination from config, from `--destination`, missing => fatal; CSV formula-injection guard; output exists / `--force`; temp file renamed; Exit codes 0 / 1 / 2.

S8 - Architecture: `core` remains free of CLI, filesystem, HTTP, CSV/XLSX and vendor dependencies; new adapters live in `infra.csv`, `infra.maps`, `cli`. Reuse Stage 1 ports (`Geocoder`, `Router`, `EmissionFactorProvider`), `RowProcessor`, `SummaryAggregator` and the existing config model; do not reimplement their rules.

S9 - Risk: adding `de.siegmar:fastcsv` to `pom.xml` is a new dependency (high-risk item 5). The binary is not in the local Maven repository, so the build requires network access; if resolution fails, stop and report instead of substituting a hand-written parser.

## Tasks

- T1 — S1,S6,S8,S9 — FastCSV dependency and `CsvRowReader` with dynamic header mapping, BOM, delimiter/charset detection, empty-row skipping — inline — RED: `CsvRowReaderTest` could not resolve the new reader class — GREEN: 5 new tests — commit: 7e4a439
- T2 — S1,S5 — `CsvReportWriter` with 30 columns, es-AR defaults, BOM, injection guard, temp+rename, `--force` — inline — RED: `CsvReportWriterTest` could not resolve the new writer class — GREEN: 5 new tests — commit: pending
- T3 — S1,S8 — `CoordinatesGeocoder`, `FileGeocoder`, test fakes and `CalculateSurveyBatchUseCase` with HAVERSINE — inline — pending
- T4 — S4,S7 — minimal CLI with all arguments, stderr progress, console summary and exit codes — inline — pending
- T5 — S2,S7 — synthetic sample, offline end-to-end smoke, `mvn verify` and closure — inline — pending

## Log

L1 (2026-11-23): bueno podes revisar si podemos arrancar con el stage 2 ? de ser asi avisame que es lo que vas a hacer asi te doy el okay

L2 (2026-11-23): dale mandale

L3 (2026-11-23): Stage 2 authorized. Stage 1 is merged in PR #1; main advanced to 52799d0 with the PRD moved under docs/. The pending plan checklist edit was committed as 8d29e0c before creating this branch. Route: inline; delivery: work-unit commits with TDD RED -> GREEN. FastCSV is absent from the local Maven repo and requires network resolution. Config examples default to PHOTON/ROUTE and to an ignored factors file, so the offline smoke needs temporary overrides outside the worktree. Push and PR are included in this authorization.

L4 (2026-11-23): FastCSV 4.4.1 resolved from Maven Central and pinned in pom.xml. T1 RED observed as a compilation failure for the missing `CsvRowReader`; GREEN after implementing charset/delimiter detection, normalized header mapping and blank-row skipping. Full `mvn test` green (53 tests). Current input file is buffered in memory for format detection, documented as a shortcut in code.

L5 (2026-11-23): T1 was escalated as high-risk item 5 (new dependency) and the user granted native review. START created lineage `review-43aa386c83cf54b5`. Two subagent transport failures (`opencode_review_transport_relay_refused`) were resolved when the retry returned a complete reviewer result; its raw JSON was captured through the exact bound `review.capture-result` invocation. Native status reported `approved` with four non-blocking advisory findings (R3-1..R3-4), and the exact `review.acknowledge-approved` token was burned once (`gentle-ai.review-acknowledged/v1`, authority `burned`). Reviewed boundary for T2 is commit 7e4a439.

L6 (2026-11-23): T2 RED observed as a compilation failure for the missing `CsvReportWriter`; two GREEN iterations fixed UTF-8 BOM decoding in the assertion and the directory-target error path. The writer emits the fixed 30-column header with CRLF, UTF-8 BOM, injection guard on text columns, temp-file rename, and OUTPUT_EXISTS/OUTPUT_WRITE_ERROR mapping. Full `mvn test` green (58 tests). Values are passed already formatted; number formatting belongs to the use case in T3.
