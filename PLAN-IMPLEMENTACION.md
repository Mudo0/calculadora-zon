# Implementation Plan - ZON Calculadora (Increment 1)

> **Revision v4.** Stages 0-6 can be implemented one at a time. Adds native maps setup (Annex A), dockerized OSRM/Photon setup for development and easy deployment (Annex C), and the self-contained offline bundle (Annex B). **Docker is optional for dev/deploy of map services; the final client bundle remains Docker-free.** Attendance counting is outside the app.

## 1. Purpose

This document is the execution plan for building Increment 1 of the survey distance/consumption/emissions calculator described in `PRD – Calculadora de ITEC.md`. It defines what to build, in which order, with which contracts, defaults, and checks.

It is **not** the LLD or ADR. The work is split in **stages**; each stage leaves something runnable and testable, so it can be implemented on its own (see section 16). Stages 0 and 1 are independent and can be done in parallel.

## 2. Confirmed Inputs

| Topic | Confirmed decision |
|---|---|
| Client | ITEC is the client. Do not use `itec` in artifact/package identifiers. |
| Geography | Primarily Córdoba, Argentina; design must work for any region. |
| Destination | Same destination for every row in an event; configured once per run/event. |
| Primary objective | 1) route distance in km from survey origin to event destination, 2) fuel consumption, 3) carbon emissions. |
| Row semantics | **1 row = 1 survey respondent = 1 trip/vehicle.** Other people who came in the same vehicle must not answer the survey; they are counted through `personas`. |
| Emissions focus | Emissions **per vehicle** that attended the event (AUTO/MOTO). Per-person emissions are not a goal and are not reported. COLECTIVO is attributed per passenger-km (see 8.2). |
| Attendance / duplicates | Counting event attendees and detecting duplicate answers is done **outside** this app. The app only reports the **total number of survey rows**. |
| People per trip | `personas` must be an integer; default range **1-5**, configurable. Empty or out of range is a row error. |
| Cost/price | Out of scope. No fuel price, no monetary cost, no currency. |
| Trip direction | One-way only: origin -> event. |
| Distance mode | Main: detailed route. Per-row fallback: straight line (Haversine) when route fails. Fallback must be explicitly labeled. |
| Bike/walk | Not calculated. Rows are reported as skipped/non-motorized with zero consumption/emissions. |
| Public transport | Generalized configurable calculation (example: bus = diesel/passenger-km factor). |
| Airplane | Out of Increment 1. |
| Electric vehicles | **Out of Increment 1.** An `electrico`-like value is treated as unknown transport. Model stays extensible. |
| Emission factors | **No official source yet.** Placeholder test values, clearly marked as not real; the report flags their use (see 8.2). |
| Origin data | Neighborhood / locality / city level. No exact home addresses, no personal data. Processing is local anyway. |
| Volume | Variable: ~20 up to ~50,000 rows. Streaming is mandatory; no full-file load in memory. 500k is **not** a target. |
| Performance | Target p95 ~1 s per row without cache; caches make repeated origins nearly free. |
| Output files | Must open in any spreadsheet app. XLSX is the recommended output (no locale issues); CSV defaults are tuned for es-AR (see 8.1). |
| Stack | Java 21, Maven. |
| Architecture | Hexagonal (Ports and Adapters): core/motor isolated, adapters for CLI/files/maps. |
| Coordinates | `groupId=ar.zon`, `artifactId=zon-calculadora`, base package `ar.zon.calculadora`. |
| Config format | `.properties`. |
| Docker | **Not used anywhere** (neither development nor the client's machine). |
| Deployment | **Self-contained offline bundle**: bundled JRE + app `.jar` + native `osrm-routed` + pre-built `.osrm` data + Photon (jar + index), so text geocoding also works offline. Must run on the client's machine **without Docker, without Java installed, without internet and without admin installation**. One bundle per target OS/CPU architecture (section 17, Annex B). |
| Samples | `samples/` contains HTML mockups only; no canonical CSV/XLSX survey sample yet. |

## 3. PRD / ADR Delta (Must Be Applied During Implementation)

The PRD was written around fuel cost. This plan supersedes those parts for Increment 1:

| Item | Plan decision |
|---|---|
| RF-COM-02 cost, RF-COM-06 price used, RF-COM-07 currency, OBJ/report totals with cost | Replace with emissions (`kgCO2e`), emission factor used, and emissions totals. |
| RF-COM-05 walk/bike/EV empty or zero | Walk/bike: `SKIPPED_NON_MOTORIZED`. EV: out of scope (unknown transport). Public transport handled by configurable factors. |
| RF-DIS-06 round trip | Fixed to one-way. |
| RNF-03/DoD 8 (500k rows) | Variable volume (tens to tens of thousands). Streaming mandatory; synthetic memory smoke tests at 5k and 50k without OOM. 500k is not a gate. |
| Q-06 fuel price | Not applicable. |
| DoD 5 hand-calculated fuel cost | Replace with hand-calculated consumption and emissions cases (using placeholder factors). |
| ADR 0002 "Confirmación" #2 (500k-row load test) | Replaced by the 5k/50k smoke tests. Add a short addendum to ADR 0002. |
| ADR 0002 stage 1 (generate the blank survey template) | Not part of Increment 1. This plan only covers reading responses and writing the report. |
| ADR 0001 (maintain Docker containers or local processes) | This plan uses only local processes shipped in an offline bundle. No Docker. |

Keep PRD IDs in code/tests where useful, and record these deltas in `docs/prd-delta.md` (or a README section).

## 4. Scope

### In scope

- CLI that processes `.csv` or `.xlsx` input and writes `.csv` or `.xlsx` report.
- Dynamic column mapping through `.properties`.
- Geocoding text locations to coordinates (Photon), plus dev modes without map services.
- Distance: route (OSRM) primary, Haversine fallback per row.
- Consumption and emissions with configurable (placeholder) factors.
- Per-row errors, summary (including total survey rows), local processing.
- Motor/core independent from CLI.
- Native (no Docker) setup guide for Photon and OSRM (Annex A).
- Self-contained offline bundle and launcher that starts/stops the bundled services (section 17, Annex B).

### Out of scope

- REST API, desktop/web UI.
- Docker (not used anywhere).
- Fuel price/cost/currency.
- Automatic OSM/factor updates.
- Authentication, DB/history.
- Airplane and electric-vehicle logic.
- Survey template generation (ADR 0002, stage 1).
- Per-person emissions.
- Counting attendees / detecting duplicate answers (done outside the app).

## 5. Architecture (Hexagonal / Ports and Adapters)

### 5.1 Style

The calculation engine is the application core; CLI, file formats, and map services are adapters.

```text
                    +-------------------------+
                    |         CLI Adapter     |
                    | args/progress/exit code |
                    +------------+------------+
                                 | calls
                                 v
+-------------------+   +-------------------------+   +----------------------+
|  Input Adapters   |-->|      Core / Motor       |<--|  Output Adapters     |
| CSV/XLSX Reader   |   |  use cases + domain     |   | CSV/XLSX Report      |
+-------------------+   |  ports (interfaces)     |   +----------------------+
                        +-----+-------------+-----+
                              |             |
                 +------------+--+       +--+------------+
                 | Geocoder Port |       | Distance Port |
                 +-------+-------+       +-------+-------+
                         |                       |
                 +-------+-------+       +-------+-------+
                 | Photon Adapter|       | OSRM Adapter  |
                 +---------------+       +---------------+
```

### 5.2 Dependency rule

- `core` has **no dependency** on `cli`, `infra`, file libs, HTTP clients, JSON libs, or map vendors. (`slf4j-api` is the only allowed third-party import.)
- `core` defines **ports** (Java interfaces) for everything external.
- `infra` implements ports as **adapters** (FastCSV/FastExcel, Photon HTTP, OSRM HTTP, `.properties` parsing).
- `cli` is the composition root: parses args, loads config, instantiates adapters, injects them into the core, runs the use case.
- Dependencies point inward: `cli/infra -> core`. Never `core -> infra/cli`.
- `core.config` holds the immutable config model; **parsing** `.properties` lives in `infra.config`.

### 5.3 Ports (core-owned interfaces) - official names

These names are the reference for code, tests and docs.

| Port | Responsibility | Core package | Adapter(s) |
|---|---|---|---|
| `SurveyRowReader` | Read input rows as a streaming iterator | `core.ingest` | `CsvRowReader`, `XlsxRowReader` |
| `ReportWriter` | Write output rows/summary streaming | `core.report` | `CsvReportWriter`, `XlsxReportWriter` |
| `Geocoder` | Resolve text location to coordinates | `core.geocode` | `PhotonGeocoder`, `CoordinatesGeocoder` (parses `lat,lon`), `FileGeocoder` (lookup file), `FakeGeocoder` (test) |
| `RouteDistanceCalculator` | Route km between coordinates | `core.distance` | `OsrmRouteCalculator`, `FakeRouteCalculator` (test) |
| `StraightLineDistanceCalculator` | Haversine km | `core.distance` | `HaversineCalculator` (pure, lives in core) |
| `EmissionFactorProvider` | Resolve factor by transport/fuel | `core.emissions` | `PropertiesEmissionFactorProvider` |

`ConsumptionCalculator` and `EmissionCalculator` are plain core services, not ports. Haversine has no I/O but keeps its port so fallback orchestration and tests stay uniform. No `Clock`/`IdProvider` ports in Increment 1. Starting/stopping the bundled Photon/OSRM processes is **not** a core concern: it is an infra service (`infra.services.LocalServicesManager`) used only by the CLI composition root.

### 5.4 Core use case

`CalculateSurveyBatchUseCase` (implements `CalculationEngine`):
- resolve the event destination once at start;
- orchestrate the pipeline row by row (order in 9.5);
- call ports, never concrete adapters;
- apply business rules (normalization, skip, fallback, factor audit);
- emit progress through `ProgressListener`;
- return `BatchResult`.

### 5.5 Composition / wiring

- CLI builds `CalculationConfig` (file + CLI overrides).
- CLI selects adapters by extension and config.
- CLI injects adapters into the use case.
- In `services.mode=BUNDLED`, the CLI first starts the bundled Photon/OSRM through `LocalServicesManager`, waits until they are healthy, and stops them when the run ends.
- A future REST/desktop adapter reuses the same core.

## 6. Repository Structure

```text
zon-calculadora/
  pom.xml
  README.md
  PLAN-IMPLEMENTACION.md
  config/
    application.example.properties
    emission-factors.example.properties
  docs/
    prd-delta.md
    maps-setup.md               # content of Annex A
    offline-bundle.md           # content of Annex B
  samples/
    ...                         # existing mockups + synthetic csv/xlsx
  packaging/
    build-bundle.sh / .ps1      # assemble the offline bundle (Annex B)
    launcher/                   # zon-calculadora.sh / .bat templates
  src/
    main/java/ar/zon/calculadora/
      cli/                      # inbound adapter + composition root
      core/
        api/                    # use case interface, request/response, progress
        config/                 # immutable config model (no parsing, no CLI)
        model/                  # domain records/enums
        pipeline/               # use case implementation
        ingest/                 # SurveyRowReader port + mapping
        geocode/                # Geocoder port + selection/cache policy
        distance/               # distance ports + fallback orchestration
        consumption/            # consumption rules
        emissions/              # emission rules + factor port
        report/                 # ReportWriter port + summary model
        errors/                 # error codes/types
        observability/          # ProgressListener
      infra/
        csv/                    # FastCSV adapters
        xlsx/                   # FastExcel adapters
        photon/                 # Photon HTTP adapter
        osrm/                   # OSRM HTTP adapter
        config/                 # .properties file adapter
        services/               # LocalServicesManager (bundled Photon/OSRM processes)
    main/resources/
    test/java/ar/zon/calculadora/
      unit/                     # core rules with fakes, no IO
      integration/              # adapters + wiring (*IT)
    test/resources/fixtures/
  .gitignore                    # includes out/, target/, dist/, and any local map data
```

Map data (hundreds of MB to GB) lives **outside** the repo (see Annexes A and B).

## 7. Maven and Dependencies

### 7.1 `pom.xml` baseline

- Java 21, `maven.compiler.release=21`.
- Main class: `ar.zon.calculadora.cli.Main`.

```xml
<groupId>ar.zon</groupId>
<artifactId>zon-calculadora</artifactId>
<version>0.1.0-SNAPSHOT</version>
<name>ZON Calculadora</name>
```

(The element is `<name>`; the previous draft had an invalid `<n>`.)

### 7.2 Dependencies (minimum)

Pin exact versions (check the latest stable ones at bootstrap; do not use ranges).

| Dependency | Why | Notes |
|---|---|---|
| `org.dhatim:fastexcel` | Write `.xlsx` streaming | ADR 0002. |
| `org.dhatim:fastexcel-reader` | Read `.xlsx` streaming | ADR 0002. |
| `de.siegmar:fastcsv` | Read/write `.csv` streaming | ADR 0002. |
| `com.fasterxml.jackson.core:jackson-databind` | Parse Photon/OSRM JSON | `infra` only. |
| `org.slf4j:slf4j-api` + `slf4j-simple` | Logging | Keep report output clean. |
| `org.junit.jupiter:junit-jupiter` | Tests | Test scope. |
| `org.assertj:assertj-core` | Assertions | Test scope. |

Prefer hand-written fakes for ports; Mockito only if really needed. Avoid Spring, Lombok, heavyweight mapping frameworks, and paid SDKs.

### 7.3 Build plugins

- `maven-compiler-plugin`
- `maven-surefire-plugin` (unit tests, `*Test`, run by `mvn test`)
- `maven-failsafe-plugin` (integration tests, `*IT`, run by `mvn verify`)
- `maven-shade-plugin` for a runnable fat jar. Configure `ServicesResourceTransformer` (merge `META-INF/services`) and **verify `java -jar` works with XLSX input** in Stage 4; the XLSX reader relies on service-loaded components.

Bundle assembly is done by scripts in `packaging/` (Annex B), not by Maven.

Acceptance: `mvn -q -DskipTests package` produces `target/zon-calculadora-*.jar`. Definition of done uses `mvn verify`.

## 8. Configuration Contract (`.properties`)

One main config plus a factors file. Keep keys stable. CLI arguments override config (see 13).

### 8.1 Main config keys

```properties
# ---- Output ----
output.format=auto                 # auto|csv|xlsx (auto = by --output extension)
output.csv.delimiter=;             # ';' opens correctly in Excel es-AR, LibreOffice, Google Sheets
output.csv.decimal=,               # decimal separator written in CSV numbers
output.csv.bom=true                # UTF-8 BOM so accents open correctly in Excel
output.csv.charset=UTF-8

# ---- Input ----
input.csv.delimiter=auto           # auto|,|;|\t
input.csv.charset=auto             # auto|UTF-8|windows-1252
input.csv.decimal=auto             # accepts both ',' and '.' when auto
input.xlsx.sheet=1                 # 1-based index or sheet name
input.headerRow=1

# ---- Column mapping (aliases comma-separated; compared after normalization:
#      trim, lower-case, accents removed, repeated spaces collapsed) ----
columns.personas=personas
columns.origen=origen
columns.destino=destino
columns.transporte=transporte
columns.combustible=combustible
columns.evento=evento

# ---- Event (same for every row of the run) ----
# Destination priority: lat/lon > destination.default (geocoded once at start) > fatal error
destination.default=
destination.lat=
destination.lon=
event.name=

# ---- Row validation ----
personas.min=1
personas.max=5
# personas.max.colectivo=          # optional per-transport override

# ---- Defaults ----
default.fuel.auto=NAFTA

# ---- Geocoding ----
geocoder.mode=PHOTON               # PHOTON|COORDINATES|FILE
geocoder.file=                     # CSV "text;lat;lon", used when mode=FILE
geo.bias.lat=
geo.bias.lon=
geo.bbox=                          # optional minLon,minLat,maxLon,maxLat
geo.maxCandidates=5
geo.cache.enabled=true
geo.cache.maxEntries=10000

# ---- Distance ----
distance.mode=ROUTE                # ROUTE|HAVERSINE|BOTH
distance.fallback=HAVERSINE        # HAVERSINE|NONE (only applies to ROUTE/BOTH)
distance.decimals=2
route.cache.enabled=true           # cache by origin coordinates (destination is constant)
route.cache.maxEntries=10000

# ---- Service endpoints (used in EXTERNAL mode; derived from host/ports in BUNDLED mode) ----
photon.baseUrl=http://localhost:2322
osrm.baseUrl=http://localhost:5000
http.timeoutMs=3000
http.retries=1                     # bounded retry on timeout / 5xx
http.maxConsecutiveFailures=20     # abort batch (fatal) after N consecutive infrastructure failures
healthcheck.enabled=true           # check Photon/OSRM at startup when they are needed

# ---- Local services ----
services.mode=EXTERNAL             # EXTERNAL: connect to Photon/OSRM already running (development)
                                   # BUNDLED: start and stop the bundled ones (offline bundle)
services.host=127.0.0.1
services.osrm.port=5000
services.photon.port=2322
services.startTimeoutSeconds=120
# Paths below are relative to the app home (-Dzon.home; fallback: parent folder of the jar).
# Executable extensions (.exe) are resolved per OS.
services.osrm.executable=./runtime/osrm/osrm-routed
services.osrm.data=./data/osrm/argentina.osrm
services.osrm.algorithm=mld
services.photon.java=./runtime/jre/bin/java
services.photon.jar=./runtime/photon/photon.jar
services.photon.dataDir=./data/photon      # parent folder of photon_data
services.photon.heap=                      # optional, e.g. -Xmx2G (measure first)

# ---- Factors ----
factors.file=./config/emission-factors.properties
```

Notes:
- `distance.mode` is the **only** mode switch. For development without map services use `geocoder.mode=COORDINATES` (or `FILE`) with `distance.mode=HAVERSINE`.
- `BOTH`: the route distance feeds consumption/emissions; the straight-line distance is written to `distance_straight_km` for comparison.
- `distance.decimals` only affects the **written** values. Totals are computed from unrounded values, so a total may differ slightly from the sum of the written rows. Document it.
- `services.mode=BUNDLED` is used by the offline bundle (section 17). In that mode the app starts the services itself and `photon.baseUrl`/`osrm.baseUrl` are ignored.
- With `geocoder.mode=COORDINATES`, `origen` (and the destination) must look like `-31.4201,-64.1888` (`lat,lon`).

### 8.2 Emission/consumption factors

**These values are placeholders for testing only. They are NOT real figures.** The client must replace them once an official source is defined.

Structure (two bases):
- `vehicle_km` (AUTO, MOTO): `liters_or_m3 = km * consumption_per_100km / 100`; `kgCO2e = liters_or_m3 * kgco2e_per_fuel_unit`. Emissions are **per vehicle** (not multiplied by `personas`).
- `passenger_km` (COLECTIVO): `kgCO2e = km * personas * kgco2e_per_passenger_km`. Consumption is not calculated (left empty with note `consumption_not_applicable`).

Key pattern: `factor.<transport>.<fuel>.<attribute>` in lower case.

```properties
# PLACEHOLDER VALUES - NOT REAL. Replace when an official source exists.

factor.auto.nafta.basis=vehicle_km
factor.auto.nafta.fuel_unit=l
factor.auto.nafta.consumption_per_100km=10.0
factor.auto.nafta.kgco2e_per_fuel_unit=2.0
factor.auto.nafta.source=PLACEHOLDER_NOT_REAL

factor.auto.gasoil.basis=vehicle_km
factor.auto.gasoil.fuel_unit=l
factor.auto.gasoil.consumption_per_100km=8.0
factor.auto.gasoil.kgco2e_per_fuel_unit=2.5
factor.auto.gasoil.source=PLACEHOLDER_NOT_REAL

factor.auto.gnc.basis=vehicle_km
factor.auto.gnc.fuel_unit=m3
factor.auto.gnc.consumption_per_100km=10.0
factor.auto.gnc.kgco2e_per_fuel_unit=2.0
factor.auto.gnc.source=PLACEHOLDER_NOT_REAL

factor.moto.nafta.basis=vehicle_km
factor.moto.nafta.fuel_unit=l
factor.moto.nafta.consumption_per_100km=4.0
factor.moto.nafta.kgco2e_per_fuel_unit=2.0
factor.moto.nafta.source=PLACEHOLDER_NOT_REAL

factor.colectivo.gasoil.basis=passenger_km
factor.colectivo.gasoil.kgco2e_per_passenger_km=0.1
factor.colectivo.gasoil.source=PLACEHOLDER_NOT_REAL
```

Rules:
- Round placeholder numbers are intentional: hand-calculated tests stay trivial.
- Every calculated row records factor key, source and `factor_is_placeholder`.
- If **any** row used a factor whose `source` starts with `PLACEHOLDER`, the console summary and the JSON summary show a warning `USING_PLACEHOLDER_FACTORS`, so a report can never be mistaken for real figures.
- Missing factor => row error `FACTOR_NOT_FOUND`, never a silent zero.
- README must say the client replaces the file and sets real `source` values.

## 9. Input Contract and Row Rules

### 9.1 Columns

Required by business logic:
- `personas` - people who traveled in that vehicle/trip (the respondent included).
- `origen` - origin text at neighborhood/locality/city level (or `lat,lon` in COORDINATES mode).
- `transporte` - transport mode.

Optional:
- `destino` - ignored for calculation: the destination is configured once per run (section 8.1). If a row has a non-empty `destino` different from the configured destination, add a data warning in `calculation_notes` and count it in the summary. The configured destination is still used.
- `combustible` - fuel type; inferred when missing.
- `evento` - event name; if its values differ inside a run, emit a warning (one destination per run).

Header mapping, file handling:
- Header matching is done after normalization (trim, lower-case, accents removed, repeated spaces collapsed), on both the config alias and the file header.
- Fully empty rows (typical at the end of an XLSX) are ignored silently and not counted.
- XLSX numeric cells (`2` arrives as `2.0`) and CSV text (`2`, `2,0`) must produce the same integer for `personas`.
- CSV: BOM handled; delimiter and charset auto-detected unless configured.
- XLSX: sheet and header row configurable.

### 9.2 Accepted transport values (normalized)

| Input examples | Canonical |
|---|---|
| auto, coche, automovil | `AUTO` |
| moto, motocicleta | `MOTO` |
| colectivo, omnibus, bondi, bus | `COLECTIVO` |
| caminando, a pie, pie | `PIE` |
| bicicleta, bici | `BICI` |

Anything else (including electric vehicles, airplane, etc.) => `UNKNOWN_TRANSPORT`.

### 9.3 Fuel inference

| Transport | Fuel provided | Result |
|---|---|---|
| `MOTO` | any/missing | `NAFTA` |
| `AUTO` | nafta/gasolina | `NAFTA` |
| `AUTO` | gasoil/diesel | `GASOIL` |
| `AUTO` | gnc/gas | `GNC` |
| `AUTO` | missing | `default.fuel.auto` (`NAFTA`), `fuel_inferred=true` |
| `COLECTIVO` | missing/any | `GASOIL` generalized, `fuel_inferred=true` |
| `PIE/BICI` | any | ignored |

Canonical fuel names: `NAFTA`, `GASOIL`, `GNC` (`diesel` is only an input alias). All inference is visible in report columns.

### 9.4 No-calculation rows

For `PIE` and `BICI`:
- `status=SKIPPED_NON_MOTORIZED`
- `distance_km=0`, `consumption_value=0`, `emissions_kgco2e=0`
- `distance_mode=NOT_APPLICABLE`
- No geocoding or routing.

### 9.5 Row processing order

1. Ignore fully empty rows.
2. Normalize `transporte`: missing => `MISSING_TRANSPORT`; unknown => `UNKNOWN_TRANSPORT`.
3. `PIE`/`BICI` => `SKIPPED_NON_MOTORIZED` and stop (other fields are not validated).
4. Validate `personas`: integer within `personas.min`..`personas.max` (per-transport override if configured); otherwise `INVALID_PEOPLE_COUNT`.
5. Validate `origen` present => else `MISSING_ORIGIN`.
6. Resolve fuel (9.3).
7. Geocode origin (destination was resolved once at startup).
8. Distance: route, with per-row fallback per `distance.*`.
9. Resolve factor, compute consumption and emissions.

## 10. Core Domain Model

```text
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

Statuses:
- `OK` (includes rows computed with `distance.mode=HAVERSINE` chosen on purpose; `distance_mode=HAVERSINE`, `distance_fallback_used=false`)
- `OK_FALLBACK_HAVERSINE` (route failed, straight line used)
- `SKIPPED_NON_MOTORIZED`
- `ERROR`

Row-level error codes (English code, Spanish message in the report):
- `MISSING_ORIGIN`, `MISSING_TRANSPORT`, `UNKNOWN_TRANSPORT`, `INVALID_PEOPLE_COUNT`
- `LOCATION_NOT_FOUND` (origin not found), `GEOCODER_ERROR` (service failure)
- `ROUTE_NOT_FOUND`, `ROUTER_ERROR` (service failure)
- `FACTOR_NOT_FOUND`

Fatal error codes (exit code 2, batch does not start or stops):
- `INVALID_CONFIG`, `MISSING_REQUIRED_COLUMN`, `INPUT_UNREADABLE`, `UNSUPPORTED_FORMAT`
- `OUTPUT_EXISTS`, `OUTPUT_WRITE_ERROR`
- `DESTINATION_NOT_RESOLVED`, `SERVICE_UNAVAILABLE` (health-check failed or too many consecutive failures)
- Bundled mode: `BUNDLE_INCOMPLETE` (a bundled executable/data file is missing), `PORT_IN_USE`, `SERVICE_START_FAILED` (a bundled service was not healthy within `services.startTimeoutSeconds`)

## 11. Public Motor API

```java
public interface CalculationEngine {
  BatchResult run(CalculationRequest request, ProgressListener listener);
}

public record CalculationRequest(
  InputSource input,
  OutputTarget output,
  CalculationConfig config
) {}

public interface ProgressListener {
  void onProgress(ProgressEvent event);
}
```

`InputSource`/`OutputTarget` support file path and stream. `ProgressListener` is optional; the CLI maps it to stderr, tests can ignore it. The engine must not read stdin or write stdout. `BatchResult` contains `summary`, `outputLocation` and counters; rows are written streaming.

## 12. Output Contract

### 12.1 Report columns (fixed order)

```text
row_number
status
error_code
error_message
evento
origen_input
origen_label
origen_lat
origen_lon
destino_input
destino_label
destino_lat
destino_lon
transporte_normalizado
combustible_normalizado
fuel_inferred
personas
distance_km
distance_straight_km
distance_mode
distance_fallback_used
distance_fallback_reason
consumption_value
consumption_unit
emissions_kgco2e
emissions_basis
factor_key
factor_source
factor_is_placeholder
calculation_notes
```

- `emissions_kgco2e` is per vehicle for AUTO/MOTO and per passenger-km attribution for COLECTIVO (`emissions_basis` says which).
- Numbers are written as numeric cells in XLSX and with the configured decimal separator in CSV.
- **CSV formula-injection guard:** text cells coming from the input that start with `=`, `+`, `-`, `@`, tab or CR are prefixed with `'` when written to CSV.
- Output is written to a temporary file and renamed on success. If the output exists, fail with `OUTPUT_EXISTS` unless `--force`.

### 12.2 Summary

Always printed to console; optional `--summary <path>` writes JSON. XLSX output may add a "Summary" sheet if FastExcel streaming allows it cleanly (verify in Stage 4); otherwise JSON/console only.

Contents:
- **total survey rows** (`rows_total`), ok, ok with fallback, skipped, errors;
- vehicles (AUTO+MOTO rows calculated);
- total km, total consumption (by unit), total kgCO2e;
- breakdown by canonical transport (rows, km, kgCO2e);
- warnings: `USING_PLACEHOLDER_FACTORS`, destination/event mismatches (counts).

## 13. CLI Contract

```bash
java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar \
  --input ./samples/input.csv \
  --output ./out/report.xlsx \
  --config ./config/application.properties \
  --destination "Estadio Mario Alberto Kempes, Córdoba" \
  --event "Evento X"
```

| Arg | Required | Description |
|---|---|---|
| `--input` | yes | Input `.csv`/`.xlsx` path |
| `--output` | yes | Output `.csv`/`.xlsx` path |
| `--config` | yes | `.properties` config path |
| `--destination` | no | Event destination: text or `lat,lon`. Overrides config |
| `--event` | no | Event name. Overrides `event.name` |
| `--services` | no | `external` or `bundled`. Overrides `services.mode` |
| `--summary` | no | Write summary JSON to this path |
| `--force` | no | Overwrite the output file if it exists |
| `--log-level` | no | INFO/DEBUG/WARN/ERROR |
| `--help` | no | Print usage |
| `--version` | no | Print version |

The config does not define input/output paths; CLI arguments are the only source.

Exit codes:
- `0`: batch completed with no row errors (skipped rows and fallback rows are fine; they appear in the summary).
- `1`: batch completed with at least one row `ERROR`.
- `2`: fatal error (invalid config, unreadable input, output not writable or already exists, destination not resolved, services unavailable, bundled services failed to start, incomplete bundle).

## 14. Error Handling and Resilience Rules

- Never fail the whole batch for row-level data errors; always include row number and error code.
- Route failure (`ROUTE_NOT_FOUND` or `ROUTER_ERROR`): fall back to Haversine only when `distance.fallback=HAVERSINE`; label clearly. Otherwise row error.
- Missing factor: row error, no fake number.
- **Startup:** resolve the destination once; if it cannot be resolved => `DESTINATION_NOT_RESOLVED`. When `healthcheck.enabled` and services are needed, call Photon and OSRM first; failure => `SERVICE_UNAVAILABLE`, exit 2.
- **During the batch:** HTTP timeout 3 s, 1 bounded retry. After `http.maxConsecutiveFailures` consecutive infrastructure failures, abort with `SERVICE_UNAVAILABLE` (otherwise a dead service would turn a large file into hours of timeouts).
- Caches are bounded (LRU). Geocode cache key: normalized text + bias. Route cache key: origin coordinates (destination is constant).
- Do not log full rows at INFO.
- **Bundled mode:** before starting, verify that executables and data files exist (`BUNDLE_INCOMPLETE`) and that the ports are free (`PORT_IN_USE`). Services listen on `127.0.0.1` only and write their output to `logs/`. They are always stopped when the run ends, fails or is interrupted (shutdown hook); no orphan process may remain (details in B.5).

## 15. Test Plan

### 15.1 TDD rule (mandatory)

1. **Red:** write a failing test for the smallest expected behavior.
2. **Green:** minimum code to pass.
3. **Refactor:** keep tests green.

No production behavior without a test that first failed for it. Bug fixes start with a reproducing test. Tests are deterministic; Photon/OSRM are covered through ports/fakes, with optional smoke tests against real local services (tagged and skipped by default).

### 15.2 Unit tests

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

Hand-calculated cases (placeholder factors; they validate formulas, not real values):

| Case | Calculation | Expected |
|---|---|---|
| AUTO NAFTA, 50 km | 50 × 10 / 100 = 5.0 l; 5.0 × 2.0 | 5.0 l, 10.0 kgCO2e |
| AUTO GNC, 100 km | 100 × 10 / 100 = 10 m3; 10 × 2.0 | 10 m3, 20.0 kgCO2e |
| MOTO NAFTA, 25 km | 25 × 4 / 100 = 1.0 l; 1.0 × 2.0 | 1.0 l, 2.0 kgCO2e |
| COLECTIVO, 40 km, personas 3 | 40 × 3 × 0.1 | 12.0 kgCO2e, no consumption |

### 15.3 Integration tests (`*IT`, `mvn verify`)

- CSV input -> CSV report with fakes for geocoder/router.
- XLSX input -> XLSX report with the same business result as CSV.
- CSV edge cases: BOM, `;` and `,` delimiters, windows-1252, decimal comma, accents.
- XLSX edge cases: numeric `personas`, empty trailing rows, other sheet/header row.
- Route failure fallback per row; fallback disabled => error.
- Bike/walk skipped.
- Destination from config, from `--destination`, missing => fatal.
- Health-check failure => exit 2; consecutive failures => abort with exit 2.
- CSV formula-injection guard; output exists / `--force`; temp file renamed.
- Exit codes 0 / 1 / 2.
- `LocalServicesManager` with a fake child process: starts, becomes healthy, is stopped on success / failure / interrupt; port in use; missing file; start timeout.

### 15.4 Fixture strategy

No real survey file exists. Create synthetic `samples/input-sample.csv` and `.xlsx` based on the mockup contract, with: auto nafta, auto missing fuel, auto gnc, moto, colectivo, bici, pie, `personas` invalid (0, 6, empty), unknown transport (incl. an electric one), unknown place, a row with a different `destino`. Add a small golden expected report for deterministic fields; allow tolerance for tests against real services.

### 15.5 Manual / smoke checks

- Offline: `geocoder.mode=COORDINATES` + `distance.mode=HAVERSINE`.
- With local Photon/OSRM (Annex A): a known Córdoba trip; route km should be at or above the straight-line km; compare with a reference route within the agreed tolerance.
- Offline bundle on a clean machine with the network off (B.6).
- Synthetic files at 5k and 50k rows: no OOM, acceptable runtime, constant memory (streaming).

## 16. Stages

Each stage lists its scope, how to try it, and when it is done. Every stage ends with `mvn verify` green (except Stage 0) and no unrelated refactor. Follow the TDD rule from 15.1 in every stage.

| Stage | Name | Depends on |
|---|---|---|
| 0 | Native map services + target-OS feasibility | - |
| 1 | Project skeleton + pure core | - |
| 2 | End-to-end CSV flow, offline | 1 |
| 3 | Real maps: Photon + OSRM adapters | 0, 2 |
| 4 | XLSX support and CSV/XLSX parity | 2 (independent of 3) |
| 5 | Offline bundle + bundled-services launcher | 0, 3, 4 |
| 6 | Hardening, smoke tests, docs | 5 |

### Stage 0 - Native map services + target-OS feasibility (no Java code)

Goal: Photon (`localhost:2322`) and OSRM (`localhost:5000`) running natively (**no Docker**) with Argentina data, and proof that they can also run on the client's operating system.

Scope:
1. Follow Annex A on the development machine (requirements, Photon, OSRM, verification).
2. **Go/no-go on the target OS:** on the client's OS/architecture (a VM is fine), run `osrm-routed` with the generated `.osrm` files and Photon with a Java 21 runtime, with the network disabled (A.6, check 4). If this fails, stop and decide before writing more code.
3. Record machine, RAM, disk, setup time and per-request latency in `docs/maps-setup.md`.
4. Save one sample Photon response and one OSRM response under `src/test/resources/fixtures/` (used later to write adapter tests).

Done when: the checks in A.6 pass, including the target-OS check, and `docs/maps-setup.md` exists.

### Stage 1 - Project skeleton + pure core

Scope:
1. Maven project, package structure, `.gitignore`, JUnit/AssertJ wired, `mvn test` green on the empty skeleton.
2. `application.example.properties` and `emission-factors.example.properties` (8.1, 8.2).
3. Config model + `.properties` loader + factors loader.
4. Domain model, statuses, error codes.
5. Normalization and validation rules (9.2-9.5).
6. `HaversineCalculator`, `ConsumptionCalculator`, `EmissionCalculator`.
7. Summary aggregation.

Try it: unit tests only.
Done when: all unit tests in 15.2 pass, including the four hand-calculated cases.

### Stage 2 - End-to-end CSV flow, offline

Scope:
1. `CsvRowReader` (FastCSV) with dynamic mapping, delimiter/charset detection, empty-row skipping.
2. `CsvReportWriter` with es-AR defaults, BOM, injection guard, temp file + rename, `--force`.
3. `CoordinatesGeocoder` and `FileGeocoder`; `FakeGeocoder`/`FakeRouteCalculator` for tests.
4. `CalculateSurveyBatchUseCase` with `distance.mode=HAVERSINE`.
5. Minimal CLI: all arguments in 13, progress to stderr, console summary, exit codes.
6. Synthetic `samples/input-sample.csv`.

Try it:
```bash
java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar \
  --input samples/input-sample.csv --output out/report.csv \
  --config config/application.properties --destination "-31.4201,-64.1888"
```
(with `geocoder.mode=COORDINATES`, `distance.mode=HAVERSINE`, and coordinates in `origen`.)

Done when: the sample runs offline, the report opens correctly in a spreadsheet, exit codes behave as in 13.

### Stage 3 - Real maps: Photon + OSRM adapters

Scope:
1. `PhotonGeocoder` (`/api?q=...&limit=...`, bias `lat`/`lon`, optional `bbox`; response is GeoJSON with `[lon, lat]`).
2. Deterministic candidate selection; record chosen label and candidate count.
3. `OsrmRouteCalculator` (`/route/v1/driving/{lon},{lat};{lon},{lat}?overview=false`; distance in meters, `code` != `Ok` means route failure).
4. Fallback orchestration for `ROUTE`/`HAVERSINE`/`BOTH`; statuses and labels.
5. Caches (geocode and route-by-origin), timeouts, retry, health-check, circuit breaker.
6. Adapter tests using the fixtures saved in Stage 0; optional tagged smoke test against the real services (started manually, `services.mode=EXTERNAL`).

Try it: run with `geocoder.mode=PHOTON`, `distance.mode=ROUTE` on a few Córdoba localities.
Done when: route km are plausible against a reference (±10% suggested), a stopped OSRM triggers fallback per row, a stopped service at startup gives exit 2.

### Stage 4 - XLSX support and parity

Scope:
1. `XlsxRowReader` (fastexcel-reader): sheet/header row config, numeric cells, empty rows.
2. `XlsxReportWriter` (fastexcel): numeric cells, header formatting, optional "Summary" sheet if streaming allows.
3. Auto-select adapters by extension.
4. Parity test: same data as CSV and XLSX gives the same business results (ADR 0002).
5. Verify the shaded fat jar reads and writes XLSX when run with `java -jar`.

Done when: parity test green and `java -jar` works with `.xlsx` in and out.

### Stage 5 - Offline bundle + bundled-services launcher

Goal: one folder (per OS/architecture) that the client unzips and runs, with no Docker, no Java installed, no admin rights and no internet (section 17, Annex B).

Scope:
1. `infra.services.LocalServicesManager` + `services.*` config keys + `--services` flag, built with TDD around a fake child process: starts, waits for health, stops on success / failure / interrupt, port in use, missing file, start timeout.
2. Resolve all relative paths against the app home (`-Dzon.home`, fallback: parent folder of the jar).
3. Launcher scripts (`zon-calculadora.sh` / `zon-calculadora.bat`) that use the bundled JRE.
4. `packaging/build-bundle` script that assembles the layout in B.1 for one OS/architecture.
5. Clean-machine, network-off test (B.6).

Try it: unzip the bundle on a clean machine of the target OS, disconnect the network, run the launcher with the sample CSV and XLSX.
Done when: both samples produce the expected reports in `ROUTE` mode, no orphan processes remain after the run or after Ctrl+C, and the ports are free afterwards.

### Stage 6 - Hardening, smoke tests and docs

Scope:
1. Synthetic 5k and 50k runs (fakes) without OOM; note runtime and memory. Measure bundle startup time and RAM with real services.
2. Final review of exit codes, error messages (Spanish) and progress output.
3. README (setup, config, CLI, limitations, OSM attribution), `docs/prd-delta.md`, `docs/maps-setup.md`, `docs/offline-bundle.md`, ADR addenda (0002: load test; 0001: no Docker, local processes only).
4. Walk through the Definition of Done (section 20).

Done when: the DoD checklist is fully ticked.

## 17. Self-contained Offline Bundle

The client's machine must need **nothing installed**: no Docker, no Java, no internet, no admin rights. Everything ships in one folder, built once per target OS and CPU architecture (a Windows x64 bundle does not run on Linux and vice versa).

### 17.1 Layout

```text
zon-calculadora-<version>-<os>-<arch>/
  zon-calculadora.sh | zon-calculadora.bat   # launcher
  app/
    zon-calculadora.jar                      # shaded fat jar
  runtime/
    jre/                                     # Java 21 runtime (runs the app AND Photon)
    osrm/                                    # osrm-routed (+ its runtime libraries)
    photon/
      photon.jar
  data/
    osrm/argentina.osrm*                     # pre-processed routing data (MLD)
    photon/photon_data/                      # Photon search index
    VERSION.txt                              # map date, OSRM version, Photon version
  config/
    application.properties                   # services.mode=BUNDLED
    emission-factors.properties
  logs/                                      # created at runtime (osrm.log, photon.log)
  licenses/                                  # JRE, OSRM, Photon, OSM attribution
  docs/
```

### 17.2 Runtime flow

1. The user runs the launcher with `--input`, `--output` and (optionally) `--destination`.
2. The launcher sets the app home and starts the bundled JRE with the jar.
3. With `services.mode=BUNDLED`, the app checks the executables/data exist and the ports are free, starts `osrm-routed` and Photon bound to `127.0.0.1`, and waits until both answer.
4. The batch runs (streaming).
5. The app stops both services (also on failure or Ctrl+C), prints the summary and returns the exit code.

Starting both services takes time (measure it; record in `docs/offline-bundle.md`) and dominates the runtime of small files. Without map services the app still works with `geocoder.mode=COORDINATES|FILE` and `distance.mode=HAVERSINE`.

### 17.3 Design rules

- The core knows nothing about processes; `LocalServicesManager` is an infra service used only by the CLI.
- In `BUNDLED` mode the endpoints are derived from `services.host` and the configured ports; `photon.baseUrl` / `osrm.baseUrl` are used only in `EXTERNAL` mode (development, Stage 3 smoke tests).
- Everything is local and offline; nothing is downloaded at runtime.
- Details to build and verify the bundle: Annex B.

## 18. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| No real survey sample | Wrong column assumptions | Configurable mapping and aliases; synthetic fixtures; validate with a client sample ASAP. |
| Photon/OSRM setup friction (first time, big downloads) | Blocks end-to-end tests | Stage 0 first and in parallel; Annex A; offline mode so Stages 1-2 never wait. |
| OSRM/Photon native on the client's OS | Bundle does not run there | Confirm target OS/arch early; Stage 0 proves it on that OS; clean offline machine test in Stage 5. |
| Missing runtime libraries in the bundle | Works only on the dev machine | Ship every library; clean-machine, network-off test (B.6). |
| Bundle size, startup time, RAM | Heavy deliverable; slow start for small files | Measure and record; lookup-file geocoder option (B.10); document client requirements. |
| Geocoding ambiguity (locality names repeat) | Wrong km/emissions | Location bias and optional `bbox`; deterministic selection; record chosen label. |
| Locality-level origins | Distances are approximate (centroid of the place) | Document the limitation in README. |
| Placeholder emission factors | Numbers mistaken for real | `PLACEHOLDER_NOT_REAL` source, `factor_is_placeholder` column, summary warning. |
| Duplicate answers (several people from one vehicle) | Inflated emissions | Handled outside the app (attendance counting); the summary gives the total number of surveys to compare against. |
| Argentina-only map data | Trips from abroad get no route | Per-row fallback to Haversine, labeled. |
| One OSRM profile | Same road network for car/moto/bus | Documented; separate profiles are a later extension. |
| Service outage mid-run | Hours of timeouts | Health-check, short timeout, circuit breaker. |
| Large files/memory | Crash/slow | Streaming, no full-list accumulation, 5k/50k smoke tests. |

## 19. Open Items

1. Provide a real anonymized CSV/XLSX sample; update mapping defaults.
2. Confirm canonical transport labels used by the client (`auto/moto/colectivo/bici/pie` or others).
3. Confirm COLECTIVO attribution: `km × personas × factor_per_passenger_km` (current default).
4. Confirm default auto fuel when missing (`NAFTA` assumed).
5. Provide an official source for emission and consumption factors (placeholders until then).
6. Confirm tolerance for route distance validation (suggest ±10%).
7. Confirm the client's **operating system and CPU architecture** (for example Windows x64 or Linux x64). One bundle is built per OS/arch.
8. Confirm the minimum hardware of the client's machine (RAM, free disk); measured values go into `docs/offline-bundle.md`.
9. Decide the bundled geocoder: Photon (any free text) or a `FILE` lookup of known localities (smaller and simpler, less flexible; B.10).
10. Decide the source of `osrm-routed`: build from source (full control) or a prebuilt self-contained build from a third party (faster; verify checksum and license).

Confirmed and closed: fixed destination per run, row = respondent = vehicle, personas range 1-5 configurable, EV out, no cost, no 500k target, no Docker, attendance counting outside the app.

## 20. Definition of Done

- [ ] Hexagonal architecture enforced: core has no CLI/HTTP/file-vendor dependencies; adapters implement ports.
- [ ] CLI processes CSV and XLSX with the same business result.
- [ ] Route mode works; per-row route failure falls back to Haversine and is labeled.
- [ ] Bike/walk rows are skipped, no fake emissions; EV/unknown transports give `UNKNOWN_TRANSPORT`.
- [ ] `personas` outside the configured range (default 1-5) is a row error.
- [ ] Consumption and emissions match the hand-calculated cases.
- [ ] Placeholder factors are flagged in rows and in the summary.
- [ ] Row errors do not stop the batch; report includes the cause. Fatal errors give exit code 2.
- [ ] Summary shows total survey rows, ok, fallback, errors, skipped, vehicles, km, consumption, kgCO2e and breakdown by transport.
- [ ] A dead Photon/OSRM does not hang the run (health-check + circuit breaker).
- [ ] CSV output opens correctly in Excel (es-AR), LibreOffice and Google Sheets; XLSX output has numeric cells.
- [ ] Synthetic 5k and 50k runs complete without OOM.
- [ ] Config externalized; no secrets; local processing only.
- [ ] `mvn verify` green; fat jar runs with `java -jar`.
- [ ] The offline bundle runs on a clean machine of the target OS with **no Java, no Docker, no admin rights and no network**, and leaves no orphan processes.
- [ ] README, `docs/prd-delta.md`, `docs/maps-setup.md`, `docs/offline-bundle.md` written; OSM attribution included.

---

# Annex A - Native map services on the development machine (becomes `docs/maps-setup.md`)

**No Docker anywhere.** This annex is for the machine where you build and test (it needs internet for the downloads). The same pieces are later copied into the offline bundle (Annex B).

## A.0 What you are installing

- **Photon**: a geocoder. Turns text ("Alta Gracia") into coordinates. A Java program plus a downloaded search index.
- **OSRM**: a routing engine. Turns two coordinates into road distance. Native programs (`osrm-extract`, `osrm-partition`, `osrm-customize`, `osrm-routed`) plus an OpenStreetMap extract that you pre-process once into `.osrm` files.

Both run as small local web servers (`localhost:2322` and `localhost:5000`). Downloads and preparation are one-time; afterwards everything works offline. Data comes from OpenStreetMap (© OpenStreetMap contributors, ODbL).

## A.1 Requirements

- Linux, macOS or Windows. Use the **same OS and CPU architecture as the client's machine** whenever you can: the OSRM program and its libraries are OS-specific. (Exploring inside WSL2 is fine, but a Windows client still needs a native Windows `osrm-routed`; validate it with Stage 0, step 2.)
- RAM: several GB free (OSRM pre-processing is the hungriest step). Record what you actually needed.
- Disk: leave at least 2-3 times the size of each download free. Check real sizes on the download pages first.
- Tools: a Java 21 runtime, `wget` or `curl`, `bzip2` (optionally `pbzip2`), `tar`. **Do not unpack the Photon archive with WinRAR.**
- Use an OpenJDK 21 distribution (for example Eclipse Temurin). The same runtime will be bundled in Annex B, so test with it from the start.

## A.2 Folder layout (outside the repo)

```text
~/zon-maps/
  photon/      # photon-<version>.jar and photon_data/
  osrm/        # argentina-latest.osm.pbf and the generated argentina.osrm* files
  tools/       # osrm binaries (see A.4)
```

```bash
mkdir -p ~/zon-maps/photon ~/zon-maps/osrm ~/zon-maps/tools
```

## A.3 Photon (geocoder)

1. **Get the program.** Download the latest `photon-<version>.jar` from https://github.com/komoot/photon/releases/latest into `~/zon-maps/photon`. Note the version (1.x).
2. **Get the Argentina search index.** Open https://download1.graphhopper.com/public/ , find the South America section and the Argentina country dump, and copy the link of the `photon-db-...-latest.tar.bz2` that matches your Photon version (1.x). The index version must match the jar version. Do not guess the URL: copy it from the page.
3. **Download and unpack in one step** (inside `~/zon-maps/photon`):
   ```bash
   cd ~/zon-maps/photon
   wget -O - "<PASTE_THE_LINK_HERE>" | bzip2 -cd | tar x
   # faster alternative: replace bzip2 with pbzip2
   ```
   This creates a `photon_data/` directory in the current folder.
4. **Start it** from the folder that *contains* `photon_data/`:
   ```bash
   java -jar photon-*.jar serve
   ```
   It listens on `http://localhost:2322`. Run `java -jar photon-*.jar -h` to see options (for example `-data-dir`, which points to the *parent* of `photon_data`, and the listen address/port options of your version). If it runs out of memory, add a heap option such as `java -Xmx4G -jar ...`.
5. **Test it** (second terminal):
   ```bash
   curl "http://localhost:2322/api?q=Alta%20Gracia&limit=3"
   curl "http://localhost:2322/api?q=Villa%20Carlos%20Paz&lat=-31.42&lon=-64.18&limit=5"
   ```
   You should get GeoJSON. In each feature, `geometry.coordinates` is `[longitude, latitude]`.
6. **Updating later:** unpack the new index into a *new* directory and swap the directories. Never unpack over the old `photon_data`.

Notes: Photon has no "country" parameter; the Argentina index only contains Argentine places, so none is needed. It supports location bias (`lat`/`lon`), `limit`, `lang`, bounding box and layer filters; check the Photon README of your version for exact parameters.

## A.4 OSRM (router), native

### A.4.1 Download the map (once)

Geofabrik asks not to download repeatedly.
```bash
cd ~/zon-maps/osrm
wget https://download.geofabrik.de/south-america/argentina-latest.osm.pbf
```
On a flaky connection, download the dated file instead of `-latest` (it can be resumed; `wget -c`). Check the size on https://download.geofabrik.de/south-america/argentina.html.

### A.4.2 Get the OSRM programs (pick one path)

You need `osrm-extract`, `osrm-partition`, `osrm-customize` and `osrm-routed`, **all from the same OSRM version**, plus the `car.lua` profile (it lives in the OSRM source tree under `profiles/`).

**Path 1 - Build from source (full control, recommended for the delivered bundle).**
Linux (Ubuntu example; the exact dependency list is in OSRM's build guide for the tag you check out and may change; if `cmake` reports a missing library, install it and retry):
```bash
sudo apt update
sudo apt install -y build-essential git cmake pkg-config libbz2-dev libxml2-dev libzip-dev libboost-all-dev lua5.2 liblua5.2-dev libtbb-dev
cd ~/zon-maps
git clone https://github.com/Project-OSRM/osrm-backend.git
cd osrm-backend
git checkout <LATEST_RELEASE_TAG>       # see the Releases page
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . -j"$(nproc)"
```
After building, the programs are in `build/` (you can also `sudo cmake --install .`, but it is not required: the bundle only needs the binaries and their libraries). On **Windows** (or macOS) follow the build guide in the OSRM wiki for that OS; the Windows build is the most laborious path, so budget time for it or use Path 2.

**Path 2 - Prebuilt programs (faster, especially on Windows).**
- The official OSRM releases page may list binaries for some platforms; check what it offers for your OS and version.
- Third-party self-contained builds exist for Linux, macOS and Windows that already bundle the runtime libraries (for example the `e-kotov/osrm-binaries` releases on GitHub, used by the `osrm.backend` R package). I have not verified asset names or recent versions: check the releases page, pin one exact version, verify the published checksum, review the license, and use the **same version for pre-processing and for the bundle**.

Whichever path you choose, copy the programs into `~/zon-maps/tools/` and note the OSRM version in `docs/maps-setup.md`.

### A.4.3 Pre-process the map (once; minutes to about an hour)

```bash
cd ~/zon-maps/osrm
~/zon-maps/tools/osrm-extract -p <PATH_TO>/profiles/car.lua argentina-latest.osm.pbf
~/zon-maps/tools/osrm-partition argentina-latest.osrm
~/zon-maps/tools/osrm-customize argentina-latest.osrm
```
Rename the result base name if you want (`argentina.osrm*`), but keep all generated `argentina-latest.osrm.*` files of the set together and consistent. The `.osm.pbf` is not needed at runtime.

### A.4.4 Start and test

```bash
~/zon-maps/tools/osrm-routed --algorithm mld --ip 127.0.0.1 --port 5000 argentina-latest.osrm
```
In another terminal (coordinates are **longitude,latitude**; Córdoba city to Villa Carlos Paz):
```bash
curl "http://localhost:5000/route/v1/driving/-64.1888,-31.4201;-64.4978,-31.4241?overview=false"
```
Expect `"code":"Ok"` and `routes[0].distance` in **meters** (a few tens of km).

Facts to keep in mind:
- OSRM uses one profile per data set. We use the car profile for AUTO, MOTO and COLECTIVO.
- Always use `partition` + `customize` + `--algorithm mld`; do not mix algorithms in the same data folder.
- The data files must be produced by the **same OSRM version** that serves them.

## A.5 Day-to-day use (development)

- Two terminals: Photon (`java -jar photon-*.jar serve` from `~/zon-maps/photon`) and OSRM (`osrm-routed ...`). Run the calculator with `services.mode=EXTERNAL`.
- Stop each with `Ctrl+C`.
- In the delivered bundle none of this is done by hand: the app starts and stops both (Annex B).
- To work without them use `geocoder.mode=COORDINATES` and `distance.mode=HAVERSINE`.

## A.6 Verification checklist (Stage 0 is done when all four pass)

1. `curl "http://localhost:2322/api?q=Alta%20Gracia&limit=3"` returns features with coordinates inside Córdoba province.
2. `curl "http://localhost:5000/route/v1/driving/-64.1888,-31.4201;-64.4978,-31.4241?overview=false"` returns `"code":"Ok"` with a plausible distance.
3. Latency is acceptable. Measure with `curl -s -o /dev/null -w "%{time_total}\n" "<url>"` and record it in `docs/maps-setup.md`.
4. **Target-OS check:** on a machine (or VM) with the client's OS/architecture, **with the network disabled and no Java or Docker installed**, `osrm-routed` serves the `.osrm` files and Photon runs with the runtime copied from your build (A.1), and checks 1 and 2 pass there.

## A.7 Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Photon fails with a Java version error | Java older than 21 | Use a Java 21 runtime; check `java -version`. |
| Photon starts but returns no features | `photon_data/` not in the folder you run from, or index/jar version mismatch | Run from the parent of `photon_data/` or use `-data-dir`; use the index that matches the jar version. |
| Photon data looks corrupted after an update | Unpacked over the old data | Delete it and unpack into a new directory. |
| Unpacking fails on Windows | Used WinRAR or a similar tool | Unpack with `bzip2`/`pbzip2` + `tar` (for example inside WSL2). |
| `osrm-extract` is killed / out of memory | Not enough RAM | Close other programs, add swap or use a machine with more RAM; record the requirement. |
| `osrm-routed` refuses to start or says the data is incompatible | Data produced by a different OSRM version | Re-run extract/partition/customize with the same version that serves them. |
| `osrm-routed` does not start on the target machine (missing library / DLL) | Runtime libraries not shipped | List dependencies (`ldd` on Linux, a dependency viewer on Windows) and copy the missing ones next to the program; or use a self-contained build. |
| OSRM returns `NoRoute` or `NoSegment` | Point outside the map (e.g. abroad), not near a road, or lat/lon swapped | Check `lon,lat` order and that points are inside Argentina. The calculator then uses the labeled Haversine fallback. |
| Port already in use (2322 / 5000) | Another process, or a previous instance still running | Stop it or change the port (`--port` in `osrm-routed`; see Photon `-h`) and the config. |
| `cmake` fails | Missing dependency or wrong tag | Install the library named in the error; check the build guide of the tag. |
| Download keeps failing | Unstable connection | Use a dated Geofabrik file and `wget -c`. |

## A.8 Limits and maintenance

- Map data ages. Updating is manual: re-download, re-process (OSRM) or swap the index (Photon), then rebuild the bundle (B.9).
- The Argentina-only extract cannot route trips that start abroad; those rows use the labeled Haversine fallback.
- Locality-level origins resolve to one point per place, so distances are approximate by nature.
- Attribution: "Map data © OpenStreetMap contributors (ODbL)" in README and delivered docs.

## A.9 Matching config keys (EXTERNAL mode)

```properties
services.mode=EXTERNAL
photon.baseUrl=http://localhost:2322
osrm.baseUrl=http://localhost:5000
geocoder.mode=PHOTON
distance.mode=ROUTE
```

---

# Annex B - Self-contained offline bundle (becomes `docs/offline-bundle.md`)

## B.0 Goal

A folder the client unzips and runs on a machine with **no Docker, no Java, no internet and no admin rights**. It contains the bundled JRE, the app jar, native `osrm-routed` with its `.osrm` data, and Photon (jar + index) running on the same JRE. One bundle per OS and CPU architecture. Built on a machine with internet (the build machine), used on machines without it.

## B.1 Layout

See section 17.1. Keep all paths relative to the bundle root.

## B.2 Components and where they come from

| Component | Source | Notes |
|---|---|---|
| Java 21 runtime (`runtime/jre/`) | An OpenJDK 21 distribution (for example Eclipse Temurin) for the **target** OS/arch | Use a full runtime (JRE or a JDK folder), **not** a trimmed `jlink` image: Photon embeds a search engine and may need modules a trimmed image lacks. Trimming can be tried later as a size optimization, only with a full test. Include its license. |
| App (`app/zon-calculadora.jar`) | `mvn package` (shaded) | Same jar for all OS. |
| `osrm-routed` (`runtime/osrm/`) | A.4.2 for the target OS/arch | Copy the program **and every runtime library it needs** (check with `ldd` on Linux or a dependency viewer on Windows). Self-contained builds help. |
| Routing data (`data/osrm/`) | A.4.3 | All `.osrm.*` files of the set; **same OSRM version** as `osrm-routed`. Not the `.osm.pbf`. |
| Photon (`runtime/photon/photon.jar`) | A.3 | Version must match the index. |
| Search index (`data/photon/photon_data/`) | A.3 | Run with `-data-dir` = `data/photon`. |
| `data/VERSION.txt` | Written by the build script | Map download date, OSRM version, Photon version, bundle version. |

## B.3 Build script (`packaging/build-bundle`)

Inputs: target OS/arch, paths to the JRE, `osrm-routed` (+ libs), the `.osrm` data, the Photon jar and index, and the shaded jar. Steps:
1. Create the folder layout (B.1 / 17.1).
2. Copy each component; copy the launcher for that OS and the bundle configuration (`services.mode=BUNDLED`).
3. Write `data/VERSION.txt` and copy `licenses/`.
4. Make the launcher and binaries executable (Linux/macOS).
5. Produce a `.zip` (Windows) or `.tar.gz` (Linux/macOS).
6. Print the total size and the checksum of the archive.

A Windows bundle is assembled on a Windows build machine (PowerShell script) and a Linux bundle on Linux (shell script).

## B.4 Launcher scripts

Linux/macOS `zon-calculadora.sh`:
```bash
#!/usr/bin/env bash
ZON_HOME="$(cd "$(dirname "$0")" && pwd)"
exec "$ZON_HOME/runtime/jre/bin/java" -Dzon.home="$ZON_HOME" -jar "$ZON_HOME/app/zon-calculadora.jar" "$@"
```

Windows `zon-calculadora.bat`:
```bat
@echo off
set "ZON_HOME=%~dp0"
"%ZON_HOME%runtime\jre\bin\java.exe" -Dzon.home="%ZON_HOME:~0,-1%" -jar "%ZON_HOME%app\zon-calculadora.jar" %*
exit /b %ERRORLEVEL%
```
(`%ZON_HOME:~0,-1%` drops the trailing backslash, which would otherwise break the quoting.) If `--config` is not given, the app uses `<zon.home>/config/application.properties`. Paths that contain spaces or accents must work: test them.

## B.5 Process management rules (`LocalServicesManager`)

- Start `osrm-routed --algorithm mld` and Photon with the **bundled** JRE, both bound to `127.0.0.1` (private to the machine; also avoids firewall prompts). Use the Photon listen options of the bundled version (`-h`).
- Before starting: executables and data exist (`BUNDLE_INCOMPLETE`); ports are free (`PORT_IN_USE`).
- Redirect each service's output to `logs/osrm.log` and `logs/photon.log`.
- Wait for readiness by polling a lightweight request (for example a Photon search with `limit=1`, an OSRM `nearest` or `route` request) until `services.startTimeoutSeconds`; on timeout stop everything and fail with `SERVICE_START_FAILED`.
- Always stop both services: normal end, row-level failures, fatal errors, Ctrl+C (shutdown hook). Stop the whole process tree (`ProcessHandle` descendants), with a forced kill after a short grace period.
- Never leave a service running after the app exits.

## B.6 Clean-machine, offline acceptance test

On a machine (or VM) of the target OS **with no Java, no Docker and the network disabled**:
1. Unzip the bundle into a path that contains spaces and an accent.
2. Run the launcher with the sample CSV and the sample XLSX in `ROUTE` mode; compare against the expected reports.
3. Confirm Photon and OSRM were started by the app (see `logs/`).
4. Press Ctrl+C mid-run: confirm no `osrm-routed` or Photon process remains (Task Manager / `ps`) and the ports are free.
5. Run again right away: it must work (no stale processes or locks).
6. Run as a normal (non-admin) user.
7. Try a run with the data folder removed: expect a clear `BUNDLE_INCOMPLETE` message and exit code 2.

## B.7 Measurements to record in `docs/offline-bundle.md`

Bundle size (zip and unpacked), time from launch to both services ready, RAM used at runtime, disk needed, per-row latency, and the **minimum machine requirements** for the client.

## B.8 Licenses and attribution

Ship a `licenses/` folder with the licenses of the runtime, OSRM and Photon, and the line "Map data © OpenStreetMap contributors (ODbL)". Review the license of every third-party binary you redistribute.

## B.9 Updating map data

On the build machine: re-download the extract and the Photon index, re-run A.4.3, rebuild the bundle (B.3) and re-run B.6. Do not mix data from different OSRM or Photon versions with other binaries.

## B.10 Lighter alternative: no Photon

If bundle size, startup time or RAM are a problem and the origins are a known set of Córdoba localities, bundle a lookup file (`geocoder.mode=FILE`, columns `text;lat;lon`) instead of Photon. The bundle shrinks and becomes fully deterministic, but any place not in the file gives `LOCATION_NOT_FOUND`. Decide with the client (open item 9).
