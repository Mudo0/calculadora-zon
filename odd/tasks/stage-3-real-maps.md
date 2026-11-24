# Stage 3 - Real maps: Photon + OSRM adapters
Branch: stage-3-real-maps | Base: 72e5754 | Delivery: work-unit commits on the feature branch; push and PR require explicit user authorization.
Test runner: `mvn test` (unit), `mvn verify` (integration). JDK 21 required.
Locator: odd/tasks/stage-3-real-maps.md (repository-relative).

## Specs

S1 - Stage 3 scope (verbatim from PLAN-IMPLEMENTACION.md):
1. `PhotonGeocoder` (`/api?q=...&limit=...`, bias `lat`/`lon`, optional `bbox`; response is GeoJSON with `[lon, lat]`).
2. Deterministic candidate selection; record chosen label and candidate count.
3. `OsrmRouteCalculator` (`/route/v1/driving/{lon},{lat};{lon},{lat}?overview=false`; distance in meters, `code` != `Ok` means route failure).
4. Fallback orchestration for `ROUTE`/`HAVERSINE`/`BOTH`; statuses and labels.
5. Caches (geocode and route-by-origin), timeouts, retry, health-check, circuit breaker.
6. Adapter tests using the fixtures saved in Stage 0; optional tagged smoke test against real services started manually or via Docker Compose (`services.mode=EXTERNAL`).

S2 - Try it and Done when (verbatim):
Try it: bring up map services with Annex C (`docker compose -f deploy/docker/docker-compose.maps.yml up -d`) or Annex A, then run with `geocoder.mode=PHOTON`, `distance.mode=ROUTE` on a few Córdoba localities.
Done when: route km are plausible against a reference (±10% suggested), a stopped OSRM triggers fallback per row, a stopped service at startup gives exit 2, and the same run works against Dockerized endpoints configured through `photon.baseUrl`/`osrm.baseUrl`.
Pendiente_acordado: el smoke con servicios reales no se puede ejecutar ahora (puertos 2322/5000 sin respuesta y el ZIP portable del Stage 0 no está en este workspace). Se entregan los adaptadores y sus pruebas con las fixtures de Stage 0; el smoke queda explícitamente pendiente y no se marca el checklist del plan como cerrado.

S3 - TDD (verbatim): 1. Red: write a failing test for the smallest expected behavior. 2. Green: minimum code to pass. 3. Refactor: keep tests green. No production behavior without a test that first failed for it.

S4 - CLI contract (verbatim section 13): `--input`, `--output` and `--config` are required; `--destination`, `--event`, `--services`, `--summary`, `--force`, `--log-level`, `--help`, `--version` optional. Exit codes: `0` no row errors, `1` at least one row `ERROR`, `2` fatal error. Config does not define input/output paths.

S5 - Configuration keys (verbatim section 8.1): `geocoder.mode=PHOTON`, `geo.bias.lat`, `geo.bias.lon`, `geo.bbox`, `geo.maxCandidates`, `geo.cache.enabled`, `geo.cache.maxEntries`, `distance.mode=ROUTE|HAVERSINE|BOTH`, `distance.fallback=HAVERSINE|NONE`, `route.cache.enabled`, `route.cache.maxEntries`, `photon.baseUrl`, `osrm.baseUrl`, `http.timeoutMs`, `http.retries`, `http.maxConsecutiveFailures`, `healthcheck.enabled`, `services.mode=EXTERNAL`.

S6 - Architecture: `core` remains free of CLI, filesystem, HTTP, JSON and vendor dependencies. Reuse `Geocoder`, `GeoCandidate`, `CandidateSelector`, `CachingGeocoder`, `Router`, `CachingRouter`, `RowProcessor`, the existing config model and the `Calculator` pipeline. HTTP adapters and JSON parsing live in `infra`.

S7 - Risk: adding `com.fasterxml.jackson.core:jackson-databind` to `pom.xml` is a new dependency (high-risk item 5). Pin an exact version and run the required verification. Changing a public record constructor also touches a consumed API (item 3); add overloads instead of breaking existing calls.

S8 - Deferred: the Stage 0 GeoRef follow-up (7 accepted aliases and 59 centroid fallbacks, plus rechecking 515 localities) is explicitly outside this stage per user decision. It stays recorded in `docs/stage-0-localidades.md`.

S9 - Output contract: the fixed 30-column report from section 12.1 stays unchanged. `origen_label` records the chosen candidate label; the selected candidate count is kept in the domain result. `distance_fallback_reason` distinguishes `ROUTE_NOT_FOUND` from `ROUTER_ERROR`.

## Tasks

