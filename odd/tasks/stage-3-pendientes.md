# Stage 3 - Pendientes posteriores a la PR #8
Rama de integración: stage-3-real-maps | Base: 21c5eac | Entrega: una PR por issue, apiladas sobre stage-3-real-maps; sin merge.
Test runner: `mvn -o test` (unit), `mvn -o verify` (integración). JDK 21 requerido.

## Specs

S1 - Issue #4, hardening del cliente HTTP (verbatim de `docs/pendientes.md`):
1. "`HttpJsonClient` llama a `guard.recordSuccess()` antes de `MAPPER.readTree(...)`. Una respuesta `200` con cuerpo inválido cuenta como éxito y reinicia el contador del disyuntor: el guardián nunca llega a abrirse aunque todas las respuestas estén mal formadas."
2. "Las respuestas `3xx` y `4xx` lanzan `ServiceCallException` sin reintentar y sin llamar a `guard.recordFailure()`, así que tampoco contribuyen a abrir el disyuntor."
3. "El constructor no valida `baseUrl`, `timeoutMs > 0` ni `retries >= 0`."
4. "No hay tests para esas tres ramas."
Decisión: `4xx` es una respuesta determinista del servicio (incluye `NoRoute` de OSRM), no un fallo de guardia; `5xx` y errores de transporte sí cuentan. Se documenta en el código y se fija con tests.

S2 - Issue #9, `distance_straight_km` en modo `BOTH` (verbatim de `docs/pendientes.md`):
"Una prueba de salida CSV que verifique, con `distance.mode=BOTH`, que `distance_km` (vial, de OSRM) y `distance_straight_km` (recta, de Haversine) aparecen con valores distintos." Cierre: "Prueba CSV verde con ambas columnas y decisión documentada sobre si hacía falta cambiar producción."

S3 - Issue #5, aviso de CVE sobre Jackson (verbatim de `docs/pendientes.md`):
"Comprobar si los CVE que mencionó un revisor con correcciones en `2.21.6` y `2.21.8` son reales y aplicables a este proyecto; solo entonces decidir si se sube la versión." Cierre: "decisión documentada con evidencia, con o sin cambio de versión."

S4 - Diferidos en este bloque: #3 (smoke con servicios reales: puertos 2322/5000 sin respuesta y falta el ZIP portable), #6 (alias y respaldos GeoRef en 515 localidades, decidido diferir por el usuario) y #7 (comparación OSRM nativo/Docker: no existe `deploy/docker/`). Ninguno se cierra ni se marca completo sin su evidencia.

S5 - Convención: cada issue tiene su propia PR con el nombre del stage en el título, `Relacionado con #N (sin cierre automático)`, plantilla del repo y contexto de cadena. Push y creación de PRs autorizados; sin merge, release ni cierre de issues. TDD con RED observado antes de cada cambio de comportamiento.

## Tasks

- T1 — S1 — #4 hardening de `HttpJsonClient` con RED -> GREEN — rama `fix/stage3-http-hardening`, PR #10 (base `stage-3-real-maps`), commit `0200b22` — estado: completo, `mvn -o verify` en verde (110 tests, 2 omitidos, 1 IT)
- T2 — S2 — #9 prueba CSV de `distance_straight_km` en BOTH — rama `test/stage3-both-straight-km`, PR #11, commit `65bf9b7` — estado: completo, `mvn -o verify` en verde (111 tests, 2 omitidos, 1 IT); sin cambios en producción
- T3 — S3 — #5 verificación del aviso de CVE y decisión documentada — rama `chore/stage3-jackson-cve`, PR propia, commit `81182b0` — estado: completo en documento; sin cambios en `pom.xml`

## Log

L1 (2026-11-25): "dale dejemos explicito que no se cierra la etapa, y luego abrimos otras PRs para solucionar los issues pendientes y terminar el stage 3" y "bueno esta bien, me gustaria que cada issue tenga su propia PR".

L2 (2026-11-25): Se actualizó la descripción de la PR #8 con el aviso explícito `Estado: Stage 3 NO cerrado`, verificado por API en UTF-8. No se tocó código ni se cerraron issues. Se elige una PR por issue apilada sobre `stage-3-real-maps` porque #8 sigue abierta. El skill `branch-pr` exige `status:approved` y shellcheck, reglas que este repositorio no aplica; se sigue la plantilla del repo.

L3 (2026-11-25): Bloque ejecutable aquí: #4, #9 y #5. Quedan para otro bloque #3, #6 y #7 por falta de servicios, datos o entorno. El checkbox de Stage 3 en `PLAN-IMPLEMENTACION.md` permanece sin marcar.

L4 (2026-11-25): T1 completo. RED observado con `mvn -o '-Dtest=HttpJsonClientTest' test`: 7 tests, 1 fallo en `rejectsInvalidConstructorArguments`. GREEN con validación de argumentos y `recordSuccess()` posterior al parseo: 7/7. `mvn -o verify`: BUILD SUCCESS, 110 tests (2 omitidos), 1 IT. Commit `0200b22`, PR #10 abierta contra `stage-3-real-maps`, sin merge. La vinculación del issue es `Relacionado con #4 (sin cierre automático)`, igual que la convención ya usada en #8; no se usa `Closes` para no cerrar issues sin autorización. Las PRs del repo no tienen labels `type:*`, así que no se agregan.

L5 (2026-11-25): T2 completo. `Stage3CliTest.bothModeWritesDifferentRoadAndStraightDistances` ejecuta la CLI con fixtures, escribe CSV real y comprueba `distance.mode=BOTH`, `distance_km` y `distance_straight_km` no vacíos y distintos. El test pasó en el primer intento (5/5): no hubo RED porque el comportamiento ya era correcto; es una prueba de regresión, no un cambio de comportamiento, y no hacía falta tocar producción. `mvn -o verify`: BUILD SUCCESS, 111 tests (2 omitidos), 1 IT.

L6 (2026-11-25): T3 completo como documento. Fuentes oficiales consultadas: release notes de Jackson 2.21.6 y 2.21.8 y advisories `GHSA-q4xh-88c3-wmh7`, `GHSA-wjgm-6hv5-3cvf`, `GHSA-gx83-3vf8-gh7j`. Los CVE son reales y `2.21.5` está afectada, pero las vías requieren `readValue` typed a `Duration`/`Path` o polimorfismo; el código solo usa `readTree`. Decisión: no subir la versión. `2.21.6`/`2.21.8` tampoco están en la caché offline, así que un bump rompería `mvn -o verify`. Evidencia en `docs/jackson-cve-verificacion.md` y `docs/pendientes.md`. Sin cambios en código ni dependencias.
