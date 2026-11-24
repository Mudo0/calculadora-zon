package ar.zon.calculadora.support;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.route.RouteResult;
import ar.zon.calculadora.core.route.Router;

import java.util.Optional;

/** Router determinista para pruebas; por defecto no encuentra rutas. */
public final class FakeRouteCalculator implements Router {

    private RouteResult result;
    private boolean failing;
    private int calls;

    /**
     * Define el resultado de todas las rutas.
     *
     * @param km distancia devuelta
     * @return este router
     */
    public FakeRouteCalculator returns(double km) {
        result = new RouteResult(km);
        return this;
    }

    /** Hace que todas las búsquedas fallen con una excepción. */
    public void fail() {
        failing = true;
    }

    /** @return número de llamadas recibidas */
    public int calls() {
        return calls;
    }

    @Override
    public Optional<RouteResult> route(GeoPoint origin, GeoPoint destination) {
        calls++;
        if (failing) {
            throw new IllegalStateException("fake router failure");
        }
        return Optional.ofNullable(result);
    }
}
