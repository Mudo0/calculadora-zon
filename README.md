# ZON Calculadora

Calculadora de distancias, consumo y emisiones para encuestas de transporte.
El proyecto está en su inicialización. El JAR arranca y muestra el nombre de la aplicación.

## Compilar y probar

Requiere JDK 21 y Maven. Desde la raíz del repositorio:

```powershell
mvn verify
java -jar target/zon-calculadora-0.1.0-SNAPSHOT.jar
```

`mvn test` corre las pruebas unitarias. `mvn verify` empaqueta el JAR y ejecuta también las pruebas de integración cuando existan.

## Estructura

- `cli` contiene el punto de entrada.
- `core` aloja las reglas de cálculo.
- `infra` aloja los adaptadores.
- `config/` contiene ejemplos para las próximas etapas.
- [Plan de implementación](PLAN-IMPLEMENTACION.md) y [documentación técnica](docs/README.md).
