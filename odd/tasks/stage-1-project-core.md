# Stage 1 - Project skeleton + pure core
Branch: `stage-1-project-core` | Delivery: work-unit commits on the feature branch; PR chain strategy deferred until a PR is requested.
Test runner: `mvn test` (unit), `mvn verify` (aggregate). JDK 21 required.

## Specs

### S1 - Stage 1 scope (verbatim from `PLAN-IMPLEMENTACION.md`, section 16)
```
1. Maven project, package structure, JUnit/AssertJ wired, `mvn test` green on the empty skeleton.
2. Add project bootstrap artifacts: `.gitignore`, `.gitattributes` (recommended), `.editorconfig` (recommended), `README.md` skeleton, `docs/` skeleton, `config/application.example.properties`, `config/emission-factors.example.properties`.
3. `.gitignore` must cover at least: `target/`, `out/`, `dist/`, `logs/`, IDE files (`.idea/`, `*.iml`, `.vscode/`, `.classpath`, `.project`, `.settings/`), OS files (`.DS_Store`, `Thumbs.db`), local env/config overrides (`.env*`, `config/application.properties`, `config/emission-factors.properties`), generated reports/runtime outputs, and local map data (`maps/`, `photon_data/`, `*.osm.pbf`, `*.osrm*`, `osrm-data/`). Keep tracked examples/synthetic fixtures with negation rules where needed.
4. Config model + `.properties` loader + factors loader.
5. Domain model, statuses, error codes.
6. Normalization and validation rules (9.2-9.5).
7. `HaversineCalculator`, `ConsumptionCalculator`, `EmissionCalculator`.
8. Summary aggregation.
```

### S2 - Stage 1 try it / done when (verbatim)
```
Try it: unit tests only.
Done when: all unit tests in 15.2 pass, including the four hand-calculated cases, and bootstrap artifacts exist with the expected ignore coverage.
```

### S3 - TDD rule (verbatim from 15.1)
```
1. **Red:** write a failing test for the smallest expected behavior.
2. **Green:** minimum code to pass.
3. **Refactor:** keep tests green.

No production behavior without a test that first failed for it. Bug fixes start with a reproducing test. Tests are deterministic; Photon/OSRM are covered through ports/fakes, with optional smoke tests against real local services (tagged and skipped by default).
```

### S4 - Dependency rule (verbatim from 5.2 and 5.7)
```
- `core` has **no dependency** on `cli`, `infra`, file libs, HTTP clients, JSON libs, or map vendors. (`slf4j-api` is the only allowed third-party import.)
- `core.config` holds the immutable config model; **parsing** `.properties` lives in `infra.config`.
```
```
| `core.*` | Java stdlib, core interfaces/types | CLI libs, FastCSV/FastExcel, HTTP client impl, OSRM/Photon JSON, filesystem/process |
```

### S5 - Main config keys (verbatim excerpts from 8.1)
```
personas.min=1
personas.max=5
default.fuel.auto=NAFTA
geocoder.mode=PHOTON               # PHOTON|COORDINATES|FILE
distance.mode=ROUTE                # ROUTE|HAVERSINE|BOTH
distance.fallback=HAVERSINE        # HAVERSINE|NONE (only applies to ROUTE/BOTH)
distance.decimals=2
geo.cache.enabled=true
geo.cache.maxEntries=10000
route.cache.enabled=true
route.cache.maxEntries=10000
factors.file=./config/emission-factors.properties
```
```
- `distance.mode` is the **only** mode switch. For development without map services use `geocoder.mode=COORDINATES` (or `FILE`) with `distance.mode=HAVERSINE`.
```
Added PR-review acceptance criterion: both destination coordinates must be finite before their ranges are checked; `NaN` and infinities are invalid configuration.

