package ar.zon.calculadora.core.geocode;

import ar.zon.calculadora.core.model.TextNormalizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Caché LRU de geocodificación por texto normalizado. */
public final class CachingGeocoder implements Geocoder {

    private final Geocoder delegate;
    private final Map<String, List<GeoCandidate>> cache;

    /**
     * Crea una caché acotada sobre otro geocodificador.
     *
     * @param delegate geocodificador real o de prueba
     * @param maxEntries cantidad máxima de entradas
     */
    public CachingGeocoder(Geocoder delegate, int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("geo.cache.maxEntries debe ser mayor que cero");
        }
        this.delegate = delegate;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<GeoCandidate>> eldest) {
                return size() > maxEntries;
            }
        });
    }

    @Override
    public List<GeoCandidate> geocode(String text) {
        String key = TextNormalizer.normalize(text);
        return cache.computeIfAbsent(key, ignored -> List.copyOf(delegate.geocode(text)));
    }
}
