package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.errors.ServiceUnavailableException;
import ar.zon.calculadora.infra.http.HttpJsonClient;
import ar.zon.calculadora.infra.http.ServiceCallException;
import ar.zon.calculadora.infra.http.ServiceFailureGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesServerErrorAndParsesJson() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = serve(request -> {
            if (calls.incrementAndGet() == 1) {
                respond(request, 503, "down");
            } else {
                respond(request, 200, "{\"features\":[]}");
            }
        });

        JsonNode json = new HttpJsonClient(url(), 1000, 1, null).getJson("/api?q=Alta%20Gracia");

        assertThat(json.path("features").isArray()).isTrue();
        assertThat(calls).hasValue(2);
    }

    @Test
    void reportsExhaustedRetriesAsServiceCallFailure() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = serve(request -> {
            calls.incrementAndGet();
            respond(request, 500, "down");
        });

        var client = new HttpJsonClient(url(), 1000, 1, null);

        assertThatThrownBy(() -> client.getJson("/api?q=x"))
                .isInstanceOf(ServiceCallException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void invalidJsonCountsAsFailureAndDoesNotResetGuard() throws IOException {
        server = serve(request -> respond(request, 200, "not json"));
        var guard = new ServiceFailureGuard(2);
        var client = new HttpJsonClient(url(), 1000, 0, guard);

        assertThatThrownBy(() -> client.getJson("/api?q=x"))
                .isInstanceOf(ServiceCallException.class);
        assertThat(guard.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void clientErrorDoesNotCountAsGuardFailure() throws IOException {
        server = serve(request -> respond(request, 404, "{\"error\":\"missing\"}"));
        var guard = new ServiceFailureGuard(1);
        var client = new HttpJsonClient(url(), 1000, 0, guard);

        assertThatThrownBy(() -> client.getJson("/api?q=x"))
                .isInstanceOf(ServiceCallException.class);
        assertThat(guard.consecutiveFailures()).isZero();
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThatThrownBy(() -> new HttpJsonClient(null, 1000, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpJsonClient("http://localhost", 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpJsonClient("http://localhost", 1000, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardAbortsBatchAfterConsecutiveFailuresAndResetsOnSuccess() {
        var guard = new ServiceFailureGuard(2);

        guard.recordFailure();
        assertThatThrownBy(guard::recordFailure).isInstanceOf(ServiceUnavailableException.class);

        guard.recordSuccess();
        guard.recordFailure();
        assertThat(guard.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void guardFailureFromHttpClientPropagatesAsFatal() throws IOException {
        server = serve(request -> respond(request, 500, "down"));
        var guard = new ServiceFailureGuard(1);
        var client = new HttpJsonClient(url(), 1000, 0, guard);

        assertThatThrownBy(() -> client.getJson("/api?q=x"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private String url() throws IOException {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HttpServer serve(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/", handler);
        created.start();
        return created;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
