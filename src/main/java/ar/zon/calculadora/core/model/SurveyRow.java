package ar.zon.calculadora.core.model;

import java.util.Map;
import java.util.List;

/** Fila de encuesta ya mapeada a nombres lógicos de columna. */
public record SurveyRow(int number, Map<String, String> cells, List<String> sourceCells) {
    public SurveyRow(int number, Map<String, String> cells) {
        this(number, cells, List.of());
    }
}
