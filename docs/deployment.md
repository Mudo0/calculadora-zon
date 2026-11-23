# Despliegue de ZON

## Decisión del Stage 0

La primera entrega se prepara como paquete portable para ejecutar en la PC del
cliente, con Java, Photon, OSRM y los datos de mapas incluidos. El launcher y la
distribución del paquete corresponden al Stage 5. La entrega inicial apunta a
Windows de 64 bits y cubre toda la provincia de Córdoba. La red vial de OSRM
se mantiene a escala provincial.

| Modo | Servicios de mapas |
| --- | --- |
| `BUNDLED` | ZON inicia y detiene sus procesos locales. Los servicios escuchan en loopback. |
| `EXTERNAL` | ZON usa las URL configuradas para servicios ya iniciados, incluidos servicios en un servidor futuro. |

Ambos modos usan los mismos contratos HTTP. El ciclo de vida de los servicios
queda fuera del motor de cálculo. Los adaptadores de la calculadora corresponden
al Stage 3.

## Cobertura regional de Photon

| Zona | Búsqueda habilitada |
| --- | --- |
| Toda la provincia | Localidad obligatoria. Incluye ciudades, pueblos y parajes con nombre, también en zonas de baja población. |
| Localidades con cobertura barrial | Localidad y barrio opcional. Para la primera medición se propone Córdoba capital, Río Cuarto, Villa María, San Francisco y Villa Carlos Paz. |
| Resto de las localidades | Solo localidad. Se usa su punto representativo como origen. |

El barrio se busca dentro de la localidad elegida. Si el barrio informado no
se encuentra, se usa el punto de la localidad y se indica la menor precisión
en el reporte. Si hay localidades homónimas, el departamento sirve para
distinguirlas. Si persiste la ambigüedad, se marca el origen para revisión.

Para generar el índice, seleccionar los lugares dentro del límite provincial
y conservar localidades nombradas y barrios nombrados de la lista urbana.
Excluir calles, numeraciones, edificios, comercios y demás puntos de interés.
La lista urbana es una decisión de cobertura revisable. Se amplía cuando haya
barrios identificables en los datos y consultas representativas que justifiquen
el espacio adicional.

Para medir el candidato, registrar cuántas localidades y barrios quedaron,
su distribución por departamento, el peso del índice en disco y comprimido,
la RAM, el arranque y la latencia. Probar un barrio conocido de capital, una
localidad pequeña de cada región de la provincia, un homónimo y un barrio
fuera de cobertura. Comparar la cobertura con el catálogo provincial de
localidades que se elija para la validación.

## Validación inicial

- Entorno de prueba: Windows 10 Pro x64, Java Temurin 21.0.13.1, 15,8 GiB de
  RAM total (2,8 GiB disponibles al relevarla) y 133,3 GiB libres en disco.
