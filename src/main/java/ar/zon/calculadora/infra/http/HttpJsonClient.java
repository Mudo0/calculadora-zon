package ar.zon.calculadora.infra.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Cliente HTTP de solo lectura con timeout, reintentos acotados y guardia de fallos del servicio. */
public final class HttpJsonClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final String baseUrl;
    private final int timeoutMs;
    private final int retries;
    private final ServiceFailureGuard guard;

    /**
     * Crea el cliente para un servicio configurado.
     *
     * @param baseUrl URL base del servicio, sin la ruta del recurso
     * @param timeoutMs timeout por intento en milisegundos
     * @param retries reintentos además del primer intento
     * @param guard guardia de fallos consecutivos del servicio, o {@code null}
     */
    public HttpJsonClient(String baseUrl, int timeoutMs, int retries, ServiceFailureGuard guard) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("http.baseUrl es obligatoria");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("http.timeoutMs debe ser mayor que cero");
        }
        if (retries < 0) {
            throw new IllegalArgumentException("http.retries no puede ser negativo");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = normalized;
        this.timeoutMs = timeoutMs;
        this.retries = retries;
        this.guard = guard;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    /**
     * Obtiene y parsea un documento JSON.
     *
     * @param pathAndQuery ruta y parámetros, empezando por {@code /}
     * @return documento parseado
     * @throws ServiceCallException si se agotan los reintentos
     * @throws ar.zon.calculadora.core.errors.ServiceUnavailableException si se abre el disyuntor
     */
    public JsonNode getJson(String pathAndQuery) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        ServiceCallException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 500) {
                    last = new ServiceCallException("Servicio devolvió " + response.statusCode(), null);
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ServiceCallException("Servicio devolvió " + response.statusCode(), null,
                            response.statusCode(), response.body());
                }
                JsonNode json = MAPPER.readTree(response.body());
                if (guard != null) {
                    guard.recordSuccess();
                }
                return json;
            } catch (IOException e) {
                last = new ServiceCallException("No se pudo consultar el servicio", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceCallException("Consulta interrumpida", e);
            }
        }
        if (guard != null) {
            guard.recordFailure();
        }
        throw last == null ? new ServiceCallException("Servicio no disponible", null) : last;
    }
}
