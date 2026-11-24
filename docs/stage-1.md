# Stage 1 — Núcleo de cálculo de ZON

**Estado: implementado e integrado en `main`** mediante el [PR #1](https://github.com/Mudo0/calculadora-zon/pull/1). Esta etapa agregó el motor que procesa una fila de encuesta y resume los resultados del lote. La verificación de la etapa pasó las 48 pruebas unitarias.

## Decisiones

| Tema | Decisión |
| --- | --- |
| Plataforma | Java 21 y Maven, con JUnit y AssertJ para las pruebas. |
| Entrada al motor | Una fila con campos mapeados, un destino resuelto y una configuración validada. |
| Cálculo | Distancia Haversine o distancia obtenida del puerto de rutas, según la configuración. Se calculan consumo y emisiones cuando corresponden al transporte y al factor. |
| Factores de emisión | Se cargan desde propiedades. Los valores de ejemplo son ficticios y el resumen advierte cuando se usan. Los factores incompletos, negativos o no finitos se rechazan. |

## Qué quedó en el código

| Componente | Responsabilidad |
| --- | --- |
| `CalculationConfig` y `ConfigValidator` | Modelan la configuración y validan modos, rangos y coordenadas del evento. |
| `ColumnMapping`, `RowRules` y `FuelResolver` | Mapean encabezados, normalizan valores y aplican las reglas de transporte, combustible y cantidad de personas. |
| `RowProcessor` | Valida la fila, resuelve el origen, elige la distancia y devuelve el resultado o el error de la fila. |
| `HaversineCalculator`, `ConsumptionCalculator` y `EmissionCalculator` | Calculan distancia en línea recta, consumo y emisiones. |
| `SummaryAggregator` | Acumula los totales, los estados de las filas y el aviso por factores ficticios. |
| `PropertiesConfigLoader` y `PropertiesEmissionFactorProvider` | Adaptan los archivos de propiedades a los modelos y puertos del núcleo. |

## Arquitectura

El núcleo depende de Java y de sus propios tipos. Consulta ubicaciones a través de `Geocoder`, rutas a través de `Router` y factores a través de `EmissionFactorProvider`. Los adaptadores viven en `infra`. Las pruebas del núcleo usan implementaciones de prueba de los puertos, por lo que pueden ejecutarse sin mapas ni servicios externos.

## Verificación y continuidad

- [x] `mvn -B -ntp verify` pasó las **48 pruebas unitarias** del Stage 1 sobre el `main` que incorporó el PR. Esa versión todavía no tenía pruebas de integración.
- [x] El review agregó casos para coordenadas no finitas y factores incompletos o inválidos.
- [ ] El **Stage 2** conecta el núcleo con la lectura de CSV, el informe y la CLI. Su revisión se sigue en el [PR #2](https://github.com/Mudo0/calculadora-zon/pull/2).
- [ ] El **Stage 3** conecta los puertos a Photon y OSRM e incorpora los alias y respaldos definidos en el Stage 0.

[Plan de implementación](../PLAN-IMPLEMENTACION.md) · [Registro técnico de tareas](../odd/tasks/stage-1-project-core.md) · [Stage 0](stage-0.md)