### S6 - Factors (verbatim from 8.2)
```
**These values are placeholders for testing only. They are NOT real figures.** The client must replace them once an official source is defined.
```
```
- `vehicle_km` (AUTO, MOTO): `liters_or_m3 = km * consumption_per_100km / 100`; `kgCO2e = liters_or_m3 * kgco2e_per_fuel_unit`. Emissions are **per vehicle** (not multiplied by `personas`).
- `passenger_km` (COLECTIVO): `kgCO2e = km * personas * kgco2e_per_passenger_km`. Consumption is not calculated (left empty with note `consumption_not_applicable`).
```
```
Key pattern: `factor.<transport>.<fuel>.<attribute>` in lower case.
```
```
- Round placeholder numbers are intentional: hand-calculated tests stay trivial.
- Every calculated row records factor key, source and `factor_is_placeholder`.
- If **any** row used a factor whose `source` starts with `PLACEHOLDER`, the console summary and the JSON summary show a warning `USING_PLACEHOLDER_FACTORS`, so a report can never be mistaken for real figures.
- Missing factor => row error `FACTOR_NOT_FOUND`, never a silent zero.
```
Added PR-review acceptance criterion: absent or empty required attributes are not `0.0`; `vehicle_km` requires `fuel_unit`, `consumption_per_100km` and `kgco2e_per_fuel_unit`; `passenger_km` requires `kgco2e_per_passenger_km`; every supplied numeric attribute must be finite and non-negative before the factor is built.

### S7 - Transport and fuel normalization (verbatim from 9.2 and 9.3)
```
| auto, coche, automovil | `AUTO` |
| moto, motocicleta | `MOTO` |
| colectivo, omnibus, bondi, bus | `COLECTIVO` |
| caminando, a pie, pie | `PIE` |
| bicicleta, bici | `BICI` |
```
```
Anything else (including electric vehicles, airplane, etc.) => `UNKNOWN_TRANSPORT`.
```
```
| `MOTO` | any/missing | `NAFTA` |
| `AUTO` | nafta/gasolina | `NAFTA` |
| `AUTO` | gasoil/diesel | `GASOIL` |
| `AUTO` | gnc/gas | `GNC` |
| `AUTO` | missing | `default.fuel.auto` (`NAFTA`), `fuel_inferred=true` |
| `COLECTIVO` | missing/any | `GASOIL` generalized, `fuel_inferred=true` |
| `PIE/BICI` | any | ignored |
```
```
Canonical fuel names: `NAFTA`, `GASOIL`, `GNC` (`diesel` is only an input alias). All inference is visible in report columns.
```
```
- Header matching is done after normalization (trim, lower-case, accents removed, repeated spaces collapsed), on both the config alias and the file header.
```

### S8 - Row processing order (verbatim from 9.5)
```
1. Ignore fully empty rows.
2. Normalize `transporte`: missing => `MISSING_TRANSPORT`; unknown => `UNKNOWN_TRANSPORT`.
3. `PIE`/`BICI` => `SKIPPED_NON_MOTORIZED` and stop (other fields are not validated).
4. Validate `personas`: integer within `personas.min`..`personas.max` (per-transport override if configured); otherwise `INVALID_PEOPLE_COUNT`.
5. Validate `origen` present => else `MISSING_ORIGIN`.
6. Resolve fuel (9.3).
7. Geocode origin (destination was resolved once at startup).
8. Distance: route, with per-row fallback per `distance.*`.
9. Resolve factor, compute consumption and emissions.
```

### S9 - Unit tests required (verbatim from 15.2)
```
- Config loading/defaults/validation (unknown mode, invalid number, missing destination).
- Header normalization, aliases and column mapping.
- Transport/fuel normalization and inference; `UNKNOWN_TRANSPORT` for EV.
- `personas`: empty, 0, 6, `2.0`, `2,0`, configured range, per-transport override.
- Row order rules (9.5): skipped rows are not validated further.
- Haversine known distance (fixed Earth radius constant documented).
- Consumption/emissions formulas (hand-calculated, below).
- Factor lookup, missing factor, placeholder flag and `USING_PLACEHOLDER_FACTORS`.
- Geocoder candidate selection determinism; cache hit avoids second call; coordinates order (**Photon and OSRM use lon,lat**; test the mapping explicitly).
- Summary aggregation (totals from unrounded values).
```

