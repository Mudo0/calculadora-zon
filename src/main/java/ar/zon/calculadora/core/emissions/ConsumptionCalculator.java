package ar.zon.calculadora.core.emissions;

/** Aplica la fórmula de consumo del plan para cada base de factor. */
public final class ConsumptionCalculator {

    private ConsumptionCalculator() {
    }

    /**
     * Calcula el consumo de una fila.
     *
     * @param km distancia sin redondear
     * @param factor factor aplicable
     * @return consumo, o una nota indicando que no aplica
     */
    public static ConsumptionResult calculate(double km, EmissionFactor factor) {
        if (factor.basis() == FactorBasis.PASSENGER_KM) {
            return new ConsumptionResult(null, "", "consumption_not_applicable");
        }
        double amount = km * factor.consumptionPer100Km() / 100.0;
        return new ConsumptionResult(amount, factor.fuelUnit(), null);
    }
}
