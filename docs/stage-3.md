# Stage 3 - Mapas reales: adaptadores Photon y OSRM

**Estado: adaptadores e2e implementados con pruebas con fixtures; el smoke contra servicios reales está pendiente.** El checklist del plan no se marca como cerrado hasta ejecutar esa comprobación.

## Decisiones

| Tema | Decisión |
| --- | --- |
| Geocodificación | `PhotonGeocoder` consulta `/api?q=...&limit=...`, aplica sesgo `lat`/`lon` y `bbox` opcional, y convierte el GeoJSON `[lon, lat]` en candidatos del dominio. |
| Selección | `CandidateSelector` elige de forma determinista, sin depender del orden de respuesta; se registran la etiqueta elegida y la cantidad de candidatos. |
| Rutas | `OsrmRouteCalculator` consulta `/route/v1/driving/{lon},{lat};{lon},{lat}?overview=false`, convierte metros a kilómetros y trata `code != Ok` como ruta inexistente. |
| Fallbacks | `ROUTE`/`BOTH` usan OSRM y pueden caer a Haversine; `distance_fallback_reason` distingue `ROUTE_NOT_FOUND` de `ROUTER_ERROR`. |
| Resiliencia | Un `ServiceFailureGuard` compartido abre el disyuntor tras fallos consecutivos; `ServiceUnavailableException` mapea a salida 2. El health-check es opcional y se ejecuta al arrancar. |
| Cachés | Se reutilizan `CachingGeocoder` y `CachingRouter` según `geo.cache.*` y `route.cache.*`. |
| Arquitectura | El núcleo sigue sin HTTP, JSON ni dependencias de terceros. Jackson y los clientes HTTP viven en `infra`. |

## Qué quedó en el código

| Componente | Responsabilidad |
| --- | --- |
| `HttpJsonClient`, `ServiceCallException`, `ServiceFailureGuard` | Cliente HTTP con timeout, reintentos y guardia de fallos consecutivos. |
| `PhotonGeocoder`, `OsrmRouteCalculator` | Adaptadores de los puertos `Geocoder` y `Router` hacia Photon y OSRM. |
| `RowResult`, `RowProcessor` | Conservan el overload de 7 argumentos y llevan etiqueta, candidatos y razón de fallback sin cambiar el contrato de 30 columnas. |
| `Main` | Compone adaptadores y cachés, elimina los guardias de Stage 2 y mapea la indisponibilidad de servicios a salida 2. |
| `PhotonGeocoderTest`, `OsrmRouteCalculatorTest`, `Stage3CliTest` | Pruebas con las fixtures del Stage 0 servidas por un `HttpServer` local. |

## Cómo ejecutar el smoke pendiente

Los servicios se levantan según el Anexo C del plan. El archivo `deploy/docker/docker-compose.maps.yml` aún no está materializado en el repositorio: hay que crearlo desde ese anexo y entonces ejecutar `docker compose -f deploy/docker/docker-compose.maps.yml up -d`. Photon escucha en `2322` y OSRM en `5000`. La prueba `RealMapsSmokeTest` está etiquetada como `smoke` y se omite por defecto. Para ejecutarla con endpoints por defecto:

```bash
mvn '-Dzon.smoke=true' '-Dtest=RealMapsSmokeTest' test
```

Para apuntar a otros endpoints:

```bash
mvn '-Dzon.smoke=true' '-Dphoton.baseUrl=http://host:2322' '-Dosrm.baseUrl=http://host:5000' '-Dtest=RealMapsSmokeTest' test
```

Las comillas evitan que PowerShell separe las propiedades con punto. En bash también funcionan.

Comprobaciones acordadas para el smoke: km de ruta plausibles contra la referencia (±10 %, referencia Córdoba–Villa Carlos Paz 36,37 km), fallback por fila al detener OSRM, salida 2 al detener un servicio en el arranque y el mismo flujo contra endpoints Docker parametrizados por `photon.baseUrl` y `osrm.baseUrl`.

## Verificación y continuidad

- [x] `mvn -o verify`: 100 pruebas unitarias, 0 fallos, 2 omitidas (smoke) y 1 de integración. `mvn -o test`: el smoke queda omitido por defecto.
- [x] Las fixtures reales del Stage 0 ejercitan mapeo `[lon, lat]`, consulta con `q`/`limit`/`lat`/`lon`/`bbox`, 36370,9 m → 36,3709 km, selección determinista y respuestas sin ruta.
- [ ] **[Pendiente]** Smoke con servicios reales: no se pudo ejecutar en este workspace. Los puertos 2322 y 5000 no responden y falta el ZIP portable `dist/zon-mapas-cordoba-stage0-windows-x64.zip`. Debe correrse en la PC con los servicios levantados.
- [ ] **[Pendiente]** Integración posterior de los 7 alias y 59 respaldos GeoRef y la revisión de 515 localidades, diferida por decisión del usuario; sigue registrada en [stage-0-localidades.md](stage-0-localidades.md).

[Inventario de pendientes](pendientes.md) · [Plan de implementación](../PLAN-IMPLEMENTACION.md) · [Registro técnico de tareas](../odd/tasks/stage-3-real-maps.md) · [Stage 0](stage-0.md) · [Smoke test](../src/test/java/ar/zon/calculadora/smoke/RealMapsSmokeTest.java)
