package ar.zon.calculadora.core.summary;

import ar.zon.calculadora.core.model.RowResult;
import ar.zon.calculadora.core.model.RowStatus;

import java.util.ArrayList;
import java.util.List;

/** Consolida los resultados del lote sin perder precisión en los totales. */
public final class SummaryAggregator {

    private SummaryAggregator() {
    }

    /**
     * Agrega los resultados de todas las filas.
     *
     * @param rows resultados del lote
     * @return resumen con totales, conteos y advertencias
     */
    public static BatchSummary summarize(List<RowResult> rows) {
        double km = 0;
        double kg = 0;
        int ok = 0;
        int errors = 0;
        int skipped = 0;
        int fallbacks = 0;
        boolean placeholder = false;

        for (RowResult row : rows) {
            switch (row.status()) {
                case OK -> ok++;
                case OK_FALLBACK_HAVERSINE -> {
                    ok++;
                    fallbacks++;
                }
                case ERROR -> errors++;
                case SKIPPED_NON_MOTORIZED -> skipped++;
            }
            if ((row.status() == RowStatus.OK || row.status() == RowStatus.OK_FALLBACK_HAVERSINE)
                    && row.distance() != null) {
                km += row.distance().km();
            }
            if (row.emission() != null) {
                kg += row.emission().kgCo2e();
                placeholder |= row.emission().placeholder();
            }
        }

        List<String> warnings = new ArrayList<>();
        if (placeholder) {
            warnings.add("USING_PLACEHOLDER_FACTORS");
        }
        return new BatchSummary(km, kg, ok, errors, skipped, fallbacks, List.copyOf(warnings));
    }
}
