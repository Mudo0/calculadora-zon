package ar.zon.calculadora.cli;

import ar.zon.calculadora.infra.csv.CsvReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke end-to-end offline con la muestra sintética del repositorio. */
class OfflineSmokeIT {

    @TempDir
    Path directory;

    @Test
    void runsTheSampleOfflineAndWritesAnEsArReport() throws Exception {
        Path factors = directory.resolve("factors.properties");
        Files.writeString(factors, """
                factor.auto.nafta.basis=vehicle_km
                factor.auto.nafta.fuel_unit=l
                factor.auto.nafta.consumption_per_100km=10.0
                factor.auto.nafta.kgco2e_per_fuel_unit=2.0
                factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
                factor.auto.gnc.basis=vehicle_km
                factor.auto.gnc.fuel_unit=m3
                factor.auto.gnc.consumption_per_100km=10.0
                factor.auto.gnc.kgco2e_per_fuel_unit=2.0
                factor.auto.gnc.source=PLACEHOLDER_NOT_REAL
                factor.moto.nafta.basis=vehicle_km
                factor.moto.nafta.fuel_unit=l
                factor.moto.nafta.consumption_per_100km=4.0
                factor.moto.nafta.kgco2e_per_fuel_unit=2.0
                factor.moto.nafta.source=PLACEHOLDER_NOT_REAL
                factor.colectivo.gasoil.basis=passenger_km
                factor.colectivo.gasoil.kgco2e_per_passenger_km=0.1
                factor.colectivo.gasoil.source=PLACEHOLDER_NOT_REAL
                """);
        Path config = directory.resolve("application.properties");
        Files.writeString(config, """
                output.format=csv
                output.csv.delimiter=;
                output.csv.decimal=,
                output.csv.bom=true
                output.csv.charset=UTF-8
                input.csv.delimiter=auto
                input.csv.charset=auto
                input.csv.decimal=auto
                columns.personas=personas
                columns.origen=origen
                columns.destino=destino
                columns.transporte=transporte
                columns.combustible=combustible
                columns.evento=evento
                destination.lat=-31.4201
                destination.lon=-64.1888
                personas.min=1
                personas.max=5
                default.fuel.auto=NAFTA
                geocoder.mode=COORDINATES
                distance.mode=HAVERSINE
                distance.decimals=2
                factors.file=%s
                """.formatted(factors.toString().replace('\\', '/')));
        Path output = directory.resolve("report.csv");
        Path summary = directory.resolve("summary.json");

        Captured result = run("--input", Path.of("samples/input-sample.csv").toString(),
                "--output", output.toString(), "--config", config.toString(),
                "--summary", summary.toString());

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.out()).contains("filas=15").contains("correctas=7")
                .contains("omitidas=2").contains("errores=6").contains("km=");
        assertThat(result.err()).contains("Procesando 15 filas");

        byte[] bytes = Files.readAllBytes(output);
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String[] lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).hasSize(16);
        assertThat(lines[0]).isEqualTo(String.join(";", CsvReportWriter.HEADERS));
        String[] first = lines[1].split(";", -1);
        assertThat(first[CsvReportWriter.HEADERS.indexOf("status")]).isEqualTo("OK");
        assertThat(first[CsvReportWriter.HEADERS.indexOf("distance_mode")]).isEqualTo("HAVERSINE");
        assertThat(first[CsvReportWriter.HEADERS.indexOf("emissions_basis")]).isEqualTo("VEHICLE_KM");
        assertThat(first[CsvReportWriter.HEADERS.indexOf("distance_km")]).contains(",");
        assertThat(String.join("\n", lines)).contains("SKIPPED_NON_MOTORIZED")
                .contains("UNKNOWN_TRANSPORT").contains("LOCATION_NOT_FOUND")
                .contains("'=CMD('calc')");

        assertThat(Files.readString(summary)).contains("\"rows_total\":15").contains("\"ok\":7")
                .contains("\"skipped\":2").contains("\"errors\":6");
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
