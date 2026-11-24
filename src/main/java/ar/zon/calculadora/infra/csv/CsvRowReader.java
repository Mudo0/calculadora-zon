package ar.zon.calculadora.infra.csv;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.ingest.ColumnMapping;
import ar.zon.calculadora.core.model.SurveyRow;
import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Lee la encuesta CSV y la deja en filas mapeadas a columnas lógicas. */
public final class CsvRowReader {

    private static final int SAMPLE_BYTES = 64 * 1024;

    /**
     * Abre la encuesta para lectura diferida, detectando el formato con una muestra acotada.
     *
     * @param path archivo de entrada
     * @param config configuración del lote
     * @return filas mapeadas en forma diferida; debe cerrarse al terminar
     * @throws BatchException si el archivo no se puede leer o faltan columnas obligatorias
     */
    public RowStream read(Path path, CalculationConfig config) throws BatchException {
        // shortcut: charset y delimitador se infieren de los primeros 64 KB; ampliar la sonda si el encabezado excede ese tamaño.
        byte[] sample;
        try {
            sample = readSample(path);
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo leer " + path + ": " + e.getMessage());
        }
        Charset charset = detectCharset(sample, config.input().csvCharset());
        char delimiter = detectDelimiter(sample, charset, config.input().csvDelimiter(),
                config.input().headerRow());

        CsvReader<CsvRecord> reader;
        try {
            reader = CsvReader.builder()
                    .fieldSeparator(delimiter)
                    .allowMissingFields(true)
                    .allowExtraFields(true)
                    .skipEmptyLines(true)
                    .detectBomHeader(true)
                    .ofCsvRecord(Files.newInputStream(path), charset);
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo abrir " + path + ": " + e.getMessage());
        }

        try {
            CloseableIterator<CsvRecord> records = reader.iterator();
            ColumnMapping mapping = null;
            for (int index = 1; index <= config.input().headerRow() && records.hasNext(); index++) {
                List<String> fields = records.next().getFields();
                if (index == config.input().headerRow()) {
                    mapping = ColumnMapping.from(fields, config.columns());
                }
            }
            if (mapping == null) {
                throw new BatchException(ErrorCode.INPUT_UNREADABLE,
                        "No se encontró la fila de encabezado " + config.input().headerRow() + ": " + path);
            }
            if (!mapping.missingRequired().isEmpty()) {
                throw new BatchException(ErrorCode.MISSING_REQUIRED_COLUMN,
                        "Faltan columnas obligatorias: " + String.join(", ", mapping.missingRequired()));
            }
            return new RowStream(reader, records, mapping);
        } catch (BatchException e) {
            closeQuietly(reader);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(reader);
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo analizar " + path + ": " + e.getMessage());
        }
    }

    private static byte[] readSample(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(SAMPLE_BYTES);
        }
    }

    private static void closeQuietly(CsvReader<CsvRecord> reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // El archivo ya se cerró o el sistema no permite cerrarlo.
        }
    }

    /** Filas mapeadas en lectura diferida. Debe cerrarse para liberar el archivo. */
    public static final class RowStream implements Iterable<SurveyRow>, AutoCloseable {

        private final CsvReader<CsvRecord> reader;
        private final CloseableIterator<CsvRecord> records;
        private final ColumnMapping mapping;

        private RowStream(CsvReader<CsvRecord> reader, CloseableIterator<CsvRecord> records, ColumnMapping mapping) {
            this.reader = reader;
            this.records = records;
            this.mapping = mapping;
        }

        @Override
        public Iterator<SurveyRow> iterator() {
            return new Iterator<>() {
                private int number;
                private SurveyRow pending;

                @Override
                public boolean hasNext() {
                    while (pending == null && records.hasNext()) {
                        List<String> fields = records.next().getFields();
                        if (fields.stream().allMatch(value -> value == null || value.isBlank())) {
                            continue;
                        }
                        pending = toRow(fields, ++number);
                    }
                    return pending != null;
                }

                @Override
                public SurveyRow next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    SurveyRow row = pending;
                    pending = null;
                    return row;
                }

                private SurveyRow toRow(List<String> fields, int rowNumber) {
                    Map<String, String> cells = new HashMap<>();
                    mapping.indexes().forEach((logical, position) ->
                            cells.put(logical, position < fields.size() ? fields.get(position) : ""));
                    return new SurveyRow(rowNumber, Map.copyOf(cells));
                }
            };
        }

        @Override
        public void close() throws IOException {
            records.close();
            reader.close();
        }
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
        // endOfInput=false tolera una secuencia multibyte cortada por el límite de la muestra.
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CoderResult result = decoder.decode(ByteBuffer.wrap(bytes), CharBuffer.allocate(bytes.length), false);
        return result.isUnderflow()
                ? StandardCharsets.UTF_8
                : Charset.forName("windows-1252");
    }

    private static char detectDelimiter(byte[] bytes, Charset charset, String configured, int headerRow)
            throws BatchException {
        if (!"auto".equalsIgnoreCase(configured)) {
            if (configured.length() != 1) {
                throw new BatchException(ErrorCode.INVALID_CONFIG, "input.csv.delimiter debe ser un carácter o auto");
            }
            return configured.charAt(0);
        }
        String text = new String(bytes, charset).replace("\uFEFF", "");
        for (int index = 1; index < headerRow; index++) {
            int newline = text.indexOf('\n');
            if (newline < 0) {
                break;
            }
            text = text.substring(newline + 1);
        }
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        char best = ';';
        int bestCount = 0;
        for (char candidate : new char[] {';', ',', '\t'}) {
            int count = 0;
            for (int i = 0; i < header.length(); i++) {
                if (header.charAt(i) == candidate) {
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
