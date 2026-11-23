# Deltas del PRD y los ADR para el Incremento 1

El PRD se escribió alrededor del costo de combustible. Este plan los reemplaza:

| Ítem | Decisión del plan |
|---|---|
| RF-COM-02, RF-COM-06, RF-COM-07 e informes con costos | Se reemplazan por emisiones (`kgCO2e`), factor de emisión usado y totales de emisiones. |
| RF-COM-05 (caminar/bici/EV vacío o cero) | Caminar/bici: `SKIPPED_NON_MOTORIZED`. EV: fuera de alcance (transporte desconocido). Transporte público: factores configurables. |
| RF-DIS-06 (viaje ida y vuelta) | Viaje de ida solamente. |
| RNF-03 / DoD 8 (500.000 filas) | Volumen variable (decenas a decenas de miles). Streaming obligatorio; pruebas sintéticas de 5.000 y 50.000 filas sin OOM. 500.000 no es una meta. |
| Q-06 (precio del combustible) | No aplicable. |
| DoD 5 (cálculo manual del costo) | Se reemplaza por casos manuales de consumo y emisiones con factores ficticios. |
| ADR 0002, "Confirmación" #2 (prueba de 500.000 filas) | Se reemplaza por las pruebas de 5.000 y 50.000 filas. Agregar un apéndice corto al ADR 0002. |
| ADR 0002, etapa 1 (generar la plantilla de encuesta) | No incluido en el Incremento 1. Este plan solo cubre lectura de respuestas y escritura del informe. |
| ADR 0001 (mantener contenedores Docker o procesos locales) | El despliegue en servidor puede usar Docker o procesos nativos; Docker es opcional. Registrar ambos modos. |
