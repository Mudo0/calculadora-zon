# Implementation Plan - ZON Calculadora (Increment 1)

> **Revision v6.** Stages 0-6 can be implemented one at a time. Target deployment is **our own server**, supporting **Windows and Ubuntu**. Docker is an option, not a requirement. Adds native maps setup (Annex A), Dockerized deployment option (Annex C), and an optional self-contained offline bundle (Annex B). The motor/core is decoupled from OSRM runtime (Docker/native/compiled) through ports. Attendance counting is outside the app.

## 1. Purpose

This document is the execution plan for building Increment 1 of the survey distance/consumption/emissions calculator described in `PRD â€“ Calculadora de ITEC.md`. It defines what to build, in which order, with which contracts, defaults, and checks.

It is **not** the LLD or ADR. The work is split in **stages**; each stage leaves something runnable and testable, so it can be implemented on its own (see section 16). Stages 0 and 1 are independent and can be done in parallel.

## 2. Confirmed Inputs

| Topic | Confirmed decision |
|---|---|
| Client | ITEC is the client. Do not use `itec` in artifact/package identifiers. |
| Geography | Primarily CÃ³rdoba, Argentina; design must work for any region. |
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
| Geocoder strategy | `PHOTON` default (text -> coordinates via Photon service). Alternatives: `FILE` (CSV `text;lat;lon`, deterministic/restricted sets) and `COORDINATES` (input already `lat,lon`, dev/test). |
| Docker | **Optional deployment option**. The app and map services (OSRM + Photon) can run on our own server (Windows or Ubuntu), with or without Docker; see Annex C. Docker vs native/compiled OSRM is an operational choice behind the same HTTP contracts. |
| Deployment | **Primary target: our own server, Windows + Ubuntu**. The calculator JAR and map services (OSRM + Photon) can be deployed as native processes or with Docker Compose (Annex C). Same app artifact/config contract; OS-specific scripts/runtime packaging where needed. **Optional secondary packaging:** self-contained offline bundle for restricted/offline machines, Docker-free (Annex B). |
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
| ADR 0002 "ConfirmaciÃ³n" #2 (500k-row load test) | Replaced by the 5k/50k smoke tests. Add a short addendum to ADR 0002. |
| ADR 0002 stage 1 (generate the blank survey template) | Not part of Increment 1. This plan only covers reading responses and writing the report. |
| ADR 0001 (maintain Docker containers or local processes) | Server deployment may use Docker or native processes; Docker is optional. Annex C covers server/Docker deployment; Annex B covers optional Docker-free offline packaging. ADR 0001 should record both modes. |

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
- Dockerized deployment option for app + OSRM/Photon on our own server, Windows + Ubuntu (Annex C).
- Optional self-contained offline bundle and launcher that starts/stops bundled services (section 17, Annex B).
- Project bootstrap artifacts: `.gitignore`, example config, synthetic samples, docs skeleton.

### Out of scope

- REST API, desktop/web UI.
- Docker as a mandatory requirement (Docker is supported as an optional deployment path; native process deployment must remain viable).
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

`ConsumptionCalculator` and `EmissionCalculator` are plain core services, not ports. Haversine has no I/O but keeps its port so fallback orchestration and tests stay uniform. No `Clock`/`IdProvider` ports in Increment 1. Starting/stopping bundled Photon/OSRM processes is **not** a core concern: it is an infra service (`infra.services.LocalServicesManager`) used only by the CLI composition root. The core/motor never depends on Docker, compiled binaries, or process lifecycle; it only depends on ports. OSRM/Photon runtime mode (Docker, native, compiled) is a deployment/infra decision behind the same HTTP adapter contracts (`osrm.baseUrl`, `photon.baseUrl`, `services.mode=EXTERNAL`).

### 5.4 Core use case

`CalculateSurveyBatchUseCase` (implements `CalculationEngine`):
- resolve the event destination once at start;
- orchestrate the pipeline row by row (order in 9.5);
- call ports, never concrete adapters;
- apply business rules (normalization, skip, fallback, factor audit);
- emit progress through `ProgressListener`;
- return `BatchResult`.

### 5.5 Geocoder strategy

Geocoder = converts an origin text (e.g. `"Alta Gracia, Córdoba"`) to `lat,lon`.

| Mode | How it works | Pros | Contras | Default/use |
|---|---|---|---|---|
| `PHOTON` | HTTP query to Photon (`/api`) using OSM index | Flexible, handles free text/locality names | Needs Photon service + index | **Default for own-server deployment** |
| `FILE` | Local CSV lookup `text;lat;lon` | Deterministic, lightweight, no geocoding service | Only resolves preloaded places | Restricted/offline/bundle contingency |
| `COORDINATES` | Input already provides `lat,lon` | No geocoding service, simplest | Not for free-text surveys | Dev/test or pre-geocoded data |

