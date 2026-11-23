package ar.zon.calculadora.core.config;

import java.util.List;
import java.util.Map;

/** Configuración inmutable del cálculo, sin análisis de archivos. */
public record CalculationConfig(
        Output output,
        Input input,
        Columns columns,
        Event event,
        Validation validation,
        Defaults defaults,
        Geocoder geocoder,
        Distance distance,
        Http http,
        Services services,
        String factorsFile) {

    /** Opciones del informe de salida. */
    public record Output(String format, String csvDelimiter, String csvDecimal, boolean csvBom, String csvCharset) {
    }

    /** Opciones de lectura de la encuesta. */
    public record Input(String csvDelimiter, String csvCharset, String csvDecimal, String xlsxSheet, int headerRow) {
    }

    /** Aliases normalizados de las columnas de la encuesta. */
    public record Columns(List<String> personas, List<String> origen, List<String> destino,
                          List<String> transporte, List<String> combustible, List<String> evento) {
    }

    /** Destino y nombre del evento, comunes a todas las filas. */
    public record Event(String destinationDefault, Double destinationLat, Double destinationLon, String eventName) {
    }

    /** Límites de personas por viaje, con ajustes opcionales por transporte. */
    public record Validation(int personasMin, int personasMax, Map<String, Integer> perTransportMax) {
    }

    /** Valores por defecto cuando la fila no los aporta. */
    public record Defaults(String fuelAuto) {
    }

    /** Estrategia de geocodificación y opciones de búsqueda y caché. */
    public record Geocoder(String mode, String file, String biasLat, String biasLon, String bbox,
                           int maxCandidates, boolean cacheEnabled, int cacheMaxEntries) {
    }

    /** Modo de distancia, respaldo y precisión de escritura. */
    public record Distance(String mode, String fallback, int decimals,
                           boolean routeCacheEnabled, int routeCacheMaxEntries) {
    }

    /** Endpoints, tiempos de espera y reintentos de los servicios HTTP. */
    public record Http(String photonBaseUrl, String osrmBaseUrl, int timeoutMs, int retries,
                       int maxConsecutiveFailures, boolean healthcheckEnabled) {
    }

    /** Modo y rutas de los servicios locales. */
    public record Services(String mode, String host, int osrmPort, int photonPort, int startTimeoutSeconds,
                           String osrmExecutable, String osrmData, String osrmAlgorithm,
                           String photonJava, String photonJar, String photonDataDir, String photonHeap) {
    }
}
