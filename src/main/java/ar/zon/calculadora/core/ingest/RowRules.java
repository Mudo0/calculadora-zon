package ar.zon.calculadora.core.ingest;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.model.FuelType;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.model.TransportMode;

import java.util.Locale;

/** Aplica las reglas de normalización y validación en el orden definido. */
public final class RowRules {

    private RowRules() {
    }

    /**
     * Analiza una fila sin consultar servicios externos.
     *
     * @param row fila mapeada
     * @param config configuración del lote
     * @return resultado de la validación inicial
     */
    public static RowAnalysis analyze(SurveyRow row, CalculationConfig config) {
        TransportMode transport = TransportMode.from(cell(row, "transporte"));
        if (transport == TransportMode.MISSING_TRANSPORT) {
            return error(transport, ErrorCode.MISSING_TRANSPORT);
        }
        if (transport == TransportMode.UNKNOWN_TRANSPORT) {
            return error(transport, ErrorCode.UNKNOWN_TRANSPORT);
        }
        if (transport == TransportMode.PIE || transport == TransportMode.BICI) {
            return new RowAnalysis(transport, RowStatus.SKIPPED_NON_MOTORIZED, null, 0, null, false);
        }

        int min = config.validation().personasMin();
        int max = config.validation().perTransportMax()
                .getOrDefault(transport.name().toLowerCase(Locale.ROOT), config.validation().personasMax());
        var people = PeopleCountParser.parse(cell(row, "personas"), min, max);
        if (people.isEmpty()) {
            return error(transport, ErrorCode.INVALID_PEOPLE_COUNT);
        }
        if (cell(row, "origen").isBlank()) {
            return error(transport, ErrorCode.MISSING_ORIGIN);
        }

        var fuelDecision = FuelResolver.resolve(transport, cell(row, "combustible"), config.defaults().fuelAuto())
                .orElseThrow();
        return new RowAnalysis(transport, RowStatus.OK, null, people.getAsInt(),
                fuelDecision.fuel(), fuelDecision.inferred());
    }

    private static RowAnalysis error(TransportMode transport, ErrorCode code) {
        return new RowAnalysis(transport, RowStatus.ERROR, code, 0, null, false);
    }

    private static String cell(SurveyRow row, String key) {
        return row.cells().getOrDefault(key, "");
    }
}
