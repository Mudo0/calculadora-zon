package ar.zon.calculadora.core.route;

import ar.zon.calculadora.core.distance.GeoPoint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Caché LRU de rutas por coordenadas de origen y destino. */
public final class CachingRouter implements Router {

    private final Router delegate;
    private final Map<String, Optional<RouteResult>> cache;

    /**
     * Crea una caché acotada sobre otro router.
     *
     * @param delegate router real o de prueba
     * @param maxEntries cantidad máxima de entradas
     */
    public CachingRouter(Router delegate, int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("route.cache.maxEntries debe ser mayor que cero");
        }
        this.delegate = delegate;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<RouteResult>> eldest) {
                return size() > maxEntries;
            }
        });
    }

    @Override
    public Optional<RouteResult> route(GeoPoint origin, GeoPoint destination) {
        String key = String.format(Locale.ROOT, "%.7f,%.7f>%.7f,%.7f",
                origin.latitude(), origin.longitude(), destination.latitude(), destination.longitude());
        return cache.computeIfAbsent(key, ignored -> delegate.route(origin, destination));
    }
}
