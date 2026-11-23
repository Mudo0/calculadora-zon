# ZON Calculadora

Calculadora de distancias, consumo y emisiones para encuestas de transporte.

## Requisitos y compilación

Requiere JDK 21 y Maven.

```powershell
mvn test
mvn verify
java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar
```

`mvn test` corre las pruebas unitarias; `mvn verify` empaqueta el JAR y ejecuta también las pruebas de integración cuando existan.

## Estructura

- `cli`: punto de entrada y composición de adaptadores.
- `core`: reglas de cálculo y puertos; no depende de CLI, archivos ni servicios de mapas.
- `infra`: adaptadores de configuración, archivos y mapas.
- `config/`: ejemplos de configuración y de factores.
- `docs/`: documentación técnica.

## Estado

Incremento 1, etapa 1: núcleo puro y artefactos de bootstrap. La CLI completa, los adaptadores CSV/XLSX y los servicios de mapas se agregan en etapas posteriores del [plan de implementación](PLAN-IMPLEMENTACION.md).

Los factores de emisión incluidos son **ficticios**, no cifras reales. El cliente debe reemplazarlos con una fuente oficial antes de usar los informes.

Los datos de mapas son © OpenStreetMap contributors (ODbL).
