package ar.zon.calculadora.core.emissions;

/** Factor de consumo y emisiones de una combinación transporte y combustible. */
public record EmissionFactor(
        String key,
        FactorBasis basis,
        String fuelUnit,
        double consumptionPer100Km,
        double kgPerFuelUnit,
        double kgPerPassengerKm,
        String source,
        boolean placeholder) {
}
