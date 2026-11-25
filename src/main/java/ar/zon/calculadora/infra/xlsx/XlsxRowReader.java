package ar.zon.calculadora.infra.xlsx;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.ingest.ColumnMapping;
import ar.zon.calculadora.core.ingest.SurveyRows;
import ar.zon.calculadora.core.model.SurveyRow;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Lee una hoja XLSX fila por fila y aplica el mismo mapeo que el CSV. */
public final class XlsxRowReader {
    public SurveyRows read(Path path, CalculationConfig config) throws BatchException {
        try {
            var workbook = new ReadableWorkbook(path.toFile());
            try {
                String selector = config.input().xlsxSheet();
                var sheet = selector.matches("[0-9]+")
                        ? workbook.getSheet(Integer.parseInt(selector) - 1)
                        : workbook.findSheet(selector);
                if (sheet.isEmpty()) {
                    throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se encontró la hoja " + selector);
                }
                return openSheet(workbook, sheet.get(), config);
            } catch (BatchException | IOException | RuntimeException e) {
                try {
                    workbook.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo leer " + path + ": " + e.getMessage());
        }
    }

    private SurveyRows openSheet(ReadableWorkbook workbook, Sheet sheet, CalculationConfig config)
            throws IOException, BatchException {
        Stream<Row> rows = sheet.openStream();
        try {
            Iterator<Row> iterator = rows.iterator();
            while (iterator.hasNext()) {
                Row row = iterator.next();
                if (row.getRowNum() < config.input().headerRow()) {
                    continue;
                }
                if (row.getRowNum() == config.input().headerRow()) {
                    List<String> headers = fields(row);
                    ColumnMapping mapping = ColumnMapping.from(headers, config.columns());
                    if (!mapping.missingRequired().isEmpty()) {
                        throw new BatchException(ErrorCode.MISSING_REQUIRED_COLUMN,
                                "Faltan columnas obligatorias: " + String.join(", ", mapping.missingRequired()));
                    }
                    return new Rows(workbook, rows, iterator, headers, mapping);
                }
                break;
            }
            throw new BatchException(ErrorCode.INPUT_UNREADABLE,
                    "No se encontró la fila de encabezado " + config.input().headerRow());
        } catch (BatchException | RuntimeException e) {
            rows.close();
            throw e;
        }
    }

    private static List<String> fields(Row row) {
        return IntStream.range(0, row.getCellCount()).mapToObj(row::getCellText).toList();
    }

    private record Rows(ReadableWorkbook workbook, Stream<Row> stream, Iterator<Row> source,
                        List<String> headers, ColumnMapping mapping) implements SurveyRows {
        @Override
        public Iterator<SurveyRow> iterator() {
            return Stream.generate(() -> {
                        try {
                            return source.hasNext() ? source.next() : null;
                        } catch (RuntimeException e) {
                            throw new UncheckedIOException(new IOException("No se pudo leer la hoja XLSX", e));
                        }
                    })
                    .takeWhile(row -> row != null)
                    .map(this::map)
                    .filter(row -> row.sourceCells().stream().anyMatch(value -> !value.isBlank()))
                    .iterator();
        }

        private SurveyRow map(Row row) {
            Map<String, String> mapped = new HashMap<>();
            mapping.indexes().forEach((logical, column) -> mapped.put(logical, row.getCellText(column)));
            return new SurveyRow(row.getRowNum(), Map.copyOf(mapped), fields(row));
        }

        @Override
        public void close() throws IOException {
            try (workbook; stream) {
                // La hoja se cierra antes del libro.
            }
        }
    }
}
