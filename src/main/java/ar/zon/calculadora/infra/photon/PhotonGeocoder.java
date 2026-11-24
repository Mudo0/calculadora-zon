package ar.zon.calculadora.infra.photon;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.model.TextNormalizer;
import ar.zon.calculadora.infra.http.HttpJsonClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Geocodificador Photon que convierte su GeoJSON en candidatos del dominio. */
public final class PhotonGeocoder implements Geocoder {

    private static final String PROVIDER = "PHOTON";

    private final HttpJsonClient client;
    private final String biasLat;
    private final String biasLon;
    private final String bbox;
    private final int maxCandidates;

    /**
     * Crea el adaptador con los parametros de busqueda configurados.
     *
     * @param client cliente HTTP compartido
     * @param biasLat latitud de sesgo, o {@code null} si no se configura
     * @param biasLon longitud de sesgo, o {@code null} si no se configura
     * @param bbox caja de busqueda {@code minLon,minLat,maxLon,maxLat}, o {@code null}
     * @param maxCandidates cantidad maxima de candidatos solicitados
     */
    public PhotonGeocoder(HttpJsonClient client, String biasLat, String biasLon, String bbox, int maxCandidates) {
        if (maxCandidates < 1) {
            throw new IllegalArgumentException("geo.maxCandidates debe ser mayor que cero");
        }
        this.client = client;
        this.biasLat = blankToNull(biasLat);
        this.biasLon = blankToNull(biasLon);
        this.bbox = blankToNull(bbox);
        this.maxCandidates = maxCandidates;
    }

    @Override
    public List<GeoCandidate> geocode(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        JsonNode json = client.getJson(query(text));
        if (json == null || !json.path("features").isArray()) {
            return List.of();
        }
        JsonNode features = json.path("features");
        List<GeoCandidate> candidates = new ArrayList<>();
        Map<String, GeoPoint> pointsByLabel = new HashMap<>();
        for (JsonNode feature : features) {
            GeoCandidate candidate = toCandidate(feature, text, 1.0);
            if (candidate == null) {
                continue;
            }
            // Photon no informa confianza: dos puntos con el mismo nombre son ambiguos.
            GeoPoint previous = pointsByLabel.putIfAbsent(
                    TextNormalizer.normalize(candidate.label()), candidate.point());
            if (previous != null && !previous.equals(candidate.point())) {
                return List.of();
            }
            candidates.add(candidate);
        }
        return List.copyOf(candidates);
    }

    private String query(String text) {
        StringBuilder path = new StringBuilder("/api?q=").append(encode(text.trim()))
                .append("&limit=").append(maxCandidates);
        if (biasLat != null && biasLon != null) {
            path.append("&lat=").append(encode(biasLat)).append("&lon=").append(encode(biasLon));
        }
        if (bbox != null) {
            path.append("&bbox=").append(encode(bbox));
        }
        return path.toString();
    }

    private static GeoCandidate toCandidate(JsonNode feature, String fallbackLabel, double score) {
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }
        double longitude = coordinates.get(0).asDouble();
        double latitude = coordinates.get(1).asDouble();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return null;
        }
        String label = feature.path("properties").path("name").asText("");
        if (label.isBlank()) {
            label = fallbackLabel.trim();
        }
        return new GeoCandidate(label, new GeoPoint(latitude, longitude), score, PROVIDER);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
