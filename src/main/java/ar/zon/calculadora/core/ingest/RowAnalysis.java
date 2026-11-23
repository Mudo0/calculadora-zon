package ar.zon.calculadora.core.ingest;

import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.model.FuelType;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.model.TransportMode;

/** Resultado de la validación inicial de una fila. */
public record RowAnalysis(TransportMode transporte, RowStatus status, ErrorCode error,
                          int personas, FuelType fuel, boolean fuelInferred) {
}