The core depends on the `Geocoder` port only; Photon/FILE/COORDINATES are adapters selected by config.

### 5.6 Composition / wiring

- CLI builds `CalculationConfig` (file + CLI overrides).
- CLI selects adapters by extension and config.
- CLI injects adapters into the use case.
- In `services.mode=BUNDLED`, the CLI first starts the bundled Photon/OSRM through `LocalServicesManager`, waits until they are healthy, and stops them when the run ends.
- Core never instantiates infra classes.

### 5.7 Allowed dependencies (guardrail)

| Layer | May depend on | Must not depend on |
|---|---|---|
| `core.*` | Java stdlib, core interfaces/types | CLI libs, FastCSV/FastExcel, HTTP client impl, OSRM/Photon JSON, filesystem/process |
| `infra.*` | core ports + external libs | other infra adapters unnecessarily |
| `cli.*` | core API + infra adapters + config parsing | business rules duplicated from core |

Enforce with package conventions and simple architecture tests (ArchUnit/JUnit) in Stage 1 or 6.

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
    deployment.md               # server/hosted deployment decisions and runbook
    docker-maps.md              # content of Annex C / Docker deployment option
    osrm-runtime-benchmark.md   # Docker vs native/compiled OSRM comparison (Stage 0 spike)
  deploy/
    docker/
      docker-compose.yml        # optional: app + OSRM + Photon for server deploy
      docker-compose.maps.yml   # optional: OSRM + Photon only
      .env.example              # ports, data paths, image versions, app config
      Dockerfile.app            # optional: calculator runtime image
      osrm/
        Dockerfile.preprocess   # optional: reproducible OSRM preprocess image
      photon/
        Dockerfile              # optional: pinned Photon runtime image
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
  .gitignore                    # Java/Maven/IDE/OS/output/local config/map data
  .gitattributes                # recommended: stable line endings for scripts/properties
  .editorconfig                 # recommended: UTF-8, LF/CRLF policy, Java formatting basics
```

Map data (hundreds of MB to GB) lives **outside** the repo (see Annexes A, B and C). Keep `deploy/docker/.env.maps` local-only; track `.env.maps.example`.

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
- `services.mode=EXTERNAL` covers both native local services and Dockerized services (Annex C). `photon.baseUrl`/`osrm.baseUrl` must point to the reachable endpoints.
- `services.mode=BUNDLED` is used by the optional offline bundle (section 17), when selected. In that mode the app starts the bundled services itself and `photon.baseUrl`/`osrm.baseUrl` are ignored.
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
  --destination "Estadio Mario Alberto Kempes, CÃ³rdoba" \
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
| AUTO NAFTA, 50 km | 50 Ã— 10 / 100 = 5.0 l; 5.0 Ã— 2.0 | 5.0 l, 10.0 kgCO2e |
| AUTO GNC, 100 km | 100 Ã— 10 / 100 = 10 m3; 10 Ã— 2.0 | 10 m3, 20.0 kgCO2e |
| MOTO NAFTA, 25 km | 25 Ã— 4 / 100 = 1.0 l; 1.0 Ã— 2.0 | 1.0 l, 2.0 kgCO2e |
| COLECTIVO, 40 km, personas 3 | 40 Ã— 3 Ã— 0.1 | 12.0 kgCO2e, no consumption |

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
- With local Photon/OSRM (Annex A or Annex C): a known CÃ³rdoba trip; route km should be at or above the straight-line km; compare with a reference route within the agreed tolerance.
- Docker deployment smoke check (optional, Annex C): `docker compose -f deploy/docker/docker-compose.maps.yml up -d` brings services healthy; the app runs with `services.mode=EXTERNAL`.
- Offline bundle on a clean machine with the network off (B.6).
- Synthetic files at 5k and 50k rows: no OOM, acceptable runtime, constant memory (streaming).

## 16. Stages

Each stage lists its scope, how to try it, and when it is done. Every stage ends with `mvn verify` green (except Stage 0) and no unrelated refactor. Follow the TDD rule from 15.1 in every stage.

| Stage | Name | Depends on | Can start in parallel with |
|---|---|---|---|
| 0 | Deployment baseline + map services contracts | - | 1 |
| 1 | Project skeleton + pure core | - | 0 |
| 2 | End-to-end CSV flow, offline | 1 | - (after 1) |
| 3 | Real maps: Photon + OSRM adapters | 0, 2 | 4 (after 0,2) |
| 4 | XLSX support and CSV/XLSX parity | 2 | 3 (after 2) |
| 5 | Optional offline bundle + bundled-services launcher | 0, 3, 4 | - (only if bundle selected) |
| 6 | Hardening, smoke tests, docs | 3, 4 (5 if bundle selected) | - |

### 16.1 Stage checklist

Use this checklist to track progress. Mark a stage done only when its "Done when" criteria and required checks pass.

- [ ] **Stage 0 - Deployment baseline + map services contracts** (`stage-0-deploy-baseline`) - independent; can run in parallel with Stage 1.
- [ ] **Stage 1 - Project skeleton + pure core** (`stage-1-project-core`) - independent; can run in parallel with Stage 0.
- [ ] **Stage 2 - End-to-end CSV flow, offline** (`stage-2-csv-offline`) - depends on Stage 1.
- [ ] **Stage 3 - Real maps: Photon + OSRM adapters** (`stage-3-real-maps`) - depends on Stages 0 and 2; can run in parallel with Stage 4 once dependencies are done.
- [ ] **Stage 4 - XLSX support and CSV/XLSX parity** (`stage-4-xlsx-parity`) - depends on Stage 2; can run in parallel with Stage 3 once Stage 2 is done.
- [ ] **Stage 5 - Optional offline bundle + bundled-services launcher** (`stage-5-offline-bundle`) - optional; depends on Stages 0, 3, 4; execute only if offline bundle packaging is selected.
- [ ] **Stage 6 - Hardening, smoke tests, docs** (`stage-6-hardening-docs`) - depends on Stages 3, 4 and 5 only if bundle selected.

### 16.2 Standard stage execution protocol (team prompt)

Use this prompt/protocol when assigning any stage to a teammate. Replace placeholders before sending.

```text
Tarea: Implementar Stage <N> - <Nombre> del proyecto ZON Calculadora.

