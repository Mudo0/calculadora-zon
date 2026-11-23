package ar.zon.calculadora.core.distance;

/** Distancia de línea recta sobre una esfera, sin servicios externos. */
public final class HaversineCalculator {

    /** Radio terrestre medio usado por convención en este proyecto, en kilómetros. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private HaversineCalculator() {
    }

    /**
     * Calcula la distancia entre dos puntos.
     *
     * @param origin punto de origen
     * @param destination punto de destino
     * @return kilómetros sin redondear
     */
    public static double distanceKm(GeoPoint origin, GeoPoint destination) {
        double lat1 = Math.toRadians(origin.latitude());
        double lat2 = Math.toRadians(destination.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(destination.longitude() - origin.longitude());

        double h = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
