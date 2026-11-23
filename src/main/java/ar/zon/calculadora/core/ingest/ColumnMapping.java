package ar.zon.calculadora.core.ingest;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.model.TextNormalizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Asocia columnas lógicas con posiciones de la cabecera. */
public record ColumnMapping(Map<String, Integer> indexes, List<String> missingRequired) {

    private static final List<String> REQUIRED = List.of("personas", "origen", "transporte");

    /**
     * Resuelve los alias configurados contra una cabecera normalizada.
     *
     * @param headers nombres de columna del archivo
     * @param columns alias configurados por columna lógica
     * @return índices encontrados y columnas obligatorias ausentes
     */
    public static ColumnMapping from(List<String> headers, CalculationConfig.Columns columns) {
        Map<String, List<String>> aliases = Map.of(
                "personas", columns.personas(),
                "origen", columns.origen(),
                "destino", columns.destino(),
                "transporte", columns.transporte(),
                "combustible", columns.combustible(),
                "evento", columns.evento());

        List<String> normalizedHeaders = headers.stream().map(TextNormalizer::normalize).toList();
        Map<String, Integer> indexes = new HashMap<>();
        aliases.forEach((logical, candidates) -> {
            for (String candidate : candidates) {
                String normalizedCandidate = TextNormalizer.normalize(candidate);
                int position = normalizedHeaders.indexOf(normalizedCandidate);
                if (position >= 0) {
                    indexes.put(logical, position);
                    break;
                }
            }
        });

        List<String> missing = REQUIRED.stream().filter(logical -> !indexes.containsKey(logical)).toList();
        return new ColumnMapping(Map.copyOf(indexes), missing);
    }

    /**
     * Devuelve el índice de una columna lógica.
     *
     * @param logical nombre lógico de la columna
     * @return índice basado en cero, o nulo si no está presente
     */
    public Integer index(String logical) {
        return indexes.get(logical);
    }
}