Contexto obligatorio:
- Leer `PLAN-IMPLEMENTACION.md` completo antes de empezar, con foco en: secciones 2-5, 8-15, Stage <N>, y Definition of Done.
- Respetar arquitectura hexagonal: `core` sin dependencias a CLI/HTTP/CSV/XLSX/OSRM/Photon/filesystem/process. Los adapters implementan ports.
- TDD obligatorio: Red -> Green -> Refactor. No agregar comportamiento productivo sin test que falle primero.
- Stack: Java 21, Maven, `groupId=ar.zon`, `artifactId=zon-calculadora`, base package `ar.zon.calculadora`. No usar `itec` en identificadores.
- No incluir alcance fuera de Increment 1 (precio/costo/moneda, UI, API REST, autenticación, DB, avión, EV, generación de plantilla encuesta, conteo de asistentes).

Dependencias / paralelismo:
- Stage asignado: <N>
- Depende de: <listar stages o "ninguna">
- Puede hacerse en paralelo con: <listar stages o "ninguna">
- No iniciar si las dependencias no están mergeadas, salvo acuerdo explícito y documentado.

Flujo Git / PR (nomenclatura estándar):
1. Actualizar rama base: `git checkout main` (o la rama base acordada) && `git pull`.
2. Crear rama: `git checkout -b stage-<N>-<slug>`.
   - Nomenclatura de rama: `stage-0-deploy-baseline`, `stage-1-project-core`, `stage-2-csv-offline`, `stage-3-real-maps`, `stage-4-xlsx-parity`, `stage-5-offline-bundle`, `stage-6-hardening-docs`.
3. Implementar SOLO el alcance del Stage <N>. Si aparece algo extra, anotarlo como issue/follow-up; no expandir scope.
4. Correr checks antes de PR:
   - `mvn test` (si aplica)
   - `mvn verify`
   - Smoke/manual check definido en "Try it / Done when" del stage.
5. Commits: Conventional Commits, pequeños y revisables. Sin Co-Authored-By de AI.
6. Push y PR:
   - Título PR: `stage-<N>: <nombre corto>`
   - Descripción PR debe incluir: objetivo del stage, cambios realizados, cómo probar (comandos), resultado de tests/verificación, riesgos/limitaciones, y checklist "Done when" del stage.
7. No mergear sin revisión. Resolver comentarios con cambios trazables.

