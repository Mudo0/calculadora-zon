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

- T1 — S1 — #4 hardening de `HttpJsonClient` con RED -> GREEN — rama `fix/stage3-http-hardening`, PR propia — estado: pendiente
- T2 — S2 — #9 prueba CSV de `distance_straight_km` en BOTH — rama `test/stage3-both-straight-km`, PR propia — estado: pendiente
- T3 — S3 — #5 verificación del aviso de CVE y decisión documentada — rama `chore/stage3-jackson-cve`, PR propia — estado: pendiente

## Log

L1 (2026-11-25): "dale dejemos explicito que no se cierra la etapa, y luego abrimos otras PRs para solucionar los issues pendientes y terminar el stage 3" y "bueno esta bien, me gustaria que cada issue tenga su propia PR".

L2 (2026-11-25): Se actualizó la descripción de la PR #8 con el aviso explícito `Estado: Stage 3 NO cerrado`, verificado por API en UTF-8. No se tocó código ni se cerraron issues. Se elige una PR por issue apilada sobre `stage-3-real-maps` porque #8 sigue abierta. El skill `branch-pr` exige `status:approved` y shellcheck, reglas que este repositorio no aplica; se sigue la plantilla del repo.

L3 (2026-11-25): Bloque ejecutable aquí: #4, #9 y #5. Quedan para otro bloque #3, #6 y #7 por falta de servicios, datos o entorno. El checkbox de Stage 3 en `PLAN-IMPLEMENTACION.md` permanece sin marcar.
