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
- T2 — S1,S5 — `CsvReportWriter` with 30 columns, es-AR defaults, BOM, injection guard, temp+rename, `--force` — inline — RED: `CsvReportWriterTest` could not resolve the new writer class — GREEN: 5 new tests — commit: 008bc81
- T3 — S1,S8 — `CoordinatesGeocoder`, `FileGeocoder`, test fakes and `CalculateSurveyBatchUseCase` with HAVERSINE — inline — RED: `GeocoderAdaptersTest` and `CalculateSurveyBatchUseCaseTest` could not resolve the new classes — GREEN: 6 new tests — commit: 26f3840
- T4 — S4,S7 — minimal CLI with all arguments, stderr progress, console summary and exit codes — inline — RED: `CliTest` could not resolve `Main.VERSION` and `Main.run` — GREEN: 4 new tests — commit: 024a761
- T5 — S2,S7 — synthetic sample, offline end-to-end smoke, `mvn verify` and closure — inline — verification-only task (no new production behavior, so no RED cycle applies): 1 integration test plus a real `java -jar` smoke, both green — commit: 4a51321
- T6 — S4,S5 — reject `--summary` colliding with input/output/config before any write — inline — RED: `CliTest` returned exit 0 and the files were overwritten — GREEN: 2 new tests, guard in `Main.validateDistinctPaths` — commit: 623cff4
- T7 — S6,S7,S8 — bounded-memory pipeline: lazy `RowStream`, incremental `CsvReportWriter.Report`, `SummaryAggregator.Accumulator` and `ReportSink` — inline — RED: compile errors on the three new APIs — GREEN: 1 new writer test and the adapted suite — commit: b4fbe07
- T8 — S6 — honor `input.headerRow` when locating the header (allows a title row above it) — inline — same RED/GREEN as T7 — commit: b4fbe07
- T9 — S7 — `distance_mode=NOT_APPLICABLE` for `SKIPPED_NON_MOTORIZED` rows — inline — RED: use case wrote `HAVERSINE` — GREEN: 1 new use-case test — commit: b4fbe07
- T10 — S4 — reject `--summary` pointing at the same file as input/output/config via hard link (`Files.isSameFile`) — inline — RED: CLI exit 0 and input overwritten — GREEN: `CliTest.refusesSummaryHardLinkedToInput`, exit 2, input hash unchanged — commit: b4c6aee
- T11 — S8 — detect the delimiter on the configured `input.headerRow`, not the first line — inline — RED: `MISSING_REQUIRED_COLUMN` with a comma CSV after a semicolon title — GREEN: `CsvRowReaderTest.detectsDelimiterOnTheConfiguredHeaderRow` — commit: b4c6aee
- T12 — S8 — treat a UTF-8 sequence truncated by the 64 KB probe boundary as incomplete input, not invalid encoding — inline — RED: `Córdoba` decoded as `CÃ³rdoba` — GREEN: `CsvRowReaderTest.detectsUtf8WhenTheSampleEndsMidCharacter` — commit: b4c6aee
- T13 — S5 — keep `OUTPUT_EXISTS` when the target appears between `open` and `commit` and `--force` is absent — inline — RED: `commit()` silently replaced the file — GREEN: `CsvReportWriterTest.refusesToPublishWhenOutputAppearsAfterOpen` — commit: b4c6aee
- T14 — S7 — write literal zeros in `distance_km`, `consumption_value` and `emissions_kgco2e` for skipped non-motorized rows (plan section 9.4) — inline — RED: cells absent — GREEN: use-case and end-to-end assertions — commit: b4c6aee
- T15 — S4 — reject `--summary` colliding with `factors.file` and, in `FILE` mode, with `geocoder.file` — inline — RED: CLI exit 0/1 and config-read file overwritten — GREEN: 2 new `CliTest` tests, exit 2 and files unchanged — commit: 90b4206
- T16 — S8 — ignore delimiters inside quoted header fields during auto-detection — inline — RED: `INPUT_UNREADABLE` with a quoted `nota;;;;;;` column in a comma CSV — GREEN: `CsvRowReaderTest.ignoresDelimitersInsideQuotedHeaderFields` — commit: 90b4206

## Log