Entregable esperado:
- Código/tests/docs del stage.
- PR con evidencia de verificación.
- Si hay decisiones abiertas o bloqueos, reportarlos explícitamente en la PR con opciones y impacto.
```

### 16.3 Dependency matrix (quick reference)

| Stage | Depends on | Independent / Parallel |
|---|---|---|
| 0 | - | Independent. Can run in parallel with Stage 1. |
| 1 | - | Independent. Can run in parallel with Stage 0. |
| 2 | 1 | Starts after Stage 1. |
| 3 | 0, 2 | Can run in parallel with Stage 4 after Stages 0 and 2 are done. |
| 4 | 2 | Can run in parallel with Stage 3 after Stage 2 is done (does not depend on Stage 3). |
| 5 | 0, 3, 4 | Optional. Only if offline bundle selected. After 0,3,4. |
| 6 | 3, 4 (+5 if bundle selected) | Final hardening stage. |

### Stage 0 - Deployment baseline + map services contracts

Goal: define and validate deployment on **our own server, supporting Windows and Ubuntu**. Photon/OSRM must be reachable through the same HTTP contracts. Docker is optional; native/compiled OSRM is a valid alternative behind the same adapter.

Scope:
1. Choose initial target environment: our own server, Windows and/or Ubuntu, OS/arch, Java 21 availability, Docker availability, disk/RAM/network policy. Record in `docs/deployment.md`.
2. Validate map services using selected path: Annex C (Docker Compose, optional) or Annex A (native processes). Both must satisfy the same HTTP contracts used by adapters (`PhotonGeocoder`, `OsrmRouteCalculator`).
3. **OSRM runtime spike (recommended):** run the same small benchmark against Docker OSRM and native/compiled OSRM where feasible (same data, same requests, same machine class). Measure p95 latency, startup, RAM, ops complexity, Windows/Ubuntu friction. Record in `docs/osrm-runtime-benchmark.md`. This compares infra runtimes only; no core changes.
4. Save one sample Photon response and one OSRM response under `src/test/resources/fixtures/` (used later to write adapter tests).
5. If optional offline bundle (Annex B) is in scope, do target-OS offline go/no-go from A.6 check 4 and record it. If not selected, record "bundle not selected" explicitly.
6. Record environment, RAM, disk, setup time, per-request latency, and image/binary versions in docs.

Done when: selected server deployment path passes smoke checks on target OS(es), fixture responses are saved, OSRM runtime comparison is recorded (or explicitly deferred with reason), and deployment/bundle decisions are recorded.

### Stage 1 - Project skeleton + pure core

Scope:
1. Maven project, package structure, JUnit/AssertJ wired, `mvn test` green on the empty skeleton.
2. Add project bootstrap artifacts: `.gitignore`, `.gitattributes` (recommended), `.editorconfig` (recommended), `README.md` skeleton, `docs/` skeleton, `config/application.example.properties`, `config/emission-factors.example.properties`.
3. `.gitignore` must cover at least: `target/`, `out/`, `dist/`, `logs/`, IDE files (`.idea/`, `*.iml`, `.vscode/`, `.classpath`, `.project`, `.settings/`), OS files (`.DS_Store`, `Thumbs.db`), local env/config overrides (`.env*`, `config/application.properties`, `config/emission-factors.properties`), generated reports/runtime outputs, and local map data (`maps/`, `photon_data/`, `*.osm.pbf`, `*.osrm*`, `osrm-data/`). Keep tracked examples/synthetic fixtures with negation rules where needed.
4. Config model + `.properties` loader + factors loader.
5. Domain model, statuses, error codes.
6. Normalization and validation rules (9.2-9.5).
7. `HaversineCalculator`, `ConsumptionCalculator`, `EmissionCalculator`.
8. Summary aggregation.

Try it: unit tests only.
Done when: all unit tests in 15.2 pass, including the four hand-calculated cases, and bootstrap artifacts exist with the expected ignore coverage.

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
6. Adapter tests using the fixtures saved in Stage 0; optional tagged smoke test against real services started manually or via Docker Compose (`services.mode=EXTERNAL`).

Try it: bring up map services with Annex C (`docker compose -f deploy/docker/docker-compose.maps.yml up -d`) or Annex A, then run with `geocoder.mode=PHOTON`, `distance.mode=ROUTE` on a few Córdoba localities.
Done when: route km are plausible against a reference (±10% suggested), a stopped OSRM triggers fallback per row, a stopped service at startup gives exit 2, and the same run works against Dockerized endpoints configured through `photon.baseUrl`/`osrm.baseUrl`.

### Stage 4 - XLSX support and parity

Scope:
1. `XlsxRowReader` (fastexcel-reader): sheet/header row config, numeric cells, empty rows.
2. `XlsxReportWriter` (fastexcel): numeric cells, header formatting, optional "Summary" sheet if streaming allows.
3. Auto-select adapters by extension.
4. Parity test: same data as CSV and XLSX gives the same business results (ADR 0002).
5. Verify the shaded fat jar reads and writes XLSX when run with `java -jar`.

Done when: parity test green and `java -jar` works with `.xlsx` in and out.

### Stage 5 - Optional offline bundle + bundled-services launcher

Goal: optional folder (per OS/architecture) for restricted/offline machines, with no Docker, no Java installed, no admin rights and no internet (section 17, Annex B). Execute only when offline bundle packaging is selected.

Scope:
1. `infra.services.LocalServicesManager` + `services.*` config keys + `--services` flag, built with TDD around a fake child process: starts, waits for health, stops on success / failure / interrupt, port in use, missing file, start timeout.
2. Resolve all relative paths against the app home (`-Dzon.home`, fallback: parent folder of the jar).
3. Launcher scripts (`zon-calculadora.sh` / `zon-calculadora.bat`) that use the bundled JRE.
4. `packaging/build-bundle` script that assembles the layout in B.1 for one OS/architecture.
5. Clean-machine, network-off test (B.6).

Try it: unzip the bundle on a clean machine of the target OS, disconnect the network, run the launcher with the sample CSV and XLSX.
Done when: if selected, both samples produce the expected reports in `ROUTE` mode, no orphan processes remain after the run or after Ctrl+C, and ports are free afterwards. If not selected, record the decision and skip.

### Stage 6 - Hardening, smoke tests and docs

Scope:
1. Synthetic 5k and 50k runs (fakes) without OOM; note runtime and memory. Measure bundle startup time and RAM with real services.
2. Final review of exit codes, error messages (Spanish) and progress output.
3. README (setup, config, CLI, limitations, OSM attribution), `docs/prd-delta.md`, `docs/deployment.md`, `docs/maps-setup.md`, `docs/docker-maps.md`, and `docs/offline-bundle.md` when bundle is selected; ADR addenda (0002: load test; 0001: server deployment with optional Docker/native processes).
4. Walk through the Definition of Done (section 20).

Done when: the DoD checklist is fully ticked.

## 17. Optional Self-contained Offline Bundle

This packaging is **optional**. Primary deployment is our own or hosted server (Annex C / native processes). Use this only for restricted/offline target machines. When selected, the target machine needs **nothing installed**: no Docker, no Java, no internet, no admin rights. Everything ships in one folder, built once per target OS and CPU architecture.

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
| Photon/OSRM setup friction (first time, big downloads) | Blocks end-to-end tests | Stage 0 first and in parallel; Annex C Docker option for server/dev; Annex A for native; offline mode so Stages 1-2 never wait. |
| Server environment mismatch (Windows/Ubuntu, Java, Docker, ports, firewall) | Deployment fails or is insecure | Confirm target environment early; document in `docs/deployment.md`; smoke test on Windows and Ubuntu target-like hosts. |
| Docker unavailable or undesired on target server | Docker-only plan blocks deploy | Keep Docker optional; support native Java process + native/Dockerized map services as configured. Compare Docker vs native/compiled OSRM in Stage 0 spike. |
| OSRM/Photon native on target OS (if bundle selected) | Bundle does not run there | Confirm target OS/arch early; Stage 0 go/no-go; clean offline machine test in Stage 5. |
| Missing runtime libraries in bundle (if selected) | Works only on build machine | Ship every library; clean-machine, network-off test (B.6). |
| Bundle size, startup time, RAM (if selected) | Heavy deliverable; slow start for small files | Measure and record; lookup-file geocoder option (B.10); document requirements. |
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
3. Confirm COLECTIVO attribution: `km Ã— personas Ã— factor_per_passenger_km` (current default).
4. Confirm default auto fuel when missing (`NAFTA` assumed).
5. Provide an official source for emission and consumption factors (placeholders until then).
6. Confirm tolerance for route distance validation (suggest Â±10%).
7. Confirm target deployment environment: **our own server**, Windows and Ubuntu support scope, Java 21 runtime, Docker availability/policy, ports, firewall, disk/RAM. Record in `docs/deployment.md`.
8. Run OSRM runtime spike: Docker vs native/compiled on Windows/Ubuntu where feasible; choose default per environment. Keep motor decoupled (HTTP port contracts only). Record in `docs/osrm-runtime-benchmark.md`.
9. Confirm deployment mode: Docker Compose, native processes, or hybrid (app native + maps Docker/native). Docker optional.
10. Confirm whether optional offline bundle is required. If yes: confirm target OS/arch and minimum hardware; record in `docs/offline-bundle.md`.
11. Geocoder strategy: `PHOTON` default for server. `FILE`/`COORDINATES` remain supported for restricted/dev cases (B.10 / 8.1).
12. Decide source of `osrm-routed` for native/bundle packaging: build from source or prebuilt self-contained build (verify checksum and license).

Confirmed and closed: fixed destination per run, row = respondent = vehicle, personas range 1-5 configurable, EV out, no cost, no 500k target, deployment target is our own server with Windows + Ubuntu support, Docker optional, motor decoupled from OSRM runtime via ports, `PHOTON` default geocoder strategy, offline bundle optional/Docker-free, attendance counting outside the app.

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
- [ ] Server deployment path documented and smoke-tested on Windows and/or Ubuntu target-like environment (Docker optional; native/compiled OSRM path evaluated).
- [ ] If offline bundle selected: it runs on a clean machine of the target OS with **no Java, no Docker, no admin rights and no network**, and leaves no orphan processes.
- [ ] README, `docs/prd-delta.md`, `docs/deployment.md`, `docs/maps-setup.md`, `docs/docker-maps.md` written; `docs/offline-bundle.md` written when bundle selected; OSM attribution included.

---

# Annex A - Native map services on the development machine (becomes `docs/maps-setup.md`)

**Native/no-Docker setup path.** This annex is for machines where you build, test, or run map services as native processes (internet needed for downloads/preprocess). The same pieces can be copied into the optional offline bundle (Annex B) or used in native server deployment.

## A.0 What you are installing

- **Photon**: a geocoder. Turns text ("Alta Gracia") into coordinates. A Java program plus a downloaded search index.
- **OSRM**: a routing engine. Turns two coordinates into road distance. Native programs (`osrm-extract`, `osrm-partition`, `osrm-customize`, `osrm-routed`) plus an OpenStreetMap extract that you pre-process once into `.osrm` files.

Both run as small local web servers (`localhost:2322` and `localhost:5000`). Downloads and preparation are one-time; afterwards everything works offline. Data comes from OpenStreetMap (Â© OpenStreetMap contributors, ODbL).

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
In another terminal (coordinates are **longitude,latitude**; CÃ³rdoba city to Villa Carlos Paz):
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

1. `curl "http://localhost:2322/api?q=Alta%20Gracia&limit=3"` returns features with coordinates inside CÃ³rdoba province.
2. `curl "http://localhost:5000/route/v1/driving/-64.1888,-31.4201;-64.4978,-31.4241?overview=false"` returns `"code":"Ok"` with a plausible distance.
3. Latency is acceptable. Measure with `curl -s -o /dev/null -w "%{time_total}\n" "<url>"` and record it in `docs/maps-setup.md`.
4. **Target-OS check (only if optional offline bundle is selected):** on a machine (or VM) with the target OS/architecture, **with the network disabled and no Java or Docker installed**, `osrm-routed` serves the `.osrm` files and Photon runs with the runtime copied from your build (A.1), and checks 1 and 2 pass there.

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
- Attribution: "Map data Â© OpenStreetMap contributors (ODbL)" in README and delivered docs.

## A.9 Matching config keys (EXTERNAL mode)

```properties
services.mode=EXTERNAL
photon.baseUrl=http://localhost:2322
osrm.baseUrl=http://localhost:5000
geocoder.mode=PHOTON
distance.mode=ROUTE
```

---

# Annex B - Optional self-contained offline bundle (becomes `docs/offline-bundle.md`)

## B.0 Goal

Optional packaging for restricted/offline machines. A folder that runs on a machine with **no Docker, no Java, no internet and no admin rights**. It contains the bundled JRE, the app jar, native `osrm-routed` with its `.osrm` data, and Photon (jar + index) running on the same JRE. One bundle per OS and CPU architecture. Primary deployment remains server-based (own/hosted), with Docker optional.

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

Ship a `licenses/` folder with the licenses of the runtime, OSRM and Photon, and the line "Map data Â© OpenStreetMap contributors (ODbL)". Review the license of every third-party binary you redistribute.

## B.9 Updating map data

On the build machine: re-download the extract and the Photon index, re-run A.4.3, rebuild the bundle (B.3) and re-run B.6. Do not mix data from different OSRM or Photon versions with other binaries.

## B.10 Lighter alternative: no Photon

If bundle size, startup time or RAM are a problem and the origins are a known set of CÃ³rdoba localities, bundle a lookup file (`geocoder.mode=FILE`, columns `text;lat;lon`) instead of Photon. The bundle shrinks and becomes fully deterministic, but any place not in the file gives `LOCATION_NOT_FOUND`. Decide with the client (open item 9).






---

# Annex C - Dockerized server deployment option (app + OSRM + Photon) (becomes `docs/docker-maps.md` / `docs/deployment.md`)

## C.0 Goal

Provide a **simple, reproducible Docker Compose option** for deploying on our own server or a hosted server. It covers OSRM and Photon and may also run the Java calculator. Docker is **optional**: the same contracts must work with native processes. It does **not** replace the optional Docker-free offline bundle (Annex B).

## C.1 When to use which path

| Path | Use when | Docker | Offline after setup |
|---|---|---:|---:|
| Annex C (this) | Own/hosted server deploy, dev, CI smoke | Optional (yes in this annex) | Depends on volumes/data cache |
| Annex A | Native local setup, debugging native binaries, preparing bundle assets | No | Yes, once installed |
| Annex B | Optional restricted/offline delivery | No | Yes |

## C.2 Prerequisites

- Docker Engine/Desktop with Docker Compose v2 (docker compose version).
- Disk: ~8-15 GB free for Argentina OSM extract, OSRM generated files, Photon index, and images (measure and record actual value).
- RAM: 8 GB minimum recommended; OSRM preprocessing and Photon import can need more. Record actual peak.
- Internet for first-time image pull and data download.

## C.3 Directory layout

Create these paths (they can live outside the repo; keep large data out of git):

`	ext
deploy/docker/
  docker-compose.maps.yml
  .env.maps.example
  .env.maps                  # local only, gitignored
