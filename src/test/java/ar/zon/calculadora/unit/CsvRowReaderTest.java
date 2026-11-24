package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import ar.zon.calculadora.infra.csv.CsvRowReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvRowReaderTest {

    @TempDir
    Path directory;

    @Test
    void readsUtf8FileWithBomAndAccents() throws Exception {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "personas;origen;transporte\r\n2;Córdoba;auto\r\n".getBytes(StandardCharsets.UTF_8);
        Path file = write("utf8.csv", concatenate(bom, body));

        var rows = new CsvRowReader().read(file, config(""));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).number()).isEqualTo(1);
        assertThat(rows.get(0).cells())
                .containsEntry("personas", "2")
                .containsEntry("origen", "Córdoba")
                .containsEntry("transporte", "auto");
    }

    @Test
    void readsWindows1252FileWithoutBom() throws Exception {
        Path file = write("cp1252.csv",
                "personas;origen;transporte\n2;Córdoba;auto\n".getBytes(Charset.forName("windows-1252")));

        var rows = new CsvRowReader().read(file, config(""));

        assertThat(rows.get(0).cells()).containsEntry("origen", "Córdoba");
    }

    @Test
    void detectsCommaDelimiterAndSkipsEmptyRows() throws Exception {
        Path file = write("commas.csv",
                "personas,origen,transporte\n\n2,Cordoba,auto\n   \n".getBytes(StandardCharsets.UTF_8));

        var rows = new CsvRowReader().read(file, config(""));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).cells()).containsEntry("origen", "Cordoba");
    }

    @Test
    void mapsNormalizedHeadersAndPreservesDecimalComma() throws Exception {
        Path file = write("headers.csv",
                "Pérsonas; Origen; Transporte; Combustible\n\"2,0\";Cordoba;auto;gasolina\n"
                        .getBytes(StandardCharsets.UTF_8));

        var rows = new CsvRowReader().read(file, config(""));

        assertThat(rows.get(0).cells())
                .containsEntry("personas", "2,0")
                .containsEntry("combustible", "gasolina");
    }

    @Test
    void rejectsFileWithoutRequiredColumns() throws Exception {
        Path file = write("invalid.csv", "foo;bar\n1;2\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new CsvRowReader().read(file, config("")))
                .extracting("code")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_COLUMN);
    }

    private CalculationConfig config(String extra) {
        String properties = "input.csv.delimiter=auto\ninput.csv.charset=auto\n" + extra;
        return new PropertiesConfigLoader().load(new StringReader(properties));
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path file = directory.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
