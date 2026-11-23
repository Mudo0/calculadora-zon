package ar.zon.calculadora.core.summary;

import java.util.List;

/** Totales y advertencias del lote, calculados con valores sin redondear. */
public record BatchSummary(double totalKm, double totalKgCo2e, int okRows, int errorRows,
                           int skippedRows, int fallbackRows, List<String> warnings) {
}