data/maps/                   # local only, gitignored
  osrm/
    argentina-latest.osm.pbf
    argentina.osrm*          # generated
  photon/
    photon_data/             # generated/imported
`

Add to .gitignore: deploy/docker/.env.maps, data/maps/, *.osm.pbf, *.osrm, *.osrm.*, photon_data/.

## C.4 Environment file (deploy/docker/.env.maps.example)

`dotenv
# Host ports exposed to the calculator/app
OSRM_PORT=5000
PHOTON_PORT=2322

# Data paths (relative to deploy/docker or absolute). Keep outside git.
OSRM_DATA_DIR=../../data/maps/osrm
PHOTON_DATA_DIR=../../data/maps/photon

# PBF file name inside OSRM_DATA_DIR
OSRM_PBF=argentina-latest.osm.pbf
OSRM_BASE=argentina

# Image pins - replace with tested versions/tags before release; record in docs/docker-maps.md
OSRM_IMAGE=osrm/osrm-backend:latest
PHOTON_IMAGE=rtuszik/photon-docker:latest

# Optional Photon settings
PHOTON_COUNTRY_CODE=ar
PHOTON_JAVA_OPTS=-Xms512m -Xmx2g
`

Copy to .env.maps and adjust values. **Do not commit .env.maps.**

## C.5 OSRM preprocessing (one-time per OSM data version)

OSRM needs preprocess files before osrm-routed can serve routes. Run these one-off containers from deploy/docker/ with the same OSRM_IMAGE:

`ash
# 1) Download Argentina extract (Geofabrik). Pin a dated file for reproducibility.
mkdir -p ../../data/maps/osrm
curl -L -o ../../data/maps/osrm/argentina-latest.osm.pbf \
  https://download.geofabrik.de/south-america/argentina-latest.osm.pbf

