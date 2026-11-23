package ar.zon.calculadora.core.emissions;

import java.util.Optional;

/** Aplica la fórmula de emisiones del plan para cada base de factor. */
public final class EmissionCalculator {

    private EmissionCalculator() {
    }

    /**
     * Calcula las emisiones de una fila.
     *
     * @param km distancia sin redondear
     * @param personas cantidad de personas del viaje
     * @param factor factor aplicable
     * @return emisiones con trazabilidad del factor
     */
    public static Optional<EmissionResult> calculate(double km, int personas, EmissionFactor factor) {
        if (factor == null) {
            return Optional.empty();
        }
        double kg = factor.basis() == FactorBasis.PASSENGER_KM
                ? km * personas * factor.kgPerPassengerKm()
                : switch (factor.fuelUnit()) {
                    case "l", "m3" -> km * factor.consumptionPer100Km() / 100.0 * factor.kgPerFuelUnit();
                    default -> throw new IllegalArgumentException("Unidad de combustible no soportada: " + factor.fuelUnit());
                };
        return Optional.of(new EmissionResult(kg, factor.key(), factor.source(), factor.placeholder()));
    }
}
