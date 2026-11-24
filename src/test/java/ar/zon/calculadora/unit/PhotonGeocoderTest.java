package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.geocode.CandidateSelector;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.infra.http.HttpJsonClient;
import ar.zon.calculadora.infra.photon.PhotonGeocoder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhotonGeocoderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsFixtureFeatureToCandidate() throws IOException {
        serve(fixture("photon-alta-gracia.json"));

        List<GeoCandidate> candidates = geocoder(null, null, null).geocode("Alta Gracia");

        assertThat(candidates).hasSize(1);
        GeoCandidate candidate = candidates.get(0);
        assertThat(candidate.label()).isEqualTo("Alta Gracia");
        assertThat(candidate.point().latitude()).isEqualTo(-31.6578916);
        assertThat(candidate.point().longitude()).isEqualTo(-64.4338563);
        assertThat(candidate.provider()).isEqualTo("PHOTON");
    }

    @Test
    void sendsQueryLimitBiasAndBbox() throws IOException {
        List<String> queries = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            respond(exchange, fixture("photon-alta-gracia.json"));
        });
        server.start();

        geocoder("-31.4201", "-64.1888", "-65,-33,-63,-30").geocode("Alta Gracia");

        assertThat(queries).hasSize(1);
        assertThat(queries.get(0)).contains("q=Alta+Gracia", "limit=5")
                .contains("lat=-31.4201", "lon=-64.1888")
                .contains("bbox=-65%2C-33%2C-63%2C-30");
    }

    @Test
    void selectionIsIndependentOfResponseOrder() {
        GeoCandidate first = new GeoCandidate("Alta Gracia", null, 5.0, "PHOTON");
        GeoCandidate second = new GeoCandidate("Alta Gracia Norte", null, 4.0, "PHOTON");
        List<GeoCandidate> reversed = new ArrayList<>(List.of(first, second));
        Collections.reverse(reversed);

        assertThat(CandidateSelector.select(List.of(first, second)))
                .isEqualTo(CandidateSelector.select(reversed));
        assertThat(CandidateSelector.select(reversed)).contains(first);
    }

    @Test
    void emptyFeaturesProduceNoCandidates() throws IOException {
        serve("{\"features\":[]}");

        assertThat(geocoder(null, null, null).geocode("no existe")).isEmpty();
    }

    private PhotonGeocoder geocoder(String biasLat, String biasLon, String bbox) throws IOException {
        return new PhotonGeocoder(new HttpJsonClient(url(), 1000, 0, null), biasLat, biasLon, bbox, 5);
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
        try (var input = PhotonGeocoderTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IOException("fixture no encontrada: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
