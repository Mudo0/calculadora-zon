package ar.zon.calculadora.core.emissions;

/** Emisiones calculadas junto con la trazabilidad del factor usado. */
public record EmissionResult(double kgCo2e, String factorKey, String source, boolean placeholder) {
}
