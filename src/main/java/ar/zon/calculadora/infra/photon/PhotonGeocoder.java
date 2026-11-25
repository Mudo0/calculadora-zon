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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Map<String, GeoPoint> pointsByName = new HashMap<>();
        Set<GeoPoint> namelessPoints = new HashSet<>();
        for (JsonNode feature : features) {
            String name = feature.path("properties").path("name").asText("");
            GeoCandidate candidate = toCandidate(feature, text, name);
            if (candidate == null) {
                continue;
            }
            // Photon no informa confianza: dos puntos con el mismo nombre son ambiguos.
            if (!name.isBlank()) {
                GeoPoint previous = pointsByName.putIfAbsent(TextNormalizer.normalize(name), candidate.point());
                if (previous != null && !previous.equals(candidate.point())) {
                    return List.of();
                }
            } else {
                namelessPoints.add(candidate.point());
            }
            candidates.add(candidate);
        }
        // Sin nombre no hay forma de desempatar: elegir el primero dependería del orden de Photon.
        if (namelessPoints.size() > 1 && candidates.stream().noneMatch(c -> c.score() == 2.0)) {
            return List.of();
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

    private static GeoCandidate toCandidate(JsonNode feature, String query, String name) {
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }
        double longitude = coordinates.get(0).asDouble();
        double latitude = coordinates.get(1).asDouble();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return null;
        }
        // La etiqueta de respaldo solo se muestra: sin nombre original no hay coincidencia exacta.
        String label = name.isBlank() ? query.trim() : name;
        double score = TextNormalizer.normalize(name).equals(TextNormalizer.normalize(query)) ? 2.0 : 1.0;
        return new GeoCandidate(label, new GeoPoint(latitude, longitude), score, PROVIDER);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
