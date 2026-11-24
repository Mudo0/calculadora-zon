package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.route.RouteResult;
import ar.zon.calculadora.infra.http.HttpJsonClient;
import ar.zon.calculadora.infra.osrm.OsrmRouteCalculator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OsrmRouteCalculatorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void convertsMetersToKilometresFromFixture() throws IOException {
        serve(fixture("osrm-cordoba-villa-carlos-paz.json"));

        var route = calculator().route(new GeoPoint(-31.420103, -64.188709), new GeoPoint(-31.42409, -64.497848));

        assertThat(route).isPresent();
        assertThat(route.get().km()).isEqualTo(36.3709);
    }

    @Test
    void buildsDrivingUrlWithLongitudeFirst() throws IOException {
        List<String> paths = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
            respond(exchange, fixture("osrm-cordoba-villa-carlos-paz.json"));
        });
        server.start();

        calculator().route(new GeoPoint(-31.420103, -64.188709), new GeoPoint(-31.42409, -64.497848));

        assertThat(paths).hasSize(1);
        assertThat(paths.get(0)).isEqualTo(
                "/route/v1/driving/-64.188709,-31.420103;-64.497848,-31.424090?overview=false");
    }

    @Test
    void nonOkCodeMeansNoRoute() throws IOException {
        serve("{\"code\":\"NoRoute\",\"routes\":[]}");

        assertThat(calculator().route(new GeoPoint(-31.42, -64.18), new GeoPoint(-31.43, -64.49))).isEmpty();
    }

    @Test
    void missingDistanceMeansNoRoute() throws IOException {
        serve("{\"code\":\"Ok\",\"routes\":[{}]}");

        assertThat(calculator().route(new GeoPoint(-31.42, -64.18), new GeoPoint(-31.43, -64.49))).isEmpty();
    }

    private OsrmRouteCalculator calculator() throws IOException {
        return new OsrmRouteCalculator(new HttpJsonClient(url(), 1000, 0, null));
    }

    private void serve(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, body));
        server.start();
    }

    private String url() throws IOException {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String fixture(String name) throws IOException {
        try (var input = OsrmRouteCalculatorTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IOException("fixture no encontrada: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
