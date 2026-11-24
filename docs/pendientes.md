# Inventario de pendientes

Este archivo es el lugar único para consultar qué falta, por qué está pendiente y cómo cerrarlo. Se actualiza cuando un pendiente se resuelve o aparece uno nuevo; el detalle de decisiones por etapa sigue en cada documento de etapa.

Fecha: 2026-11-24. Rama: `stage-3-real-maps`. Tests: `mvn -o verify` en verde con 97 pruebas unitarias y 1 de integración; el smoke con servicios reales está omitido.

Issues de seguimiento: [P1 smoke real #3](https://github.com/Mudo0/calculadora-zon/issues/3), [P2 hardening HTTP #4](https://github.com/Mudo0/calculadora-zon/issues/4), [P2 CVE Jackson #5](https://github.com/Mudo0/calculadora-zon/issues/5), [P3 GeoRef #6](https://github.com/Mudo0/calculadora-zon/issues/6) y [P3 OSRM/Docker #7](https://github.com/Mudo0/calculadora-zon/issues/7). El plan no marca Stage 3 como completado hasta que estén resueltos.

Orden de prioridad: primero lo que bloquea cerrar Stage 3 (P1), luego la deuda técnica conocida (P2) y finalmente lo diferido por decisión (P3).

---

## P1 — Smoke con servicios reales (bloquea el cierre de Stage 3) — [#3](https://github.com/Mudo0/calculadora-zon/issues/3)

**Qué falta.** Ejecutar contra Photon y OSRM reales las comprobaciones que hoy solo se hicieron con fixtures servidas por un stub local:

1. Correr `RealMapsSmokeTest` con Photon en `2322` y OSRM en `5000`.
2. Correr el flujo completo de la CLI con `geocoder.mode=PHOTON` y `distance.mode=ROUTE` sobre varias localidades de Córdoba.
3. Verificar los cuatro criterios del plan: km plausibles contra la referencia Córdoba–Villa Carlos Paz (36,37 km, tolerancia sugerida ±10 %), fallback por fila al detener OSRM, salida 2 al detener un servicio en el arranque y el mismo flujo apuntando a endpoints Docker parametrizados por `photon.baseUrl` / `osrm.baseUrl`.
4. Marcar el checkbox de Stage 3 en `PLAN-IMPLEMENTACION.md` solo cuando todo lo anterior pase.

**Por qué.** En este workspace los puertos `2322` y `5000` no responden y falta el paquete portable `dist/zon-mapas-cordoba-stage0-windows-x64.zip`. Además, no existe el directorio `deploy/docker/`: el archivo `docker-compose.maps.yml` está descrito en el Anexo C del plan pero no está materializado en el repositorio. Las pruebas actuales demuestran que los adaptadores interpretan bien las respuestas, pero no que hablen con los servicios reales. El plan exige ese smoke para dar por cerrada la etapa, por eso el checkbox sigue sin marcar.

**Cómo hacerlo.** En la PC con los servicios disponibles, levantar Photon y OSRM con el ZIP portable según `docs/stage-0.md`, o materializar el compose desde el Anexo C y ejecutar `docker compose -f deploy/docker/docker-compose.maps.yml up -d`. Luego:

```powershell
# Smoke etiquetado (por defecto se omite)
mvn '-Dzon.smoke=true' '-Dtest=RealMapsSmokeTest' test

# Otros endpoints
mvn '-Dzon.smoke=true' '-Dphoton.baseUrl=http://host:2322' '-Dosrm.baseUrl=http://host:5000' '-Dtest=RealMapsSmokeTest' test
```

Las comillas son obligatorias en PowerShell: sin ellas separa las propiedades con punto y Maven falla con `Unknown lifecycle phase .smoke=true`. Para la prueba de fallback, detener solo OSRM y confirmar que las filas pasan a `OK_FALLBACK_HAVERSINE` con `distance_fallback_reason=ROUTE_NOT_FOUND`; para la salida 2, detener un servicio antes de arrancar con `healthcheck.enabled=true`.

**Cierre:** smoke verde, cuatro criterios verificados y checkbox actualizado.

---

## P2 — Hardening del cliente HTTP (advisories de la revisión de T1) — [#4](https://github.com/Mudo0/calculadora-zon/issues/4)

**Qué falta.** Cuatro hallazgos informativos que la revisión nativa de T1 registró como advisories y que no se corrigieron para no reabrir un recibo ya aprobado:

1. `HttpJsonClient` llama a `guard.recordSuccess()` antes de `MAPPER.readTree(...)`. Una respuesta `200` con cuerpo inválido cuenta como éxito y reinicia el contador del disyuntor: el guardián nunca llega a abrirse aunque todas las respuestas estén mal formadas.
2. Las respuestas `3xx` y `4xx` lanzan `ServiceCallException` sin reintentar y sin llamar a `guard.recordFailure()`, así que tampoco contribuyen a abrir el disyuntor.
3. El constructor no valida `baseUrl`, `timeoutMs > 0` ni `retries >= 0`.
4. No hay tests para esas tres ramas.

**Por qué.** No bloquearon la aprobación de T1, pero son comportamiento real bajo fallo y quedaron documentados en `odd/tasks/stage-3-real-maps.md` (L3). La revisión ya quemó su autoridad para ese candidato: corregirlo crea un candidato nuevo, no reabre el antiguo.

**Cómo hacerlo.** Un test RED por rama (cuerpo `200` no JSON, respuesta `404`, constructor con valores inválidos), después el cambio mínimo en `src/main/java/ar/zon/calculadora/infra/http/HttpJsonClient.java`: registrar éxito solo cuando el parseo termine bien, decidir explícitamente si `4xx` cuenta como fallo de guardia, y validar argumentos al construir. Suite completa al final y nueva unidad de revisión porque toca resiliencia.

**Cierre:** ramas cubiertas por tests, `mvn -o test` en verde y decisión registrada sobre el tratamiento de `4xx`.

---

## P2 — Verificar el aviso de CVE sobre `jackson-databind` 2.21.5 — [#5](https://github.com/Mudo0/calculadora-zon/issues/5)

**Qué falta.** Comprobar si los CVE que mencionó un revisor con correcciones en `2.21.6` y `2.21.8` son reales y aplicables a este proyecto; solo entonces decidir si se sube la versión.

**Por qué.** Fue un reclamo tardío de un revisor que no fue admitido en el resultado final de la revisión nativa. No hay fuente verificada en el repositorio y la versión `2.21.5` está fijada deliberadamente como dependencia nueva de riesgo 5. Cambiarla sin evidencia sería una modificación de dependencia sin justificación.

**Cómo hacerlo.** Consultar la base de datos oficial de vulnerabilidades (GitHub Advisory Database o NVD) y el registro de cambios de Jackson para `2.21.5` → `2.21.8`. Si aplica, subir a la menor versión corregida que esté disponible, ejecutar `mvn -o test` y someter el cambio a revisión por ser dependencia. Si no aplica, dejar constancia del descarto con la fuente consultada.

**Cierre:** decisión documentada con evidencia, con o sin cambio de versión.

---

## P3 — Integración de alias y respaldos GeoRef (diferida por el usuario) — [#6](https://github.com/Mudo0/calculadora-zon/issues/6)

**Qué falta.** Integrar los siete alias aceptados con su departamento y los 59 centroides de GeoRef como respaldo, y repetir las 515 consultas de localidades.

**Por qué.** El usuario decidió dejarlo para después. El ZIP portable que se usó en Stage 0 es anterior a esa decisión, así que no sirve para validar la integración. Stage 3 entregó los adaptadores HTTP; la resolución de nombres alternativos y homónimos queda fuera de su alcance.

**Cómo hacerlo.** Tomar la tabla y la decisión de `docs/stage-0-localidades.md` como fuente. Agregar la resolución por alias normalizado con departamento y el respaldo por centroide cuando Photon no devuelva resultado; conservar el ID de GeoRef. Los casos compuestos o ambiguos deben pedir el departamento o marcarse para revisión. Escribir tests sobre las 515 localidades y verificar en especial homónimos y localidades censales compuestas.

**Cierre:** cada nombre y departamento resuelve el origen esperado y los casos ambiguos quedan marcados.

---

## P3 — Comparación OSRM nativo contra Docker y chequeo en PC limpia — [#7](https://github.com/Mudo0/calculadora-zon/issues/7)

**Qué falta.** Comparar OSRM nativo con el contenedor en un entorno de servidor candidato y repetir el smoke en una PC limpia.

**Por qué.** La comparación se difirió en Stage 0 por falta de entorno de servidor candidato; el Anexo C describe el compose pero no hay archivos en `deploy/docker/`.

**Cómo hacerlo.** Materializar `deploy/docker/docker-compose.maps.yml` y `.env.maps.example` desde el Anexo C, levantar ambos servicios, medir las mismas rutas y documentar diferencias o la decisión de no usar Docker en `docs/deployment.md`.

**Cierre:** comparación registrada o decisión de descarte documentada.

---

## Texto para la descripción del PR

> Implementa Stage 3: adaptadores reales de Photon y OSRM cableados en la CLI, con cachés, reintentos, disyuntor y health-check. `mvn -o verify` en verde (97 pruebas unitarias, 2 omitidas, 1 de integración) y smoke del jar con fixtures servidas por un stub local.
>
> **Pendiente antes de cerrar el checklist de Stage 3:** el smoke contra servicios reales. En este workspace los puertos `2322`/`5000` no responden, falta el ZIP portable del Stage 0 y el compose del Anexo C no está materializado. Motivos, pasos y criterios de cierre en `docs/pendientes.md`.
>
> Seguimiento posterior: [#3] smoke real, [#4] hardening del cliente HTTP, [#5] verificación del aviso de CVE sobre `jackson-databind` 2.21.5, [#6] integración de alias y respaldos GeoRef y [#7] comparación OSRM/Docker. Ninguno se cierra con este PR.

## Entrega y checklist

Los cinco pendientes tienen issue propio (#3 a #7) con el mismo contenido: qué falta, por qué y cómo cerrarlo. La rama `stage-3-real-maps` se publica y la PR enlaza esos issues como relacionados, sin cerrarlos. El checkbox de Stage 3 en `PLAN-IMPLEMENTACION.md` se marca únicamente cuando estén resueltos todos los pendientes; el smoke P1 es condición necesaria.

[Índice de documentación](README.md) · [Stage 3](stage-3.md) · [Registro de tareas](../odd/tasks/stage-3-real-maps.md) · [Plan](../PLAN-IMPLEMENTACION.md)
