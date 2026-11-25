package ar.zon.calculadora.core.model;

import ar.zon.calculadora.core.distance.DistanceResult;
import ar.zon.calculadora.core.emissions.ConsumptionResult;
import ar.zon.calculadora.core.emissions.EmissionResult;
import ar.zon.calculadora.core.errors.ErrorCode;

/** Resultado final de una fila del informe. */
public record RowResult(int number, RowStatus status, ErrorCode error, NormalizedTrip trip,
                        DistanceResult distance, ConsumptionResult consumption, EmissionResult emission,
                        String originLabel, int candidateCount, String fallbackReason) {

    /**
     * Crea un resultado sin datos de geocodificacion ni de respaldo.
     *
     * @param number numero de fila
     * @param status estado de la fila
     * @param error codigo de error, o {@code null}
     * @param trip viaje normalizado, o {@code null}
     * @param distance distancia calculada, o {@code null}
     * @param consumption consumo calculado, o {@code null}
     * @param emission emision calculada, o {@code null}
     */
    public RowResult(int number, RowStatus status, ErrorCode error, NormalizedTrip trip,
                     DistanceResult distance, ConsumptionResult consumption, EmissionResult emission) {
        this(number, status, error, trip, distance, consumption, emission, "", 0, "");
    }
}