L1 (2026-11-23): bueno podes revisar si podemos arrancar con el stage 2 ? de ser asi avisame que es lo que vas a hacer asi te doy el okay

L2 (2026-11-23): dale mandale

L3 (2026-11-23): Stage 2 authorized. Stage 1 is merged in PR #1; main advanced to 52799d0 with the PRD moved under docs/. The pending plan checklist edit was committed as 8d29e0c before creating this branch. Route: inline; delivery: work-unit commits with TDD RED -> GREEN. FastCSV is absent from the local Maven repo and requires network resolution. Config examples default to PHOTON/ROUTE and to an ignored factors file, so the offline smoke needs temporary overrides outside the worktree. Push and PR are included in this authorization.

L4 (2026-11-23): FastCSV 4.4.1 resolved from Maven Central and pinned in pom.xml. T1 RED observed as a compilation failure for the missing `CsvRowReader`; GREEN after implementing charset/delimiter detection, normalized header mapping and blank-row skipping. Full `mvn test` green (53 tests). Current input file is buffered in memory for format detection, documented as a shortcut in code.

L5 (2026-11-23): T1 was escalated as high-risk item 5 (new dependency) and the user granted native review. START created lineage `review-43aa386c83cf54b5`. Two subagent transport failures (`opencode_review_transport_relay_refused`) were resolved when the retry returned a complete reviewer result; its raw JSON was captured through the exact bound `review.capture-result` invocation. Native status reported `approved` with four non-blocking advisory findings (R3-1..R3-4), and the exact `review.acknowledge-approved` token was burned once (`gentle-ai.review-acknowledged/v1`, authority `burned`). Reviewed boundary for T2 is commit 7e4a439.

L6 (2026-11-23): T2 RED observed as a compilation failure for the missing `CsvReportWriter`; two GREEN iterations fixed UTF-8 BOM decoding in the assertion and the directory-target error path. The writer emits the fixed 30-column header with CRLF, UTF-8 BOM, injection guard on text columns, temp-file rename, and OUTPUT_EXISTS/OUTPUT_WRITE_ERROR mapping. Full `mvn test` green (58 tests). Values are passed already formatted; number formatting belongs to the use case in T3. Native assessment for commit 008bc81: `risk=medium`, `review_due=false`, `review_due_reason=under_budget`; reviewed boundary remains 7e4a439.

L7 (2026-11-23): T3 RED observed as compilation failures for the missing geocoder adapters and use case. GREEN after implementing coordinates/file geocoders, reusable test fakes, destination resolution and row-to-report mapping that reuses `RowProcessor` and `SummaryAggregator`. One test correction made the fuel explicit so `fuel_inferred` is false. Full `mvn test` green (64 tests). HAVERSINE is handled inside `RowProcessor`; no router is called in that mode. Native assessment for commit 26f3840: `risk=medium`, `review_due=false`, `review_due_reason=under_budget`; reviewed boundary remains 7e4a439.

L8 (2026-11-23): T4 RED observed as `CliTest` failing to resolve `Main.VERSION` and `Main.run` against the placeholder CLI. GREEN after implementing argument parsing, config/factor loading, geocoder selection, stderr progress, console/JSON summary and exit codes 0/1/2; two GREEN iterations fixed the `Router` lambda and moved `CliTest` into the `cli` package so `run` stays package-private. Full `mvn test` green (68 tests). The shade plugin already points to `ar.zon.calculadora.cli.Main`, so the runnable jar needs no pom change. Stage 2 restricts execution to HAVERSINE + COORDINATES/FILE/bundled-off; those guards are CLI-level and disappear when later stages add the real adapters. Native assessment for commit 024a761: `risk=medium`, `review_due=false`, `review_due_reason=under_budget`; reviewed boundary remains 7e4a439.