# 2) Extract
docker run --rm -t -v "C:\Users\mateo\Desktop\Calculadora ITEC\batch-encuestas/../../data/maps/osrm:/data"  \
  osrm-extract -p /opt/car.lua /data/argentina-latest.osm.pbf

# 3) Partition
docker run --rm -t -v "C:\Users\mateo\Desktop\Calculadora ITEC\batch-encuestas/../../data/maps/osrm:/data"  \
  osrm-partition /data/argentina-latest.osrm

# 4) Customize
docker run --rm -t -v "C:\Users\mateo\Desktop\Calculadora ITEC\batch-encuestas/../../data/maps/osrm:/data"  \
  osrm-customize /data/argentina-latest.osrm
`

PowerShell equivalent:

`powershell
New-Item -ItemType Directory -Force ../../data/maps/osrm | Out-Null
Invoke-WebRequest -Uri https://download.geofabrik.de/south-america/argentina-latest.osm.pbf -OutFile ../../data/maps/osrm/argentina-latest.osm.pbf
docker run --rm -t -v "C:\Users\mateo\Desktop\Calculadora ITEC\batch-encuestas/../../data/maps/osrm:/data"  osrm-extract -p /opt/car.lua /data/argentina-latest.osm.pbf
docker run --rm -t -v "C:\Users\mateo\Desktop\Calculadora ITEC\batch-encuestas/../../data/maps/osrm:/data"  osrm-partition /data/argentina-latest.osrm
docker run --rm -t -v "C:\Users\mateo\Desktop\Calculadora ITEC\batch-encuestas/../../data/maps/osrm:/data"  osrm-customize /data/argentina-latest.osrm
`

Notes:
- Use the **same OSRM image/version** for preprocess and runtime.
- car.lua is inside the official OSRM backend image at /opt/car.lua.
- Preprocess can take several minutes and significant RAM/CPU. If killed, increase Docker memory.

## C.6 Docker Compose file (deploy/docker/docker-compose.maps.yml)

`yaml
name: zon-maps

