package ar.zon.calculadora.core.model;

import ar.zon.calculadora.core.distance.DistanceResult;
import ar.zon.calculadora.core.emissions.ConsumptionResult;
import ar.zon.calculadora.core.emissions.EmissionResult;
import ar.zon.calculadora.core.errors.ErrorCode;

/** Resultado final de una fila del informe. */
public record RowResult(int number, RowStatus status, ErrorCode error, NormalizedTrip trip,
                        DistanceResult distance, ConsumptionResult consumption, EmissionResult emission) {
}
