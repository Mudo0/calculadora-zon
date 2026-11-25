package ar.zon.calculadora.infra.osrm;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.route.RouteResult;
import ar.zon.calculadora.core.route.Router;
import ar.zon.calculadora.infra.http.HttpJsonClient;
import ar.zon.calculadora.infra.http.ServiceCallException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Optional;

/** Router OSRM que consume el servicio de conduccion y convierte metros a kilometros. */
public final class OsrmRouteCalculator implements Router {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpJsonClient client;

    /**
     * Crea el adaptador con el cliente HTTP compartido.
     *
     * @param client cliente HTTP del servicio OSRM
     */
    public OsrmRouteCalculator(HttpJsonClient client) {
        this.client = client;
    }

    @Override
    public Optional<RouteResult> route(GeoPoint origin, GeoPoint destination) {
        String path = String.format(Locale.ROOT,
                "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                origin.longitude(), origin.latitude(), destination.longitude(), destination.latitude());
        JsonNode json;
        try {
            json = client.getJson(path);
        } catch (ServiceCallException e) {
            // OSRM responde 400 con {"code":"NoRoute"} cuando no hay ruta entre los puntos.
            if (isNoRoute(e)) {
                return Optional.empty();
            }
            throw e;
        }
        if (json == null || !"Ok".equals(json.path("code").asText())) {
            return Optional.empty();
        }
        JsonNode routes = json.path("routes");
        if (!routes.isArray() || routes.isEmpty()) {
            return Optional.empty();
        }
        double meters = routes.get(0).path("distance").asDouble(Double.NaN);
        if (!Double.isFinite(meters) || meters < 0) {
            return Optional.empty();
        }
        return Optional.of(new RouteResult(meters / 1000.0));
    }

    private static boolean isNoRoute(ServiceCallException failure) {
        if (failure.statusCode() != 400 || failure.responseBody() == null) {
            return false;
        }
        try {
            return "NoRoute".equals(MAPPER.readTree(failure.responseBody()).path("code").asText());
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
