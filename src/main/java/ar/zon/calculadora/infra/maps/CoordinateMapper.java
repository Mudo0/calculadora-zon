package ar.zon.calculadora.infra.maps;

import ar.zon.calculadora.core.distance.GeoPoint;

import java.util.List;

/** Convierte las coordenadas [lon, lat] usadas por Photon y OSRM. */
public final class CoordinateMapper {

    private CoordinateMapper() {
    }

    /**
     * Convierte una coordenada de Photon a punto del dominio.
     *
     * @param coordinates par [lon, lat]
     * @return punto con latitud y longitud en el orden del dominio
     */
    public static GeoPoint fromPhoton(List<Number> coordinates) {
        return fromLonLat(coordinates);
    }

    /**
     * Convierte una coordenada de OSRM a punto del dominio.
     *
     * @param coordinates par [lon, lat]
     * @return punto con latitud y longitud en el orden del dominio
     */
    public static GeoPoint fromOsrm(List<Number> coordinates) {
        return fromLonLat(coordinates);
    }

    private static GeoPoint fromLonLat(List<Number> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("La coordenada debe tener lon y lat");
        }
        return new GeoPoint(coordinates.get(1).doubleValue(), coordinates.get(0).doubleValue());
    }
}
