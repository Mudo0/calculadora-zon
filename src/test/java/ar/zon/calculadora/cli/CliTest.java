package ar.zon.calculadora.cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CliTest {

    @TempDir
    Path directory;

    @Test
    void writesReportAndSummaryWithCleanExitCode() throws Exception {
        Path input = input("""
                personas;origen;transporte;combustible
                2;-31.4,-64.2;auto;nafta
                1;-31.5,-64.3;auto;nafta
                """);
        Path output = directory.resolve("report.csv");
        Path summary = directory.resolve("summary.json");

        Captured result = run("--input", input.toString(), "--output", output.toString(),
                "--config", config(false).toString(), "--destination", "-31.4201,-64.1888",
                "--summary", summary.toString(), "--event", "Evento X", "--force");

        assertThat(result.code()).isEqualTo(0);
        assertThat(result.out()).contains("filas=2").contains("errores=0");
        assertThat(result.err()).contains("Procesando 2 filas");
        assertThat(Files.readAllBytes(output)).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(Files.readString(output).split("\r\n")).hasSize(3);
        assertThat(Files.readString(summary)).contains("\"rows_total\":2").contains("\"errors\":0");
    }

    @Test
    void returnsOneWhenAnyRowFails() throws Exception {
        Path input = input("""
                personas;origen;transporte;combustible
                2;Desconocido;auto;nafta
                """);
        Path output = directory.resolve("with-error.csv");

        Captured result = run("--input", input.toString(), "--output", output.toString(),
                "--config", config(true).toString());

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.out()).contains("errores=1");
        assertThat(Files.readString(output)).contains("LOCATION_NOT_FOUND");
    }

    @Test
    void returnsTwoForFatalErrors() throws Exception {
        Path input = input("""
                personas;origen;transporte;combustible
                2;-31.4,-64.2;auto;nafta
                """);
        Path output = directory.resolve("exists.csv");
        Files.writeString(output, "old");
        String[] args = {"--input", input.toString(), "--output", output.toString(),
                "--config", config(true).toString()};

        assertThat(run(args).code()).isEqualTo(2);
        assertThat(run("--input", directory.resolve("missing.csv").toString(),
                "--output", output.toString(), "--config", config(true).toString()).code()).isEqualTo(2);
        assertThat(run("--unknown").code()).isEqualTo(2);
        assertThat(run("--input", input.toString(), "--output", output.toString(),
                "--config", config(true).toString(), "--services", "bundled").code()).isEqualTo(2);
        assertThat(run("--input", input.toString(), "--output", output.toString(),
                "--config", config(true).toString(), "--force").code()).isEqualTo(0);
    }

    @Test
    void printsHelpVersionAndUsageWithoutArguments() {
        assertThat(run("--help").out()).contains("Uso:");
        assertThat(run("--version").out()).contains(Main.VERSION);
        assertThat(run().out()).contains("Uso:");
    }

    private Path input(String content) throws Exception {
        Path file = directory.resolve("input.csv");
        Files.writeString(file, content);
        return file;
    }

    private Path config(boolean withDestination) throws Exception {
        Path factors = directory.resolve("factors.properties");
        Files.writeString(factors, """
                factor.auto.nafta.basis=vehicle_km
                factor.auto.nafta.fuel_unit=l
                factor.auto.nafta.consumption_per_100km=10.0
                factor.auto.nafta.kgco2e_per_fuel_unit=2.0
                factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
                """);
        String destination = withDestination ? """
                destination.lat=-31.4201
                destination.lon=-64.1888
                """ : "";
        Path file = directory.resolve("app.properties");
        Files.writeString(file, """
                output.format=csv
                geocoder.mode=COORDINATES
                distance.mode=HAVERSINE
                distance.decimals=2
                """ + destination + "factors.file="
                + factors.toString().replace('\\', '/') + "\n");
        return file;
    }

    private Captured run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int code = Main.run(args);
            return new Captured(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Captured(int code, String out, String err) {
    }
}