### S10 - Hand-calculated cases (verbatim from 15.2)
```
| Case | Calculation | Expected |
|---|---|---|
| AUTO NAFTA, 50 km | 50 × 10 / 100 = 5.0 l; 5.0 × 2.0 | 5.0 l, 10.0 kgCO2e |
| AUTO GNC, 100 km | 100 × 10 / 100 = 10 m3; 10 × 2.0 | 10 m3, 20.0 kgCO2e |
| MOTO NAFTA, 25 km | 25 × 4 / 100 = 1.0 l; 1.0 × 2.0 | 1.0 l, 2.0 kgCO2e |
| COLECTIVO, 40 km, personas 3 | 40 × 3 × 0.1 | 12.0 kgCO2e, no consumption |
```

### S11 - Domain model and codes (verbatim from section 10)
```
SurveyRow            # raw dynamic row + row number + source
NormalizedTrip       # origen, destino, transporte, combustible, personas, evento
GeoPoint             # lat, lon, label, provider, candidatesCount, selectionReason
DistanceResult       # km, mode=ROUTE|HAVERSINE, straightKm, fallbackUsed, provider
ConsumptionResult    # value, unit (l|m3), factorKey, factorSource
EmissionResult       # kgCo2e, basis, factorKey, factorSource, isPlaceholder
RowResult            # fields + outputs + status + errorCode + errorMessage + notes
BatchSummary         # totals, counters, km/consumption/kgco2e, warnings
CalculationConfig    # immutable config
```
```
- `OK` (includes rows computed with `distance.mode=HAVERSINE` chosen on purpose; `distance_mode=HAVERSINE`, `distance_fallback_used=false`)
- `OK_FALLBACK_HAVERSINE` (route failed, straight line used)
- `SKIPPED_NON_MOTORIZED`
- `ERROR`
```
```
- `MISSING_ORIGIN`, `MISSING_TRANSPORT`, `UNKNOWN_TRANSPORT`, `INVALID_PEOPLE_COUNT`
- `LOCATION_NOT_FOUND` (origin not found), `GEOCODER_ERROR` (service failure)
- `ROUTE_NOT_FOUND`, `ROUTER_ERROR` (service failure)
- `FACTOR_NOT_FOUND`
```
```
- `INVALID_CONFIG`, `MISSING_REQUIRED_COLUMN`, `INPUT_UNREADABLE`, `UNSUPPORTED_FORMAT`
- `OUTPUT_EXISTS`, `OUTPUT_WRITE_ERROR`
- `DESTINATION_NOT_RESOLVED`, `SERVICE_UNAVAILABLE` (health-check failed or too many consecutive failures)
- Bundled mode: `BUNDLE_INCOMPLETE` (a bundled executable/data file is missing), `PORT_IN_USE`, `SERVICE_START_FAILED` (a bundled service was not healthy within `services.startTimeoutSeconds`)
```

## Tasks

- T1 — S1,S2 — bootstrap artifacts: `.gitignore`, config examples, README/docs skeleton — inline — RED: baseline skeleton test green before artifacts — GREEN: `mvn test` after artifacts — commit: `ae3812c` — P3 fixed: original PRD restored at its root path and linked from `docs/README.md`; final commit recorded in L9
- T2 — S4,S5,S6,S9 — immutable config model, `.properties` loader, factor loader/provider — inline — RED: missing config/emission classes in `PropertiesConfigLoaderTest` and `PropertiesEmissionFactorProviderTest` — GREEN: 9 new tests — commit: `ce12208` — P1/P2 fixed: four new RED tests, GREEN after validation changes — commit: `6003715`
- T3 — S7,S8,S9,S11 — domain model, statuses/error codes, normalization and row validation — inline — RED: missing normalization/ingest classes in `RowNormalizationTest` — GREEN: 9 new tests — commit: `bf64bc8`
- T4 — S6,S8,S9,S10,S11 — Haversine, consumption, emission calculators and summary aggregation — inline — RED: missing distance/emission/summary types in `CalculationServicesTest` and `BatchSummaryTest` — GREEN: 10 new tests including four hand-calculated cases — commit: `6e18692`
- T5 — S4,S9,S11 — geocoder/distance ports, candidate selection, bounded cache policy, row processing — inline — RED: missing ports/processor in `GeoPortsTest` and `RowProcessingTest` — GREEN: 17 new tests — commit: `b9d4b84`

