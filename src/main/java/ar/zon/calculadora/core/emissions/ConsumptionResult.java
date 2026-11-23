package ar.zon.calculadora.core.emissions;

/** Consumo calculado, o la razón por la que no aplica. */
public record ConsumptionResult(Double amount, String unit, String note) {
}
