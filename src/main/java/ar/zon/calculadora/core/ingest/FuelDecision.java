package ar.zon.calculadora.core.ingest;

import ar.zon.calculadora.core.model.FuelType;

/** Combustible aplicado a una fila y si fue inferido. */
public record FuelDecision(FuelType fuel, boolean inferred) {
}
