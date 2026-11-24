package ar.zon.calculadora.infra.maps;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.core.geocode.Geocoder;

import java.util.List;

/** Resuelve orígenes que ya vienen como ``lat,lon`` en la encuesta. */
public final class CoordinatesGeocoder implements Geocoder {

    @Override
    public List<GeoCandidate> geocode(String text) {
        if (text == null) {
            return List.of();
        }
        String[] parts = text.split(",", -1);
        if (parts.length != 2) {
            return List.of();
        }
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(parts[0].trim());
            longitude = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            return List.of();
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            return List.of();
        }
        return List.of(new GeoCandidate(text.trim(), new GeoPoint(latitude, longitude), 1.0, "COORDINATES"));
    }
}