## Log

### L1 (2026-11-23)
quiero que leamos @PLAN-IMPLEMENTACION.md y preparemos todo para hacer el stage 1. primero miralo bien y reportame los pasos que haras antes de mandarte a hacer.

### L2 (2026-11-23)
bueno ahi corregí la version de java asi que podemos arrancar

### L3 (2026-11-23) — analysis and route
Branch `main` was clean; `pom.xml`, package placeholders, `Main`/`MainTest`, `.gitignore`, `.editorconfig`, `.gitattributes` and minimal config examples already exist. Stage 0 is not started but Stage 1 is independent. Verified `java -version` = OpenJDK 21.0.2 and Maven uses it. RDD is on by default; each work-unit commit gets `gentle-ai review assess` against the previous reviewed boundary. Route: inline (no writer trigger, no parallel independent units). Delivery strategy: work-unit commits on this branch; no push or PR without explicit authorization, so the chain-strategy choice is deferred to PR time. The on-disk plan contains mojibake (`CÃ³rdoba`, `â€“`); quoted strings above are literal, new artifacts use correct accents.

### L4 (2026-11-23) — progress
T1 through T5 implemented with observed RED then GREEN evidence. Work-unit commits: `ae3812c`, `ce12208`, `bf64bc8`, `6e18692`, `b9d4b84`. Each commit was assessed with `gentle-ai review assess --cwd . --agent opencode --base-ref ca91b08 --committed-only --json`; result: `risk=medium`, `review_due=false`, `review_due_reason=under_budget`. No native review was started because RDD did not mark any slice due.

### L5 (2026-11-23) — verification
`mvn test` green after each task. Final checks: `mvn verify` green, `java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar` prints `ZON Calculadora`, `git diff --check` clean. Core imports are only `java.*` and `ar.zon.calculadora.core.*`; `.properties` parsing and coordinate mapping live in `infra`. Engram mirror is pending because the memory store rejected the current project/session; the local document is authoritative.

### L6 (2026-11-23) — closure
Stage 1 complete within the authorized scope. Risk: none (new internal code only; no existing public CLI/config contract broken, no data migration, no concurrency or delivery change). Push, PR and merge remain user decisions.

### L7 (2026-11-23) — PR review corrections (verbatim)
P1: Los factores incompletos producen ceros
El lector convierte cualquier atributo numérico ausente o vacío en 0.0. Si se omite kgco2e_per_fuel_unit en un factor vehicle_km, la fila puede quedar como OK con cero emisiones. Validá los campos requeridos según la base del factor y rechazá valores negativos o no finitos antes de construirlo.
P2: La validación acepta coordenadas NaN
El lector acepta NaN como número y Math.abs(NaN) > 90 devuelve falso. Por ejemplo, destination.lat=NaN con una longitud válida pasa el control y puede producir una distancia inválida. Comprobá que ambas coordenadas sean finitas antes de validar sus rangos.
P2: La eliminación del PRD deja referencias sin destino
El PR elimina el PRD original, pero el plan todavía lo cita por nombre y conserva referencias a sus requisitos. Este resumen registra los cambios del Incremento 1 y no contiene el documento base. Conservá el PRD o indicá su nueva ubicación y actualizá las referencias.

### L8 (2026-11-23) — correction plan
Reopen T1 and T2. T2 covers P1 (basis-specific required factor attributes and finite non-negative numbers) and P2/NaN (finite destination coordinates before range checks). T1 covers P3 (restore the original PRD and keep `docs/prd-delta.md` as the delta only). All code corrections follow RED → GREEN.

### L9 (2026-11-23) — correction evidence
RED: 4 new tests failed (`rejectsVehicleFactorWithoutRequiredEmissionAttribute`, `rejectsPassengerFactorWithoutRequiredAttribute`, `rejectsNegativeAndNonFiniteNumbers`, `rejectsNonFiniteDestinationCoordinates`). GREEN: all 48 tests pass after `6003715`. P3 verified structurally: the PRD is no longer deleted in the diff against `origin/main`, `PLAN-IMPLEMENTACION.md` still resolves its original filename, and `docs/README.md` links it explicitly.
