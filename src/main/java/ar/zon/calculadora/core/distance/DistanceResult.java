package ar.zon.calculadora.core.distance;

/** Distancia calculada y si provino del respaldo de línea recta. */
public record DistanceResult(double km, boolean fallback) {
}
