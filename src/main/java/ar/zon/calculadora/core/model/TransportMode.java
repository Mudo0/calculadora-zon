package ar.zon.calculadora.core.model;

import java.util.Locale;
import java.util.Map;

/** Modos de transporte canónicos y sus alias de entrada. */
public enum TransportMode {

    AUTO,
    MOTO,
    COLECTIVO,
    PIE,
    BICI,
    MISSING_TRANSPORT,
    UNKNOWN_TRANSPORT;

    private static final Map<String, TransportMode> ALIASES = Map.ofEntries(
            Map.entry("auto", AUTO),
            Map.entry("coche", AUTO),
            Map.entry("automovil", AUTO),
            Map.entry("moto", MOTO),
            Map.entry("motocicleta", MOTO),
            Map.entry("colectivo", COLECTIVO),
            Map.entry("omnibus", COLECTIVO),
            Map.entry("bondi", COLECTIVO),
            Map.entry("bus", COLECTIVO),
            Map.entry("caminando", PIE),
            Map.entry("a pie", PIE),
            Map.entry("pie", PIE),
            Map.entry("bicicleta", BICI),
            Map.entry("bici", BICI));

    /**
     * Normaliza un valor de entrada a un modo de transporte.
     *
     * @param raw valor leído de la encuesta
     * @return modo canónico, o MISSING_TRANSPORT/UNKNOWN_TRANSPORT según corresponda
     */
    public static TransportMode from(String raw) {
        String normalized = TextNormalizer.normalize(raw);
        if (normalized.isEmpty()) {
            return MISSING_TRANSPORT;
        }
        return ALIASES.getOrDefault(normalized.toLowerCase(Locale.ROOT), UNKNOWN_TRANSPORT);
    }
}
