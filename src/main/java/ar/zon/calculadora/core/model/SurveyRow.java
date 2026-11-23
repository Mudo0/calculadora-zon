package ar.zon.calculadora.core.model;

import java.util.Map;

/** Fila de encuesta ya mapeada a nombres lógicos de columna. */
public record SurveyRow(int number, Map<String, String> cells) {
}
