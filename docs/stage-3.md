# Stage 3 - Mapas reales: adaptadores Photon y OSRM

**Estado: smoke nativo ejecutado contra Photon y OSRM del paquete portable; la variante Docker sigue pendiente en #7.** El checklist del plan no se marca como cerrado hasta resolver #3, #6 y #7.

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

## Cómo ejecutar el smoke

Hay dos formas de levantar los servicios. La ya ejecutada usa el paquete portable del Stage 0: descomprimir `dist/zon-mapas-cordoba-stage0-windows-x64-revision-localidades.zip` en `dist/zon-mapas-stage0` y arrancar cada servicio en una terminal, desde esa carpeta:

```powershell
.\runtime\java\bin\java.exe -Xmx768m -jar .\runtime\photon\photon-1.3.0.jar serve -data-dir .\data\photon -listen-ip 127.0.0.1 -listen-port 2322
.\runtime\osrm\osrm-routed.exe --algorithm mld --ip 127.0.0.1 --port 5000 -t 2 .\data\osrm\cordoba
```

La variante Docker sigue pendiente en #7: el archivo `deploy/docker/docker-compose.maps.yml` aún no está materializado; hay que crearlo desde el Anexo C del plan y ejecutar `docker compose -f deploy/docker/docker-compose.maps.yml up -d`. Photon escucha en `2322` y OSRM en `5000`. La prueba `RealMapsSmokeTest` está etiquetada como `smoke` y se omite por defecto. Para ejecutarla con endpoints por defecto:

```bash
mvn '-Dzon.smoke=true' '-Dtest=RealMapsSmokeTest' test
```

Para apuntar a otros endpoints:

```bash
mvn '-Dzon.smoke=true' '-Dphoton.baseUrl=http://host:2322' '-Dosrm.baseUrl=http://host:5000' '-Dtest=RealMapsSmokeTest' test
```

Las comillas evitan que PowerShell separe las propiedades con punto. En bash también funcionan.

### Reproducir las comprobaciones de la CLI

Los comandos `mvn` de arriba ejercitan los adaptadores por separado. Para repetir la corrida completa, crear estos tres archivos en un directorio temporal fuera del repositorio.

`input.csv` (el destino se fija en la configuración, no en el CSV):

```csv
personas;origen;transporte;combustible
2;Alta Gracia;auto;nafta
2;Villa Carlos Paz;auto;nafta
2;Cordoba;auto;nafta
```

`factors.properties`:

```properties
factor.auto.nafta.basis=vehicle_km
factor.auto.nafta.fuel_unit=l
factor.auto.nafta.consumption_per_100km=10.0
factor.auto.nafta.kgco2e_per_fuel_unit=2.0
factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
```

`app.properties` (configuración efectiva de las tres corridas; `factors.file` es la ruta absoluta al archivo anterior con `/` como separador):

```properties
output.format=csv
geocoder.mode=PHOTON
geo.maxCandidates=5
geo.cache.enabled=false
distance.mode=ROUTE
route.cache.enabled=false
distance.fallback=HAVERSINE
distance.decimals=4
destination.lat=-31.4201
destination.lon=-64.1888
photon.baseUrl=http://127.0.0.1:2322
osrm.baseUrl=http://127.0.0.1:5000
http.timeoutMs=3000
http.retries=0
http.maxConsecutiveFailures=20
healthcheck.enabled=true
factors.file=C:/ruta/al/temporal/factors.properties
```

Desde la raíz del repositorio, generar el jar con `mvn -o package -DskipTests` y ejecutar cada escenario con rutas absolutas a los archivos temporales (en Windows con `/` como separador):

```powershell
java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar --input C:/ruta/al/temporal/input.csv --output C:/ruta/al/temporal/report-<escenario>.csv --config C:/ruta/al/temporal/app.properties --force
```

Tres corridas, con un solo valor distinto entre ellas:

| Corrida | OSRM | `healthcheck.enabled` | `--output` | Resultado observado |
| --- | --- | --- | --- | --- |
| Nominal | arriba | `true` | `report-nominal.csv` | salida `0`, 3/3 `OK`, sin fallback; Villa Carlos Paz 36,5730 km |
| Fallback | detenido | `false` | `report-fallback.csv` | salida `0`, 3/3 `OK_FALLBACK_HAVERSINE` con `distance_fallback_reason=ROUTER_ERROR` |
| Salida 2 | detenido | `true` | `report-salida2.csv` | salida `2`, `SERVICE_UNAVAILABLE`, sin informe |

Después de la corrida con salida 2, `Test-Path C:/ruta/al/temporal/report-salida2.csv` debe devolver `False`. El health-check se ejecuta antes de abrir el informe (`Main.java:110-116`), así que esa corrida no crea ni sobrescribe ningún CSV; el nombre propio de salida evita confundirlo con el informe de las corridas anteriores. Borrar `report-salida2.csv` antes de la corrida si quedó de una ejecución previa.

En la corrida de fallback se detuvo solo OSRM (`Stop-Process` sobre el PID que escucha en `5000`) y se reinició al terminar; Photon siguió arriba en las tres.

Comprobaciones acordadas para el smoke: km de ruta plausibles contra la referencia (±10 %, referencia Córdoba–Villa Carlos Paz 36,37 km), fallback por fila al detener OSRM, salida 2 al detener un servicio en el arranque y el mismo flujo contra endpoints Docker parametrizados por `photon.baseUrl` y `osrm.baseUrl`. Al detener OSRM el motivo observado es `ROUTER_ERROR`; `ROUTE_NOT_FOUND` corresponde a un `NoRoute` devuelto por el servicio, no a una caída.

## Verificación y continuidad

- [x] `mvn -o verify`: 100 pruebas unitarias, 0 fallos, 2 omitidas (smoke) y 1 de integración. `mvn -o test`: el smoke queda omitido por defecto.
- [x] Las fixtures reales del Stage 0 ejercitan mapeo `[lon, lat]`, consulta con `q`/`limit`/`lat`/`lon`/`bbox`, 36370,9 m → 36,3709 km, selección determinista y respuestas sin ruta.
- [x] Smoke con servicios reales: `RealMapsSmokeTest` 2/2 con endpoints por defecto y con `-Dphoton.baseUrl` / `-Dosrm.baseUrl` explícitos. CLI completa sobre tres localidades con `geocoder.mode=PHOTON` y `distance.mode=ROUTE`: 3/3 correctas, Villa Carlos Paz 36,5730 km y sin fallback. Con OSRM detenido, 3/3 filas `OK_FALLBACK_HAVERSINE` con `distance_fallback_reason=ROUTER_ERROR`; con `healthcheck.enabled=true`, salida `2` y sin informe. Servicios levantados desde `dist/zon-mapas-cordoba-stage0-windows-x64-revision-localidades.zip`. La variante Docker queda en #7.
- [ ] **[Pendiente]** Integración posterior de los 7 alias y 59 respaldos GeoRef y la revisión de 515 localidades, diferida por decisión del usuario; sigue registrada en [stage-0-localidades.md](stage-0-localidades.md).

[Inventario de pendientes](pendientes.md) · [Plan de implementación](../PLAN-IMPLEMENTACION.md) · [Registro técnico de tareas](../odd/tasks/stage-3-real-maps.md) · [Stage 0](stage-0.md) · [Smoke test](../src/test/java/ar/zon/calculadora/smoke/RealMapsSmokeTest.java)
