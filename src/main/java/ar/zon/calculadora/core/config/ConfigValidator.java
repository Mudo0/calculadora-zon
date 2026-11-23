package ar.zon.calculadora.core.config;

import ar.zon.calculadora.core.errors.ErrorCode;

import java.util.Set;

/** Valida la configuración ya construida, sin leer archivos. */
public final class ConfigValidator {

    private static final Set<String> OUTPUT_FORMATS = Set.of("auto", "csv", "xlsx");
    private static final Set<String> GEOCODER_MODES = Set.of("PHOTON", "COORDINATES", "FILE");
    private static final Set<String> DISTANCE_MODES = Set.of("ROUTE", "HAVERSINE", "BOTH");
    private static final Set<String> FALLBACK_MODES = Set.of("HAVERSINE", "NONE");
    private static final Set<String> SERVICE_MODES = Set.of("EXTERNAL", "BUNDLED");

    private ConfigValidator() {
    }

    /**
     * Comprueba modos, números y rangos de la configuración.
     *
     * @param config configuración a validar
     */
    public static void validate(CalculationConfig config) {
        requireChoice("output.format", config.output().format(), OUTPUT_FORMATS);
        requireChoice("geocoder.mode", config.geocoder().mode(), GEOCODER_MODES);
        requireChoice("distance.mode", config.distance().mode(), DISTANCE_MODES);
        requireChoice("distance.fallback", config.distance().fallback(), FALLBACK_MODES);
        requireChoice("services.mode", config.services().mode(), SERVICE_MODES);

        requirePositive("personas.min", config.validation().personasMin());
        requirePositive("personas.max", config.validation().personasMax());
        if (config.validation().personasMin() > config.validation().personasMax()) {
            throw invalid("personas.min no puede superar personas.max");
        }
        config.validation().perTransportMax().forEach((transport, max) -> requirePositive("personas.max." + transport, max));

        requireNonNegative("distance.decimals", config.distance().decimals());
        if (config.distance().decimals() > 6) {
            throw invalid("distance.decimals no puede superar 6");
        }
        requirePositive("geo.maxCandidates", config.geocoder().maxCandidates());
        requirePositive("geo.cache.maxEntries", config.geocoder().cacheMaxEntries());
        requirePositive("route.cache.maxEntries", config.distance().routeCacheMaxEntries());
        requirePositive("http.timeoutMs", config.http().timeoutMs());
        requireNonNegative("http.retries", config.http().retries());
        requirePositive("http.maxConsecutiveFailures", config.http().maxConsecutiveFailures());
        requirePositive("services.osrm.port", config.services().osrmPort());
        requirePositive("services.photon.port", config.services().photonPort());
        requirePositive("services.startTimeoutSeconds", config.services().startTimeoutSeconds());
        requirePositive("input.headerRow", config.input().headerRow());

        boolean hasLat = config.event().destinationLat() != null;
        boolean hasLon = config.event().destinationLon() != null;
        if (hasLat != hasLon) {
            throw invalid("destination.lat y destination.lon deben indicarse juntos");
        }
        if (hasLat && (!Double.isFinite(config.event().destinationLat())
                || !Double.isFinite(config.event().destinationLon()))) {
            throw invalid("destination.lat/destination.lon deben ser números finitos");
        }
        if (hasLat && (Math.abs(config.event().destinationLat()) > 90
                || Math.abs(config.event().destinationLon()) > 180)) {
            throw invalid("destination.lat/destination.lon fuera de rango");
        }
    }

    /**
     * Comprueba que exista un destino configurable antes de iniciar el lote.
     *
     * @param config configuración a validar
     */
    public static void requireDestination(CalculationConfig config) {
        if (config.event().destinationLat() == null && config.event().destinationDefault().isBlank()) {
            throw new InvalidConfigurationException(ErrorCode.DESTINATION_NOT_RESOLVED,
                    "No se configuró el destino del evento");
        }
    }

    private static void requireChoice(String key, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw invalid(key + " tiene un valor desconocido: " + value);
        }
    }

    private static void requirePositive(String key, int value) {
        if (value < 1) {
            throw invalid(key + " debe ser mayor que cero");
        }
    }

    private static void requireNonNegative(String key, int value) {
        if (value < 0) {
            throw invalid(key + " no puede ser negativo");
        }
    }

    private static InvalidConfigurationException invalid(String detail) {
        return new InvalidConfigurationException(ErrorCode.INVALID_CONFIG, detail);
    }
}
