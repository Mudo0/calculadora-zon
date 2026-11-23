package ar.zon.calculadora.core.route;

import ar.zon.calculadora.core.distance.GeoPoint;

import java.util.Optional;

/** Puerto de cálculo de ruta entre dos puntos. */
public interface Router {

    /**
     * Busca una ruta.
     *
     * @param origin punto de origen
     * @param destination punto de destino
     * @return distancia de ruta, vacío si no existe
     */
    Optional<RouteResult> route(GeoPoint origin, GeoPoint destination);
}
