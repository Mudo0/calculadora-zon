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
- T5 — S1,S2,S3 — optional tagged smoke test and documentation of the pending real-service check — inline — RED: no tagged test exists — commit: 34b347a — GREEN: `mvn -o test` 97 tests, 2 skipped — review: under_budget
- T6 — S1,S2 — full `mvn verify`, jar smoke with fixtures/local stub, and stage closure with honest pending state — inline — verification-only — evidence: `mvn -o verify` BUILD SUCCESS (97 tests, 2 skipped, 1 IT); jar + stub 8765 exit 0, `36,3709 km`, `origen_label=Alta Gracia`, `distance_mode=ROUTE` sin fallback

## Log

L1 (2026-11-24): y podemos construir el stage 3 y luego dejar como pendiente eso que falta? probablemente se le haya olvidado subir a mi compañero

L2 (2026-11-24): El usuario confirmó la construcción del Stage 3 con el smoke de servicios reales pendiente. Previamente decidió dejar para después la integración de alias/respaldos de GeoRef. Route: inline, TDD RED -> GREEN, work-unit commits on stage-3-real-maps. RDD está activo por defecto; la dependencia JSON nueva se escala como ítem de riesgo 5. Jackson 2.21.5, jackson-core 2.21.5 y jackson-annotations 2.21 están en el repositorio Maven local.

L3 (2026-11-24): T1 revisado y aprobado con consentimiento explícito del usuario; recibo quemado en la lineage review-0f18a4f2645403b7. Los hallazgos quedaron como advisories no bloqueantes: MAPPER.readTree tras recordSuccess en HttpJsonClient, 4xx sin reintento ni guardia, validación faltante en el constructor, y avisos de CVE sobre jackson-databind 2.21.5 con versiones corregidas publicadas (reclamo tardío todavía no verificado). Native review declaró esos hallazgos informational y no ofreció corrección, así que no reabren la revisión de T1; se registran para trabajo posterior.

L4 (2026-11-24): T2 en verde. RED observado como error de compilación por la clase PhotonGeocoder ausente; GREEN con la fixture photon-alta-gracia.json servida por un HttpServer local. Se verificaron mapeo [lon,lat], consulta con q/limit/lat/lon/bbox codificados, selección determinista independiente del orden y respuesta sin features.

L5 (2026-11-24): T3 en verde. RED observado como error de compilación por la clase OsrmRouteCalculator ausente; GREEN con la fixture osrm-cordoba-villa-carlos-paz.json servida por un HttpServer local. Se verificaron 36370.9 m -> 36.3709 km, URL `driving` con longitud primero y `overview=false`, y respuestas `code != Ok` o sin distancia como ruta inexistente. La primera ejecución falló solo por el decimal final de la coordenada formateada con `%.6f`; se ajustó la expectativa del test.

L6 (2026-11-24): T4 en verde. RED: los guardias de Stage 2 rechazaban `distance.mode=ROUTE` y `geocoder.mode=PHOTON`, y el `Router` era vacío. GREEN: `Stage3CliTest` con servidor local sirve ambas fixtures y verifica flujo PHOTON+ROUTE (salida 0, `36,3709`, `origen_label=Alta Gracia`, sin fallback), apertura de disyuntor con salida 2 y `SERVICE_UNAVAILABLE` sin escribir informe, y health-check fallido en arranque con salida 2. Se conservó el constructor de 7 argumentos de `RowResult` (S7), el contrato de 30 columnas y los códigos de salida (S4). `mvn -o test`: 95 tests, 0 fallos. Suite completa en verde tras los cambios de `RowResult`/`RowProcessor`/`CalculateSurveyBatchUseCase`.

L7 (2026-11-24): T5 en verde. RED: no existía prueba etiquetada ni documento de etapa. GREEN: `RealMapsSmokeTest` con `@Tag("smoke")` y `@EnabledIfSystemProperty(zon.smoke)`, endpoints por `-Dphoton.baseUrl` (default 2322) y `-Dosrm.baseUrl` (default 5000), acotado a ±10% de 36,37 km; `docs/stage-3.md` registra decisiones, comandos y el pendiente explícito. `mvn -o test`: 97 tests, 2 omitidos. Al activarlo con `-Dzon.smoke=true` falla con `ConnectException` en 2322/5000: evidencia de que los servicios no están levantados. PowerShell requiere comillas en las propiedades con punto; se documentó.

L8 (2026-11-24): T6 verificado. `mvn -o verify`: BUILD SUCCESS, 97 tests (2 omitidos por smoke) y 1 IT (`OfflineSmokeIT`), jar shaded generado con avisos no bloqueantes de recursos duplicados de Jackson/fastcsv. Smoke del jar con stub HTTP local en 127.0.0.1:8765 sirviendo ambas fixtures y `healthcheck.enabled=true`: salida 0, `filas=1 correctas=1`, `36,3709 km`, `origen_label=Alta Gracia`, `distance_mode=ROUTE`, `distance_fallback_used=false`. Stub y scratch fuera del worktree, eliminados después. El checkbox de Stage 3 del plan se deja sin marcar: queda pendiente el smoke con servicios reales y la integración GeoRef diferida. Sin push, PR ni merge.

L9 (2026-11-24): El usuario pidió dejar por escrito los pendientes dentro de la PR y en un archivo. Se creó `docs/pendientes.md` con cinco pendientes clasificados P1/P2/P3 (qué falta, por qué y cómo hacerlo), texto listo para pegar en la descripción del PR y la condición de cierre de cada uno. Se enlazó desde `docs/README.md` y `docs/stage-3.md`; se corrigió el conteo de pruebas en `docs/stage-3.md` (97, no 95) y se verificó que `deploy/docker/` y `dist/` no existen en este workspace. No se creó PR ni issue: ambas acciones requieren autorización explícita.

