package ar.zon.calculadora.support;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.model.TextNormalizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Geocoder determinista para pruebas, sin servicios externos. */
public final class FakeGeocoder implements Geocoder {

    private final Map<String, GeoPoint> points = new HashMap<>();
    private boolean failing;

    /**
     * Registra un punto para un texto de origen.
     *
     * @param text texto normalizado del origen
     * @param point coordenada devuelta
     * @return este geocoder
     */
    public FakeGeocoder put(String text, GeoPoint point) {
        points.put(TextNormalizer.normalize(text), point);
        return this;
    }

    /** Hace que todas las búsquedas fallen con una excepción. */
    public void fail() {
        failing = true;
    }

    @Override
    public List<GeoCandidate> geocode(String text) {
        if (failing) {
            throw new IllegalStateException("fake geocoder failure");
        }
        GeoPoint point = points.get(TextNormalizer.normalize(text));
        if (point == null) {
            return List.of();
        }
        return List.of(new GeoCandidate(text, point, 1.0, "fake"));
    }
}
