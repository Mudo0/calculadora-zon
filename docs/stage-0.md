# Stage 0 — Mapas locales para ZON

**Estado: en curso.** Photon y OSRM respondieron con datos reales desde la carpeta portable de prueba en Windows x64. El ZIP de mapas pesa **310,6 MiB**. Ya se revisaron los 66 casos de GeoRef. Queda validar los alias candidatos, decidir el respaldo y repetir la prueba en una máquina limpia sin conexión.

## Decisiones

| Tema | Decisión |
| --- | --- |
| Primera entrega | Paquete portable para Windows x64 que incluye Java 21, Photon, OSRM y los datos preparados. |
| Rutas | OSRM nativo con la red vial de toda la provincia de Córdoba. |
| Orígenes | Localidades y parajes en toda la provincia. Barrio opcional en Córdoba capital, Río Cuarto, Villa María, San Francisco y Villa Carlos Paz para el primer candidato. |
| Precisión | Si el barrio no se encuentra, usar la localidad y reflejar la menor precisión en el reporte. Usar el departamento para distinguir homónimos y marcar los casos ambiguos para revisión. |
| Servidor futuro | Mantener el modo `EXTERNAL` para apuntar a servicios ya iniciados mediante URL configurables. La comparación de OSRM nativo y Docker se difirió hasta contar con un entorno de servidor candidato. |

## Qué hicimos y qué quedó en el repo

1. Probamos OSRM 26.11.0 nativo con el mapa provincial. La ruta Córdoba–Villa Carlos Paz devolvió `Ok` y **36,37 km**.
2. Generamos un índice con Photon 1.3.0. El filtro seleccionó **3.169 registros de localidades y parajes** y **871 registros de barrios**. Photon importó 4.038 de los 4.040 seleccionados. El índice pesa **1,2 MiB comprimido**.
3. Probamos ambos servicios desde la carpeta portable usando el Java incluido para Photon. Photon devolvió resultados para Alta Gracia, Nueva Córdoba y localidades del interior. La comparación con GeoRef encontró **449 de 515** localidades por nombre normalizado y cercanía al centroide, con coincidencias en los 26 departamentos.
4. Actualizamos el plan, registramos los resultados y guardamos dos respuestas HTTP reales para las futuras pruebas de adaptadores. [Plan](../PLAN-IMPLEMENTACION.md) · [Mediciones y fuentes](deployment.md) · [Respuesta de Photon](../src/test/resources/fixtures/photon-alta-gracia.json) · [Respuesta de OSRM](../src/test/resources/fixtures/osrm-cordoba-villa-carlos-paz.json)

El ZIP local quedó en `dist/zon-mapas-cordoba-stage0-windows-x64.zip`, fuera de Git. Es un paquete de validación de los servicios con instrucciones de arranque manual.

## Qué se hizo en el código

| Código | Qué hace | Dónde quedó |
| --- | --- | --- |
| Java de ZON | El punto de entrada sigue mostrando `ZON Calculadora`. El Stage 0 no modificó la CLI ni agregó lógica al motor. | [Punto de entrada](../src/main/java/ar/zon/calculadora/cli/Main.java) |
| Java auxiliar para Photon | Leyó el volcado comprimido registro por registro. Seleccionó localidades y parajes de Córdoba y barrios cuyo municipio o localidad figuraba en la lista urbana. Quitó la geometría detallada de los registros seleccionados y conservó sus coordenadas para la búsqueda. Escribió el volcado filtrado que se importó con Photon. | Carpeta temporal de la PC de desarrollo. |
| Comandos de PowerShell | Prepararon el mapa de OSRM con el perfil de auto, arrancaron ambos servicios en loopback, hicieron las consultas HTTP, midieron el arranque y la latencia, contrastaron Photon con GeoRef y comprimieron Java portable, los ejecutables y los datos. | Ejecuciones de validación en la PC de desarrollo. |

En el código versionable, los dos JSON del apartado anterior son datos de prueba. Los adaptadores HTTP y el gestor de procesos locales todavía están pendientes en las etapas indicadas abajo.

## Arquitectura prevista

El motor pedirá coordenadas a través de `Geocoder` y distancias por carretera a través de `RouteCalculator`. En el **Stage 3**, los adaptadores HTTP implementarán esos puertos y consultarán Photon u OSRM mediante URL configuradas. Los JSON guardados sirven para verificar cómo interpretan las respuestas.

| Modo | Quién ejecuta los servicios | Cómo se comunica ZON |
| --- | --- | --- |
| `BUNDLED` | El gestor de procesos locales los inicia y detiene. Se implementa en el **Stage 5**. | HTTP hacia la misma PC, por loopback. |
| `EXTERNAL` | Los servicios ya están iniciados, por ejemplo en un servidor. | HTTP hacia las URL configuradas. |

Los dos modos comparten los adaptadores y los contratos HTTP. El gestor de procesos y el empaquetado quedan fuera del motor de cálculo.

## Pendientes para cerrar el Stage 0

- [x] Revisar los 66 casos y repetir las 515 consultas contra el índice actual. Se conservaron las 449 coincidencias y se documentaron 9 alias candidatos y 57 propuestas de respaldo. [Revisión por localidad](stage-0-localidades.md)
- [ ] Validar los alias candidatos y acordar el catálogo de respaldo para los casos sin coincidencia verificada. Tras integrarlos en el Stage 3, volver a probar la resolución de las 515 localidades.
- [ ] Probar ambos servicios en una PC o VM limpia con Windows x64, con la red desactivada, sin Java ni Docker instalados y con una cuenta sin permisos de administrador. Usar el Java incluido y registrar el resultado.
