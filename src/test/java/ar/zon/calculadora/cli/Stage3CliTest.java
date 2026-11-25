package ar.zon.calculadora.cli;

import ar.zon.calculadora.infra.csv.CsvReportWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Stage3CliTest {

    @TempDir
    Path directory;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void runsWithPhotonAndRouteUsingFixtures() throws Exception {
        serveFixtures();
        Path config = config(1, false);
        Path output = directory.resolve("stage3.csv");

        Captured result = run("--input", input().toString(), "--output", output.toString(),
                "--config", config.toString(), "--force");

        assertThat(result.code()).isEqualTo(0);
        String report = Files.readString(output);
        assertThat(report).contains("36,3709");
        assertThat(headerValue(report, "origen_label")).isEqualTo("Alta Gracia");
        assertThat(headerValue(report, "distance_fallback_reason")).isEmpty();
    }

    @Test
    void bothModeWritesDifferentRoadAndStraightDistances() throws Exception {
        serveFixtures();
        Path config = config(1, false, "BOTH");
        Path output = directory.resolve("both.csv");

        Captured result = run("--input", input().toString(), "--output", output.toString(),
                "--config", config.toString(), "--force");

        assertThat(result.code()).isEqualTo(0);
        String report = Files.readString(output);
        String road = headerValue(report, "distance_km");
        String straight = headerValue(report, "distance_straight_km");
        assertThat(road).isNotBlank();
        assertThat(straight).isNotBlank();
        assertThat(road).isNotEqualTo(straight);
        assertThat(headerValue(report, "distance_mode")).isEqualTo("BOTH");
    }

    @Test
    void stopsBatchWithExitTwoWhenServiceBreakerOpens() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 500, "down"));
        server.start();
        Path config = config(1, false);
        Path output = directory.resolve("fatal.csv");

        Captured result = run("--input", input().toString(), "--output", output.toString(),
                "--config", config.toString(), "--force");

        assertThat(result.code()).isEqualTo(2);
        assertThat(result.err()).contains("SERVICE_UNAVAILABLE");
        assertThat(Files.exists(output)).isFalse();
    }

    @Test
    void photonSuccessDoesNotResetOsrmFailureCounter() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> respond(exchange, 200, fixture("photon-alta-gracia.json")));
        server.createContext("/route", exchange -> respond(exchange, 500, "down"));
        server.start();
        Path config = config(2, false);
        Path output = directory.resolve("osrm-breaker.csv");

        Captured result = run("--input", input(2).toString(), "--output", output.toString(),
                "--config", config.toString(), "--force");

        assertThat(result.code()).isEqualTo(2);
        assertThat(result.err()).contains("SERVICE_UNAVAILABLE");
    }

    @Test
    void healthcheckFailsAtStartupWhenServiceIsDown() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 500, "down"));
        server.start();
        Path config = config(20, true);
        Path output = directory.resolve("health.csv");

        Captured result = run("--input", input().toString(), "--output", output.toString(),
                "--config", config.toString(), "--force");

        assertThat(result.code()).isEqualTo(2);
        assertThat(result.err()).contains("SERVICE_UNAVAILABLE");
    }

    private void serveFixtures() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = exchange.getRequestURI().getPath().startsWith("/api")
                    ? fixture("photon-alta-gracia.json")
                    : fixture("osrm-cordoba-villa-carlos-paz.json");
            respond(exchange, 200, body);
        });
        server.start();
    }

    private Path input() throws IOException {
        return input(1);
    }

    private Path input(int rows) throws IOException {
        Path file = directory.resolve("input-" + rows + ".csv");
        StringBuilder content = new StringBuilder("personas;origen;transporte;combustible\n");
        for (int i = 0; i < rows; i++) {
            content.append("2;Alta Gracia;auto;nafta\n");
        }
        Files.writeString(file, content.toString());
        return file;
    }

    private Path config(int maxFailures, boolean healthcheck) throws IOException {
        return config(maxFailures, healthcheck, "ROUTE");
    }

    private Path config(int maxFailures, boolean healthcheck, String distanceMode) throws IOException {
        Path factors = directory.resolve("factors.properties");
        Files.writeString(factors, """
                factor.auto.nafta.basis=vehicle_km
                factor.auto.nafta.fuel_unit=l
                factor.auto.nafta.consumption_per_100km=10.0
                factor.auto.nafta.kgco2e_per_fuel_unit=2.0
                factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
                """);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Path file = directory.resolve("app.properties");
        Files.writeString(file, String.join("\n",
                "output.format=csv",
                "geocoder.mode=PHOTON",
                "geo.maxCandidates=5",
                "geo.cache.enabled=false",
                "distance.mode=" + distanceMode,
                "route.cache.enabled=false",
                "distance.fallback=HAVERSINE",
                "distance.decimals=4",
                "destination.lat=-31.4201",
                "destination.lon=-64.1888",
                "photon.baseUrl=" + baseUrl,
                "osrm.baseUrl=" + baseUrl,
                "http.timeoutMs=1000",
                "http.retries=0",
                "http.maxConsecutiveFailures=" + maxFailures,
                "healthcheck.enabled=" + healthcheck,
                "factors.file=" + factors.toString().replace('\\', '/'),
                ""));
        return file;
    }

    private static String headerValue(String report, String header) throws IOException {
        String[] headers = CsvReportWriter.HEADERS.toArray(String[]::new);
        String[] values = report.split("\r\n")[1].split(";");
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(header)) {
                return values[i];
            }
        }
        throw new IOException("columna no encontrada: " + header);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String fixture(String name) throws IOException {
        try (var input = Stage3CliTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IOException("fixture no encontrada: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
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
