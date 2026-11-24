package ar.zon.calculadora.infra.csv;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.ingest.ColumnMapping;
import ar.zon.calculadora.core.model.SurveyRow;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lee la encuesta CSV y la deja en filas mapeadas a columnas lógicas. */
public final class CsvRowReader {

    /**
     * Lee un archivo CSV aplicando aliases, detección de BOM, charset y delimitador.
     *
     * @param path archivo de entrada
     * @param config configuración del lote
     * @return filas no vacías, numeradas desde uno
     * @throws BatchException si el archivo no se puede leer o faltan columnas obligatorias
     */
    public List<SurveyRow> read(Path path, CalculationConfig config) throws BatchException {
        // shortcut: el archivo se bufferiza completo para detectar charset y delimitador; migrar a sonda streaming para archivos muy grandes.
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo leer " + path + ": " + e.getMessage());
        }

        Charset charset = detectCharset(bytes, config.input().csvCharset());
        char delimiter = detectDelimiter(bytes, charset, config.input().csvDelimiter());
        List<CsvRecord> records;
        try (CsvReader<CsvRecord> reader = CsvReader.builder()
                .fieldSeparator(delimiter)
                .allowMissingFields(true)
                .skipEmptyLines(true)
                .detectBomHeader(true)
                .ofCsvRecord(new ByteArrayInputStream(bytes), charset)) {
            records = reader.stream().toList();
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo analizar " + path + ": " + e.getMessage());
        }

        if (records.isEmpty()) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "El archivo de entrada está vacío: " + path);
        }
        ColumnMapping mapping = ColumnMapping.from(records.get(0).getFields(), config.columns());
        if (!mapping.missingRequired().isEmpty()) {
            throw new BatchException(ErrorCode.MISSING_REQUIRED_COLUMN,
                    "Faltan columnas obligatorias: " + String.join(", ", mapping.missingRequired()));
        }

        List<SurveyRow> rows = new ArrayList<>();
        int number = 0;
        for (int index = 1; index < records.size(); index++) {
            List<String> fields = records.get(index).getFields();
            if (fields.stream().allMatch(value -> value == null || value.isBlank())) {
                continue;
            }
            Map<String, String> cells = new HashMap<>();
            mapping.indexes().forEach((logical, position) ->
                    cells.put(logical, position < fields.size() ? fields.get(position) : ""));
            rows.add(new SurveyRow(++number, Map.copyOf(cells)));
        }
        return List.copyOf(rows);
    }

    private static Charset detectCharset(byte[] bytes, String configured) {
        if (!"auto".equalsIgnoreCase(configured)) {
            return Charset.forName(configured);
        }
        if (startsWith(bytes, 0x00, 0x00, 0xFE, 0xFF)) {
            return Charset.forName("UTF-32BE");
        }
        if (startsWith(bytes, 0xFF, 0xFE, 0x00, 0x00)) {
            return Charset.forName("UTF-32LE");
        }
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return StandardCharsets.UTF_8;
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return StandardCharsets.UTF_16BE;
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return StandardCharsets.UTF_16LE;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (Exception e) {
            return Charset.forName("windows-1252");
        }
    }

    private static char detectDelimiter(byte[] bytes, Charset charset, String configured) throws BatchException {
        if (!"auto".equalsIgnoreCase(configured)) {
            if (configured.length() != 1) {
                throw new BatchException(ErrorCode.INVALID_CONFIG, "input.csv.delimiter debe ser un carácter o auto");
            }
            return configured.charAt(0);
        }
        String text = new String(bytes, charset).replace("\uFEFF", "");
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        char best = ';';
        int bestCount = 0;
        for (char candidate : new char[] {';', ',', '\t'}) {
            int count = 0;
            for (int i = 0; i < firstLine.length(); i++) {
                if (firstLine.charAt(i) == candidate) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