L10 (2026-11-24): El usuario autorizó crear un issue por pendiente, aclarar el bloqueo en el plan y publicar rama con PR. Se crearon los issues #3 (smoke real), #4 (hardening HTTP), #5 (CVE Jackson), #6 (GeoRef) y #7 (OSRM/Docker) con el mismo contenido de `docs/pendientes.md`. Se enlazaron desde el inventario y se añadió a `PLAN-IMPLEMENTACION.md` la condición explícita de que Stage 3 no se marca completo hasta resolver #3-#7. Queda pendiente el push y la apertura del PR con esos issues como relacionados, sin cerrarlos.

L11 (2026-11-24): Rama publicada en `origin/stage-3-real-maps` y PR [#8](https://github.com/Mudo0/calculadora-zon/pull/8) abierta contra `main` con los ocho commits de la etapa. El cuerpo enlaza los issues #3-#7 como relacionados, sin cerrarlos, e indica lo no ejecutado (smoke real, medición de cobertura y javadoc). Durante la publicación apareció un commit ajeno, `72d78c7 update readme`, que normalizó un enlace en `docs/README.md`; quedó incluido en el PR porque ya estaba en la rama local. No se descartó ni se modificó.

L12 (2026-11-24): El usuario pidió corregir tres hallazgos de la revisión de la PR #8 y dejar el cuarto (BOTH) para después con issue propio. RED observado en tres pruebas: los homónimos de Photon con orden invertido seleccionaban el primero, los éxitos de Photon reiniciaban el contador compartido antes del segundo fallo de OSRM, y un fallo del router con fallback escribía `ROUTE_NOT_FOUND`. GREEN con score constante sin orden en `PhotonGeocoder` (dos puntos con el mismo nombre quedan sin candidatos → `ERROR/LOCATION_NOT_FOUND`), un `ServiceFailureGuard` por servicio en `Main` y `ROUTER_ERROR` como motivo en `RowProcessor`. Se creó el issue [#9](https://github.com/Mudo0/calculadora-zon/issues/9) para la prueba CSV de `distance_straight_km` en BOTH, que no se implementa. `mvn -o verify`: BUILD SUCCESS, 100 tests (2 omitidos) y 1 IT. Commit: 281cd6e. Sin merge de la PR.

L13 (2026-11-24): Los dos commits se publicaron en `origin/stage-3-real-maps` (`6d91284`) y la PR #8 se actualizó sola; sigue OPEN y sin merge. Assess nativo sobre `0378241..HEAD`: `risk: medium`, `review_due: false` (`under_budget`), así que no se abrió una transacción de revisión ni se pidió consentimiento. Sin smoke con servicios reales: #3 sigue pendiente y el checklist de Stage 3 continúa sin marcar.

L14 (2026-11-24): El usuario pidió corregir dos comentarios inline nuevos de la PR #8 (Tomilomi). RED observado: `noRouteFromRealHttp400MeansNoRoute` falló con `ServiceCallException` de `HttpJsonClient.java:66` antes de leer el `code`, y `exactMatchWinsRegardlessOfResponseOrder` eligió `Barrio Córdoba` en vez de `Córdoba`. GREEN: `ServiceCallException` conserva status y cuerpo del 400 de forma aditiva, `OsrmRouteCalculator` mapea solo `400` con `{"code":"NoRoute"}` a ruta inexistente y deja fallar los demás 4xx, y `PhotonGeocoder` da score `2.0` a la coincidencia exacta normalizada manteniendo la detección de homónimos. `mvn -o verify`: BUILD SUCCESS, 103 tests (2 omitidos por smoke real) y 1 IT. Commits `1190f47` y `f4d9bf1` publicados en `origin/stage-3-real-maps`; PR #8 sigue OPEN y sin merge. #3-#7 y #9 siguen abiertos; el smoke con servicios reales no se ejecutó.

L15 (2026-11-24): Assess nativo sobre `f452976..HEAD`: `risk: medium`, `review_due: false` (`under_budget`), sin transacción de revisión ni consentimiento. Se respondieron los dos comentarios inline con texto UTF-8 verificado sin mojibake: respuestas en `https://github.com/Mudo0/calculadora-zon/pull/8#discussion_r4688397935` y `https://github.com/Mudo0/calculadora-zon/pull/8#discussion_r4688398213`. La PR sigue OPEN y sin merge; no se cerraron issues.

L16 (2026-11-24): Apareció un tercer comentario inline de Tomilomi (4688485054) sobre candidatos de Photon sin `properties.name`: la etiqueta de respaldo tomada de la consulta recibía score exacto y chocaba con el candidato real en la detección de homónimos, dejando la fila sin candidato. RED observado como `NoSuchElementException` en `namelessCandidateDoesNotDiscardTheExactMatch`. GREEN: `toCandidate` usa el nombre original para puntuar, la etiqueta de respaldo queda solo para mostrar y la comparación de homónimos usa el nombre original sin contar los anónimos; el mapa se renombró `pointsByName` con tests en verde. `mvn -o verify`: BUILD SUCCESS, 104 tests (2 omitidos por smoke real) y 1 IT. Commit `77bc01d` publicado; respuesta verificada en `https://github.com/Mudo0/calculadora-zon/pull/8#discussion_r4688534967`. Assess `3f57ea7..HEAD`: `medium` / `under_budget`. PR #8 sigue OPEN y sin merge; #3-#7 y #9 siguen abiertos.
