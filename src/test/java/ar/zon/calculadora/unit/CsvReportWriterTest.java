package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import ar.zon.calculadora.infra.csv.CsvReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvReportWriterTest {

    private static final String HEADERS = String.join(";",
            "row_number", "status", "error_code", "error_message", "evento",
            "origen_input", "origen_label", "origen_lat", "origen_lon",
            "destino_input", "destino_label", "destino_lat", "destino_lon",
            "transporte_normalizado", "combustible_normalizado", "fuel_inferred", "personas",
            "distance_km", "distance_straight_km", "distance_mode", "distance_fallback_used",
            "distance_fallback_reason", "consumption_value", "consumption_unit",
            "emissions_kgco2e", "emissions_basis", "factor_key", "factor_source",
            "factor_is_placeholder", "calculation_notes");

    @TempDir
    Path directory;

    @Test
    void writesFixedHeaderWithBomAndEsArDefaults() throws Exception {
        Path output = directory.resolve("report.csv");

        new CsvReportWriter().write(output, List.of(Map.of(
                "row_number", "1",
                "status", "OK",
                "origen_input", "Córdoba",
                "distance_km", "12,34")), config(""));

        byte[] bytes = Files.readAllBytes(output);
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String[] lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines[0]).isEqualTo(HEADERS);
        assertThat(lines[1]).contains("Córdoba").contains("12,34");
        assertThat(lines[1].split(";", -1)).hasSize(30);
    }

    @Test
    void prefixesFormulaLikeInputText() throws Exception {
        Path output = directory.resolve("injection.csv");

        new CsvReportWriter().write(output, List.of(Map.of(
                "evento", "=CMD('calc')",
                "origen_input", "+SUM(A1:A2)",
                "destino_input", "@host",
                "error_message", "\ttrigger")), config(""));

        String content = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertThat(content).contains("'=CMD('calc')")
                .contains("'+SUM(A1:A2)")
                .contains("'@host")
                .contains("'\ttrigger");
    }

    @Test
    void refusesExistingOutputUnlessForced() throws Exception {
        Path output = directory.resolve("exists.csv");
        Files.writeString(output, "old");
        var writer = new CsvReportWriter();
        var rows = List.of(Map.of("row_number", "2"));

        assertThatThrownBy(() -> writer.write(output, rows, config(""), false))
                .isInstanceOf(BatchException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.OUTPUT_EXISTS);

        writer.write(output, rows, config(""), true);
        assertThat(Files.readString(output)).contains("2");
    }

    @Test
    void reportsUnwritableOutput() throws Exception {
        Path target = Files.createDirectory(directory.resolve("as-file"));

        assertThatThrownBy(() -> new CsvReportWriter().write(target,
                List.of(Map.of("status", "OK")), config(""), true))
                .isInstanceOf(BatchException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.OUTPUT_WRITE_ERROR);
    }

    @Test
    void writesThroughTemporaryFileAndLeavesNoTempBehind() throws Exception {
        Path output = directory.resolve("nested").resolve("report.csv");

        new CsvReportWriter().write(output, List.of(Map.of("status", "OK")), config(""));

        assertThat(Files.exists(output)).isTrue();
        try (var paths = Files.list(output.getParent())) {
            assertThat(paths.map(path -> path.getFileName().toString())).containsExactly("report.csv");
        }
    }

    @Test
    void writesRowsIncrementallyBeforeCommitting() throws Exception {
        Path output = directory.resolve("streamed.csv");

        try (var report = new CsvReportWriter().open(output, config(""))) {
            report.writeRow(Map.of("row_number", "1", "status", "OK"));
            report.writeRow(Map.of("row_number", "2", "status", "ERROR"));
            assertThat(Files.exists(output)).isFalse();
            report.commit();
        }

        String[] lines = Files.readString(output).split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).contains("OK");
        assertThat(lines[2]).contains("ERROR");
        try (var paths = Files.list(directory)) {
            assertThat(paths.map(path -> path.getFileName().toString())).containsExactly("streamed.csv");
        }
    }

    @Test
    void refusesToPublishWhenOutputAppearsAfterOpen() throws Exception {
        Path output = directory.resolve("race.csv");

        try (var report = new CsvReportWriter().open(output, config(""))) {
            Files.writeString(output, "created by another process");
            report.writeRow(Map.of("row_number", "1"));
            assertThatThrownBy(report::commit)
                    .isInstanceOf(BatchException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.OUTPUT_EXISTS);
        }

        assertThat(Files.readString(output)).isEqualTo("created by another process");
    }

    private CalculationConfig config(String extra) {
        String properties = """
                output.format=csv
                output.csv.delimiter=;
                output.csv.decimal=,
                output.csv.bom=true
                output.csv.charset=UTF-8
                """ + extra;
        return new PropertiesConfigLoader().load(new StringReader(properties));
    }
}
