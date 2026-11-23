package ar.zon.calculadora.core.model;

/** Estado de una fila del informe. */
public enum RowStatus {

    OK,
    OK_FALLBACK_HAVERSINE,
    SKIPPED_NON_MOTORIZED,
    ERROR
}