services:
  osrm:
    image: 
    command: osrm-routed --algorithm mld /data/.osrm
    ports:
      - ":5000"
    volumes:
      - :/data:ro
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- 'http://localhost:5000/route/v1/driving/-64.1888,-31.4201;-64.1888,-31.4201?overview=false' | grep -q 'Ok'"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 20s

  photon:
    image: 
    ports:
      - ":2322"
    volumes:
      - :/photon/data
    environment:
      - COUNTRY_CODE=
      - JAVA_OPTS=
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- 'http://localhost:2322/api?q=Cordoba&limit=1' | grep -q 'features'"]
      interval: 20s
      timeout: 5s
      retries: 20
      start_period: 60s
`

Notes:
- Photon image/tag and env vars must be validated against the selected image documentation before implementation; pin a known-good tag. If the team chooses a different Photon image, keep the same external contract: HTTP on port 2322 with /api.
- For production-like deploys, bind ports to localhost or a private network; do not expose public map endpoints without access control.
- 
estart: unless-stopped is appropriate for dev/deploy; disable in CI if needed.

## C.7 Step-by-step: bring up services

`ash
cd deploy/docker
cp .env.maps.example .env.maps
# edit .env.maps if needed

# Preprocess OSRM once (C.5)
# Start services
docker compose --env-file .env.maps -f docker-compose.maps.yml up -d