- OSRM v26.11.0: compilación Windows x64 de
  [e-kotov/osrm-binaries](https://github.com/e-kotov/osrm-binaries/releases/tag/v26.11.0).
  Se verificó el SHA-256 del archivo y se ejecutó `--version` en los cuatro binarios.
- Photon 1.3.0: se verificó el SHA-256 del
  [JAR oficial](https://github.com/komoot/photon/releases/tag/1.3.0) y su CLI
  arrancó con Java 21.
- OSRM usa el [extracto provincial de OpenStreetMap France](https://download.openstreetmap.fr/extracts/south-america/argentina/).
  El PBF probado pesa 41.723.966 bytes y su MD5 publicado coincide.
  La fecha de datos que informó OSRM fue 2026-11-23 00:53:29 UTC.
  Datos © OpenStreetMap contributors.
- OSRM nativo devolvió `Ok` y 36.370,9 m entre Córdoba y Villa Carlos Paz.
  La respuesta está en
  `src/test/resources/fixtures/osrm-cordoba-villa-carlos-paz.json`.
  Con 2 hilos, la extracción tomó unos 31 s y la partición y personalización,
  14,6 s. Los archivos generados ocuparon 382,5 MiB. La primera respuesta
  llegó a los 0,6 s, el p95 de 20 consultas HTTP locales fue de 3,2 ms y la
  memoria residente del servidor fue de 292,8 MiB.
  Para esta versión, se pasó el nombre base `cordoba` sin extensión a
  `osrm-partition`, `osrm-customize` y `osrm-routed`.
- Photon local devolvió Alta Gracia en `[-64.4338563, -31.6578916]` usando
  un índice piloto de tres localidades reales: Córdoba, Alta Gracia y
  Villa Carlos Paz. La respuesta está en
  `src/test/resources/fixtures/photon-alta-gracia.json`. Con un máximo
  de heap de 768 MiB, la primera respuesta llegó a los 5,5 s, el p95 de 20
  consultas locales fue de 18,1 ms y la memoria residente fue de 333,5 MiB.
  El índice piloto ocupó alrededor de 0,1 MiB.
- El índice nacional prearmado de Photon pesa alrededor de 4,07 GB comprimido.
  Para la prueba regional se descargó el
  [volcado JSON de Argentina](https://download1.graphhopper.com/public/south-america/argentina/index.html)
  de 1.054.299.047 bytes, con datos al 2026-11-21 23:59:58 UTC y MD5
  `190d9785d9065840cb9ce54fd0d65feb`. Se seleccionaron lugares por centroide
  dentro del rectángulo de prueba `[-66.0, -35.3, -61.5, -29.3]`
  (oeste, sur, este, norte). Es un límite de prueba para Córdoba y alrededores.
  El recorte amplio produjo 9.325.384 lugares y 14.006 MiB de JSON en
  401,7 s. Se interrumpió su importación para usar el índice piloto.
  El candidato provincial medido a continuación se generó con el filtro de
  localidades y barrios, independientemente de ese recorte amplio.

## Candidato provincial y tamaño del ZIP

El filtro usó la provincia declarada en el volcado (Córdoba o código AR-X)
y las categorías de localidades y barrios. Se seleccionaron 3.169 registros
de localidades y parajes, y 871 registros barriales. Los conteos barriales
antes de importar fueron Córdoba capital 674, Río Cuarto 58, Villa María 44,
San Francisco 35 y Villa Carlos Paz 60. Photon importó 4.038 documentos
de los 4.040 seleccionados.

- Índice de Photon: 2,0 MiB en disco y 1,2 MiB dentro del ZIP.
- ZIP de mapas Windows x64: 310,6 MiB, con Java Temurin 21 portable, Photon
  1.3.0, OSRM 26.11.0 y sus datos provinciales. Incluye instrucciones de prueba.
- Photon con Java portable: 7,8 s hasta la primera respuesta, 19,3 ms de
  p95 para 20 consultas de Nueva Córdoba y 359,7 MiB de memoria residente.
- OSRM en la carpeta portable devolvió 36.370,9 m para Córdoba a Villa Carlos Paz.
- En la prueba contra [GeoRef](https://www.argentina.gob.ar/georef), 449 de
  515 localidades coincidieron por nombre normalizado y proximidad al
  centroide (hasta 0,1° de diferencia por eje), con coincidencias en los
  26 departamentos. Las otras 66 requieren revisar nombres alternativos,
  localidades compuestas y posibles faltantes antes de dar por validada
  la cobertura de entrega.

## Criterios de salida

- Photon resuelve una localidad conocida de Córdoba y devuelve GeoJSON.
- OSRM devuelve `code: Ok` y metros de ruta para Córdoba a Villa Carlos Paz.
- Guardar un JSON real de cada servicio en `src/test/resources/fixtures/`.
- Registrar los tamaños, tiempos y consumo de memoria medidos.
- Validar ambos servicios en Windows objetivo, sin Internet, Docker, Java
  instalado ni permisos de administrador, usando Java incluido en la carpeta
  de prueba. Pendiente en una PC o VM limpia. Las mediciones anteriores se
  hicieron con Java ya instalado y consultas a loopback en la PC de desarrollo.

La comparación de OSRM con Docker se difiere porque la primera entrega usa
procesos nativos. Se podrá comparar con el mismo mapa y consultas cuando haya
un entorno de servidor candidato.
