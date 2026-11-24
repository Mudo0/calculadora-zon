package ar.zon.calculadora.infra.csv;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.LineDelimiter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Escribe el informe CSV con el contrato de columnas y formatos de la sección 12.1. */
public final class CsvReportWriter {

    /** Columnas del informe, en orden fijo. */
    public static final List<String> HEADERS = List.of(
            "row_number", "status", "error_code", "error_message", "evento",
            "origen_input", "origen_label", "origen_lat", "origen_lon",
            "destino_input", "destino_label", "destino_lat", "destino_lon",
            "transporte_normalizado", "combustible_normalizado", "fuel_inferred", "personas",
            "distance_km", "distance_straight_km", "distance_mode", "distance_fallback_used",
            "distance_fallback_reason", "consumption_value", "consumption_unit",
            "emissions_kgco2e", "emissions_basis", "factor_key", "factor_source",
            "factor_is_placeholder", "calculation_notes");

    private static final Set<String> NUMERIC_COLUMNS = Set.of(
            "row_number", "origen_lat", "origen_lon", "destino_lat", "destino_lon", "personas",
            "distance_km", "distance_straight_km", "consumption_value", "emissions_kgco2e");

    /**
     * Escribe el informe en la ruta indicada, rechazando un archivo existente.
     *
     * @param path ruta de salida
     * @param rows filas con valores ya formateados, indexados por columna lógica
     * @param config configuración del lote
     * @throws BatchException si la salida existe o no se puede escribir
     */
    public void write(Path path, List<Map<String, String>> rows, CalculationConfig config) throws BatchException {
        write(path, rows, config, false);
    }

    /**
     * Escribe el informe usando un archivo temporal y lo renombra al terminar.
     *
     * @param path ruta de salida
     * @param rows filas con valores ya formateados, indexados por columna lógica
     * @param config configuración del lote
     * @param force reemplaza la salida existente cuando es verdadero
     * @throws BatchException si la salida existe, la configuración es inválida o no se puede escribir
     */
    public void write(Path path, List<Map<String, String>> rows, CalculationConfig config,
                      boolean force) throws BatchException {
        if (Files.isDirectory(path)) {
            throw new BatchException(ErrorCode.OUTPUT_WRITE_ERROR, "La salida es un directorio: " + path);
        }
        if (Files.exists(path) && !force) {
            throw new BatchException(ErrorCode.OUTPUT_EXISTS, "La salida ya existe: " + path);
        }
        char delimiter = delimiter(config);
        Charset charset = charset(config);
        Path parent = path.toAbsolutePath().getParent();
        Path temporary;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".zon-report-", ".tmp");
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.OUTPUT_WRITE_ERROR, "No se pudo crear la salida: " + e.getMessage());
        }
        try {
            try (OutputStream out = Files.newOutputStream(temporary);
                 CsvWriter writer = CsvWriter.builder()
                         .fieldSeparator(delimiter)
                         .lineDelimiter(LineDelimiter.CRLF)
                         .build(out, charset)) {
                if (config.output().csvBom() && charset.equals(StandardCharsets.UTF_8)) {
                    out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                }
                writer.writeRecord(HEADERS);
                for (Map<String, String> row : rows) {
                    writer.writeRecord(HEADERS.stream()
                            .map(header -> guard(row.getOrDefault(header, ""), header))
                            .toList());
                }
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // El archivo temporal queda huérfano solo si el sistema no permite borrarlo.
            }
            throw new BatchException(ErrorCode.OUTPUT_WRITE_ERROR, "No se pudo escribir " + path + ": " + e.getMessage());
        }
    }

    private static String guard(String value, String header) {
        if (value == null || NUMERIC_COLUMNS.contains(header) || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value
                : value;
    }

    private static char delimiter(CalculationConfig config) throws BatchException {
        String configured = config.output().csvDelimiter();
        if (configured == null || configured.length() != 1) {
            throw new BatchException(ErrorCode.INVALID_CONFIG, "output.csv.delimiter debe ser un carácter");
        }
        return configured.charAt(0);
    }

    private static Charset charset(CalculationConfig config) throws BatchException {
        try {
            return Charset.forName(config.output().csvCharset());
        } catch (RuntimeException e) {
            throw new BatchException(ErrorCode.INVALID_CONFIG, "output.csv.charset no es válido: "
                    + config.output().csvCharset());
        }
    }
}
