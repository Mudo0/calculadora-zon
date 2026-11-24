package ar.zon.calculadora.core.pipeline;

import ar.zon.calculadora.core.errors.BatchException;

import java.util.Map;

/** Destino de cada fila ya formateada del informe. */
@FunctionalInterface
public interface ReportSink {

    /**
     * Escribe una fila del informe.
     *
     * @param cells valores indexados por columna lógica
     * @throws BatchException si la fila no se puede escribir
     */
    void accept(Map<String, String> cells) throws BatchException;
}
