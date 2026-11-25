# Stage 4 — Exportación de encuestas CSV y Excel

El Stage 4 agrega lectura y escritura de XLSX al flujo de encuestas de movilidad de ZON. Permite que ITEC abra el informe directamente en Excel y que el mismo lote, procesado desde CSV o XLSX con la misma configuración, arroje los mismos resultados. El alcance y los criterios de aceptación están en el [plan de implementación](../PLAN-IMPLEMENTACION.md#stage-4---xlsx-support-and-parity).

## Del archivo de entrada al informe

| Componente | Qué hace |
| --- | --- |
| `Main` | Selecciona el lector por extensión. Con `output.format=auto`, también selecciona el escritor por extensión. Entrada y salida pueden tener formatos distintos. |
| `CsvRowReader` y `XlsxRowReader` | Leen las respuestas de forma incremental, reutilizan el mapeo de columnas y conservan los valores originales para la hoja **Datos**. El lector XLSX usa el número de fila de la hoja. |
| `CalculateSurveyBatchUseCase` | Procesa las filas con las reglas del motor y entrega al informe cada respuesta junto con su resultado. Calcula los totales del lote. |
| `XlsxReportWriter` | Escribe las hojas con FastExcel. Usa temporales para generar el informe incrementalmente y publica el archivo final cuando termina. Respeta `--force` para reemplazar una salida existente. |

En XLSX, `input.xlsx.sheet` admite un nombre de hoja o un índice desde 1. `input.headerRow` indica el número de fila del encabezado. Los valores predeterminados son la primera hoja y la primera fila. Las filas completamente vacías se omiten. Una celda numérica con el valor `2` y un CSV que trae `2,0` representan la misma cantidad de personas.

## El Excel que abre ITEC

| Hoja | Contenido y uso |
| --- | --- |
| **Resumen** | Es la primera hoja. Destaca respuestas, distancia total y emisiones estimadas. Incluye conteos de respuestas correctas, omitidas, con error y con distancia de respaldo. Si se usaron factores de prueba, muestra una advertencia. |
| **Datos** | Conserva las columnas de la encuesta. Traduce los encabezados conocidos y mantiene los encabezados adicionales tal como llegaron. **Fila de origen** permite cruzar cada respuesta con **Cálculos**. |
| **Cálculos** | Muestra resultados y errores por respuesta con encabezados y estados en español. Distancia, consumo y emisiones se guardan como números para poder sumarlos y filtrarlos en Excel. |

**Datos** y **Cálculos** tienen filtros, encabezados y primera columna fijos, y anchos ajustados. Los estados en **Cálculos** se distinguen por texto y color. El diseño y la traducción están en el exportador, por lo que se aplican a los nuevos informes. La salida CSV conserva sus encabezados técnicos.

El **Resumen** incluye dos gráficos de barras en español, uno de respuestas por estado y otro de emisiones por transporte. Los gráficos se incrustan como imágenes. El desglose de emisiones suma los valores redondeados que aparecen en **Cálculos**.

Las distancias se expresan en **km** y las emisiones en **kg CO₂e**. La unidad de consumo viene del factor aplicado. La configuración de ejemplo usa **L** para nafta y gasoil y **m³** para GNC. **Cálculos** conserva los valores como números y muestra la unidad en una columna propia. **Resumen** suma los consumos publicados por unidad. Una fila sin consumo aplicable, como el colectivo con factor por pasajero-kilómetro, no aporta a esos subtotales.

## Modelos de vehículos

El Stage 4 admite una columna opcional `modelo_vehiculo` en CSV o XLSX. Si viene en el archivo, aparece como **Modelo del vehículo** en **Datos**. La muestra [input-questionnaire.csv](../samples/input-questionnaire.csv) incluye dos modelos ficticios. Está inspirada en el [formulario de transporte](samples/cuestionario-transporte%283%29.html) y usa un tramo por respuesta, de acuerdo con el contrato actual del motor.

El efecto del modelo en las emisiones queda para una etapa posterior. Hoy el motor selecciona factores por tipo de transporte y combustible. Para afinar el cálculo por modelo, ITEC tendría que definir qué campos puede aportar la encuesta, por ejemplo marca, modelo, año y motorización, y qué catálogo respalda el consumo o factor por variante. Una vez disponible el catálogo, el cálculo podría usar el factor de la variante identificada y recurrir al factor general cuando falte una coincidencia. El informe debería indicar cuál se aplicó y su fuente.

## Validación

- `XlsxParityIT` verifica resultados equivalentes desde CSV y XLSX, lectura de una hoja y un encabezado configurados, datos numéricos, filas vacías, encabezados en español y conservación del modelo opcional.
- La prueba pasó con Java 21 y un JAR armado manualmente. También verificó XLSX → XLSX con `java -jar` y XLSX → CSV. La muestra arrojó 52,30 km y 5,12 kg CO₂e con factores de prueba en ambos formatos.
- Queda por verificar el empaquetado oficial con `mvn verify`. En esta sesión Maven no pudo resolver `maven-failsafe-plugin:3.5.5` desde la caché y el acceso a Maven Central falló con `Permission denied: getsockopt`.