- T1 — S3,S5,S6,S7 — pinned JSON dependency, shared HTTP client with timeout/retry and consecutive-failure guard, core fatal service exception — inline — RED: compile failure for missing HTTP client and exception — commit: c91f6a0 — GREEN: `mvn -o test` exit 0 — review: granted, approved, acknowledged (lineage review-0f18a4f2645403b7)
- T2 — S1,S3,S6 — `PhotonGeocoder` with query, bias, bbox, `[lon,lat]` mapping, deterministic candidates and label/count — inline — RED: missing adapter class — commit: d1a3eb4 — GREEN: `mvn -o test` exit 0 — review: under_budget
- T3 — S1,S3,S6 — `OsrmRouteCalculator` with route URL, meters-to-km, `code != Ok` handling — inline — RED: missing adapter class — commit: 3ec062d — GREEN: `mvn -o test` exit 0 — review: under_budget
- T4 — S1,S5 — startup health-check and CLI wiring: PHOTON/ROUTE/BOTH, caches, fatal mapping, fallback reason — inline — RED: Stage 2 guards reject ROUTE/PHOTON — commit: be2fb17 — GREEN: `mvn -o test` exit 0, 95 tests — review: under_budget
- T5 — S1,S2,S3 — optional tagged smoke test and documentation of the pending real-service check — inline — RED: no tagged test exists — commit: pending — GREEN: `mvn -o test` 97 tests, 2 skipped
- T6 — S1,S2 — full `mvn verify`, jar smoke with fixtures/local stub, and stage closure with honest pending state — inline — verification-only — commit: pending

## Log

L1 (2026-11-24): y podemos construir el stage 3 y luego dejar como pendiente eso que falta? probablemente se le haya olvidado subir a mi compañero

L2 (2026-11-24): El usuario confirmó la construcción del Stage 3 con el smoke de servicios reales pendiente. Previamente decidió dejar para después la integración de alias/respaldos de GeoRef. Route: inline, TDD RED -> GREEN, work-unit commits on stage-3-real-maps. RDD está activo por defecto; la dependencia JSON nueva se escala como ítem de riesgo 5. Jackson 2.21.5, jackson-core 2.21.5 y jackson-annotations 2.21 están en el repositorio Maven local.

L3 (2026-11-24): T1 revisado y aprobado con consentimiento explícito del usuario; recibo quemado en la lineage review-0f18a4f2645403b7. Los hallazgos quedaron como advisories no bloqueantes: MAPPER.readTree tras recordSuccess en HttpJsonClient, 4xx sin reintento ni guardia, beste faltante en el constructor, y avisos de CVE sobre jackson-databind 2.21.5 con versiones corregidas publicadas. Native review declaró esos hallazgos informational y no ofreció corrección, así que no reabren la revisión de T1; se registran para trabajo posterior.

L4 (2026-11-24): T2 en verde. RED observado como error de compilación por la clase PhotonGeocoder ausente; GREEN con la fixture photon-alta-gracia.json servida por un HttpServer local. Se verificaron mapeo [lon,lat], consulta con q/limit/lat/lon/bbox codificados, selección determinista independiente del orden y respuesta sin features.

L5 (2026-11-24): T3 en verde. RED observado como error de compilación por la clase OsrmRouteCalculator ausente; GREEN con la fixture osrm-cordoba-villa-carlos-paz.json servida por un HttpServer local. Se verificaron 36370.9 m -> 36.3709 km, URL `driving` con longitud primero y `overview=false`, y respuestas `code != Ok` o sin distancia como ruta inexistente. La primera ejecución falló solo por el decimal final de la coordenada formateada con `%.6f`; se ajustó la expectativa del test.

L6 (2026-11-24): T4 en verde. RED: los guardias de Stage 2 rechazaban `distance.mode=ROUTE` y `geocoder.mode=PHOTON`, y el `Router` era vacío. GREEN: `Stage3CliTest` con servidor local sirve ambas fixtures y verifica flujo PHOTON+ROUTE (salida 0, `36,3709`, `origen_label=Alta Gracia`, sin fallback), apertura de disyuntor con salida 2 y `SERVICE_UNAVAILABLE` sin escribir informe, y health-check fallido en arranque con salida 2. Se conservó el constructor de 7 argumentos de `RowResult` (S7), el contrato de 30 columnas y los códigos de salida (S4). `mvn -o test`: 95 tests, 0 fallos. Suite completa en verde tras los cambios de `RowResult`/`RowProcessor`/`CalculateSurveyBatchUseCase`.

L7 (2026-11-24): T5 en verde. RED: no existía prueba etiquetada ni documento de etapa. GREEN: `RealMapsSmokeTest` con `@Tag("smoke")` y `@EnabledIfSystemProperty(zon.smoke)`, endpoints por `-Dphoton.baseUrl` (default 2322) y `-Dosrm.baseUrl` (default 5000), acotado a ±10% de 36,37 km; `docs/stage-3.md` registra decisiones, comandos y el pendiente explícito. `mvn -o test`: 97 tests, 2 omitidos. Al activarlo con `-Dzon.smoke=true` falla con `ConnectException` en 2322/5000: evidencia de que los servicios no están levantados. PowerShell requiere comillas en las propiedades con punto; se documentó.