# Check status
docker compose --env-file .env.maps -f docker-compose.maps.yml ps
docker compose --env-file .env.maps -f docker-compose.maps.yml logs -f --tail=100
`

Stop:

`ash
docker compose --env-file .env.maps -f docker-compose.maps.yml down
`

Reset data (destructive, local only):

`ash
docker compose --env-file .env.maps -f docker-compose.maps.yml down -v
# then remove data/maps/osrm and data/maps/photon contents as needed
`

## C.8 Verification checklist

Run after up -d:

`ash
# OSRM route
curl "http://localhost:5000/route/v1/driving/-64.1888,-31.4201;-64.4992,-31.4201?overview=false"

# Photon geocode
curl "http://localhost:2322/api?q=Kempes%20Cordoba&limit=3"
`

Expected:
- OSRM returns JSON with code: "Ok" and 
outes[0].distance in meters.
- Photon returns GeoJSON eatures with coordinates [lon,lat].
- docker compose ps shows both services healthy.

## C.9 App configuration for Dockerized maps

Use services.mode=EXTERNAL and point to Compose endpoints:

`properties
services.mode=EXTERNAL
geocoder.mode=PHOTON
distance.mode=ROUTE
photon.baseUrl=http://localhost:2322
osrm.baseUrl=http://localhost:5000
healthcheck.enabled=true
`

For a remote deploy, replace localhost with the deploy host/DNS reachable from the machine running the calculator. Keep credentials/network access out of this plan if not needed; do not expose unauthenticated services publicly.

## C.10 Operational notes

- **Pin image versions** before implementation/release. latest is acceptable only for initial spike; record tested tags in docs/docker-maps.md.
- **Same OSRM version** must be used for preprocess and runtime.
- **Photon index compatibility** matters; use the image/version flow documented by the selected Photon image.
- **Data updates**: download new PBF/index, rerun preprocess/import, restart compose, smoke test, record data date in data/maps/VERSION.txt or docs.
- **Resource limits**: add Compose deploy.resources or Docker Desktop limits if needed; measure before setting hard caps.
- **Logs**: docker compose logs; avoid logging full survey rows.
- **Backups**: back up generated OSRM/Photon data if rebuild time is costly.
- **Security**: bind to localhost/private network; no public exposure by default; no secrets in committed files.

## C.11 Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| OSRM container exits immediately | Missing/invalid .osrm files or wrong base name | Re-run C.5; check volume mount and OSRM_BASE. |
| OSRM NoRoute | Bad coords, outside extract, lat/lon swapped | Verify lon,lat; confirm Argentina extract; check fallback behavior. |
| Photon unhealthy / empty results | Index not ready, wrong image env, insufficient memory | Check logs; increase PHOTON_JAVA_OPTS; validate image docs and country/data config. |
| Port conflict | 2322/5000 already used | Change OSRM_PORT/PHOTON_PORT in .env.maps and app config. |
| Slow first startup | Photon index load / OSRM mmap warmup | Wait for healthcheck; record startup time. |
| Preprocess OOM/killed | Docker memory limit too low | Increase Docker memory; rerun preprocess. |
| App gets connection refused | Services not healthy or wrong baseUrl | Check docker compose ps, healthchecks, and photon.baseUrl/osrm.baseUrl. |

## C.12 Definition of done for Docker maps setup

- [ ] deploy/docker/docker-compose.maps.yml and .env.maps.example exist and are documented.
- [ ] .gitignore excludes local env/data/map artifacts.
- [ ] OSRM preprocess steps run successfully on a clean machine with Docker.
- [ ] docker compose up -d makes both services healthy.
- [ ] OSRM and Photon smoke checks pass.
- [ ] Calculator runs against Dockerized endpoints with services.mode=EXTERNAL.
- [ ] Image tags, data source/date, RAM/disk, and startup time recorded in docs/docker-maps.md.