L9 (2026-11-23): T5 added `samples/input-sample.csv` with 15 deterministic rows (OK, inferred fuel, gnc, moto, colectivo, skipped non-motorized, invalid personas, unknown transport/fuel, unknown place, per-row destino, injection-like origin) and `OfflineSmokeIT`, which runs the CLI on that sample with temp offline config/factors and asserts exit 1, BOM, 30-column header, 16 lines, Spanish decimal comma, skip/error statuses and the injection prefix. `mvn verify` green: 68 unit tests plus 1 integration test; shaded jar built. Real `java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar` smoke from the same sample printed `filas=15 correctas=7 con_respaldo=0 omitidas=2 errores=6 km=45.41 kgco2e=9.66 avisos=USING_PLACEHOLDER_FACTORS`, exit 1, BOM `EFBBBF`, 16 lines, and a summary JSON with rows_total=15. `--version`/`--help`/no-args exit 0 and a fatal input error exits 2. Per-row `destino` is mapped but Stage 2 uses the batch destination only; that is a documented later-stage behavior, not a regression. Smoke scratch lived under the approved temp directory and was removed.

L10 (2026-11-23): Final docs commit 5e09c45 records the T5 hash because amending 693d28e to 4a51321 changed it. All five work units are committed on stage-2-csv-offline; all native assessments after T1 returned `review_due=false`/`under_budget`. Push and PR toward main remain as the last authorized step.

L11 (2026-11-23): Branch stage-2-csv-offline pushed to origin and PR #2 opened against main: https://github.com/Mudo0/calculadora-zon/pull/2. Delivery as one PR (1707 changed lines) follows the user's explicit "push the branch and open a PR" instruction; no chain was requested. Stage 2 is closed as verified; merge and any later stage are separate human decisions.

L12 (2026-11-23): PR #2 received four inline review comments. Two P1: (a) `--summary` could overwrite `--input` or `--output` without `--force`; (b) the reader buffered the whole file and the use case accumulated every report row, against the plan's incremental requirement. Two P2: (c) `input.headerRow` was ignored; (d) `PIE`/`BICI` rows wrote `distance_mode=HAVERSINE` instead of `NOT_APPLICABLE` (PLAN-IMPLEMENTACION.md section 9.4). User: "dale". Route: inline, TDD RED -> GREEN. Verified: `mvn test` green (73 tests), `mvn verify` green (73 unit + 1 IT), real jar smoke unchanged (`filas=15 correctas=7 con_respaldo=0 omitidas=2 errores=6 km=45.41 kgco2e=9.66 avisos=USING_PLACEHOLDER_FACTORS`, exit 1, BOM `EFBBBF`), collision guard returns exit 2 with the input hash unchanged. Native assessment over commits 623cff4 and b4fbe07 against base 7e4a439: `risk=medium`, `review_due=false`, `review_due_reason=under_budget`; reviewed boundary remains 7e4a439. Scratch lived under the approved temp directory and was removed.

L13 (2026-11-23): Second review round on PR #2 (2026-11-24T02:24:56Z) added five cases: hard-link path identity, delimiter detection on `input.headerRow`, UTF-8 split at the 64 KB probe boundary, `commit()` racing the output file without `--force`, and the literal zeros required by section 9.4 (already present in the first round). User: "dale". Route: inline, TDD RED -> GREEN; all five RED failures were observed before the fix. Verified: focused `mvn test` green (26 tests), `mvn verify` green (78 unit + 1 IT), real jar smoke unchanged (`filas=15 correctas=7 con_respaldo=0 omitidas=2 errores=6 km=45.41 kgco2e=9.66 avisos=USING_PLACEHOLDER_FACTORS`, exit 1, BOM `EFBBBF`) with skipped rows now `NOT_APPLICABLE` and three literal zeros; hard-link `--summary` exits 2 with the input hash unchanged. Scratch lived under the approved temp directory and was removed.

L14 (2026-11-23): Third review round on PR #2 (2026-11-24T02:39:48Z) added two cases: `--summary` could overwrite `factors.file` or the `FILE`-mode geocoder catalog, and delimiter auto-detection counted separators inside quoted header fields. User: "de vuelta MAS comentarios" (asked to read them); the fixes follow the previously authorized review-fix scope. Route: inline, TDD RED -> GREEN; all three new tests failed before the fix. Verified: focused `mvn test` green (18 tests), `mvn verify` green (81 unit + 1 IT), real jar smoke on a comma CSV with a quoted `nota;;;;;;` column exits 0 with one correct row, while `--summary` collisions with factors and catalog exit 2 with both file hashes unchanged. Scratch lived under the approved temp directory and was removed.
