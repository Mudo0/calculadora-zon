package ar.zon.calculadora.cli;

import ar.zon.calculadora.core.summary.BatchSummary;

import java.util.Locale;

/** Formatea el resumen del lote para consola y para el archivo JSON opcional. */
final class SummaryRenderer {

    private SummaryRenderer() {
    }

    /**
     * Devuelve el resumen legible para la consola.
     *
     * @param summary resumen calculado
     * @return texto de una línea
     */
    static String console(BatchSummary summary) {
        return String.format(Locale.ROOT,
                "filas=%d correctas=%d con_respaldo=%d omitidas=%d errores=%d km=%.2f kgco2e=%.2f avisos=%s",
                rowsTotal(summary), summary.okRows(), summary.fallbackRows(), summary.skippedRows(),
                summary.errorRows(), summary.totalKm(), summary.totalKgCo2e(),
                String.join(",", summary.warnings()));
    }

    /**
     * Devuelve el resumen como JSON simple.
     *
     * @param summary resumen calculado
     * @return documento JSON de una línea
     */
    static String json(BatchSummary summary) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendNumber(json, "rows_total", rowsTotal(summary), true);
        appendNumber(json, "ok", summary.okRows(), false);
        appendNumber(json, "ok_with_fallback", summary.fallbackRows(), false);
        appendNumber(json, "skipped", summary.skippedRows(), false);
        appendNumber(json, "errors", summary.errorRows(), false);
        json.append(",\"total_km\":").append(String.format(Locale.ROOT, "%.6f", summary.totalKm()));
        json.append(",\"total_kgco2e\":").append(String.format(Locale.ROOT, "%.6f", summary.totalKgCo2e()));
        json.append(",\"warnings\":[");
        for (int i = 0; i < summary.warnings().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(escape(summary.warnings().get(i))).append('"');
        }
        return json.append("]}").toString();
    }

    private static int rowsTotal(BatchSummary summary) {
        return summary.okRows() + summary.skippedRows() + summary.errorRows();
    }

    private static void appendNumber(StringBuilder json, String key, int value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(key).append("\":").append(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
