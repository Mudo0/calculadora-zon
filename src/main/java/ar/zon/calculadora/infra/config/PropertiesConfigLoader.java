package ar.zon.calculadora.infra.config;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.config.ConfigValidator;
import ar.zon.calculadora.core.config.InvalidConfigurationException;
import ar.zon.calculadora.core.errors.ErrorCode;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Convierte un archivo .properties en la configuración inmutable del núcleo. */
public final class PropertiesConfigLoader {

    /**
     * Lee y valida la configuración de un lector de propiedades.
     *
     * @param reader lector de las propiedades
     * @return configuración validada, sin destino obligatorio aún
     */
    public CalculationConfig load(Reader reader) {
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IOException e) {
            throw new InvalidConfigurationException(ErrorCode.INVALID_CONFIG, "No se pudo leer la configuración: " + e.getMessage());
        }

        Map<String, Integer> perTransportMax = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("personas.max.")) {
                String transport = key.substring("personas.max.".length()).trim().toLowerCase(Locale.ROOT);
                perTransportMax.put(transport, intValue(properties, key, 5));
            }
        }

        CalculationConfig config = new CalculationConfig(
                new CalculationConfig.Output(
                        text(properties, "output.format", "auto"),
                        text(properties, "output.csv.delimiter", ";"),
                        text(properties, "output.csv.decimal", ","),
                        bool(properties, "output.csv.bom", true),
                        text(properties, "output.csv.charset", "UTF-8")),
                new CalculationConfig.Input(
                        text(properties, "input.csv.delimiter", "auto"),
                        text(properties, "input.csv.charset", "auto"),
                        text(properties, "input.csv.decimal", "auto"),
                        text(properties, "input.xlsx.sheet", "1"),
                        intValue(properties, "input.headerRow", 1)),
                new CalculationConfig.Columns(
                        aliases(properties, "columns.personas", "personas"),
                        aliases(properties, "columns.origen", "origen"),
                        aliases(properties, "columns.destino", "destino"),
                        aliases(properties, "columns.transporte", "transporte"),
                        aliases(properties, "columns.combustible", "combustible"),
                        aliases(properties, "columns.evento", "evento")),
                new CalculationConfig.Event(
                        text(properties, "destination.default", ""),
                        doubleOrNull(properties, "destination.lat"),
                        doubleOrNull(properties, "destination.lon"),
                        text(properties, "event.name", "")),
                new CalculationConfig.Validation(
                        intValue(properties, "personas.min", 1),
                        intValue(properties, "personas.max", 5),
                        Map.copyOf(perTransportMax)),
                new CalculationConfig.Defaults(text(properties, "default.fuel.auto", "NAFTA")),
                new CalculationConfig.Geocoder(
                        text(properties, "geocoder.mode", "PHOTON"),
                        text(properties, "geocoder.file", ""),
                        text(properties, "geo.bias.lat", ""),
                        text(properties, "geo.bias.lon", ""),
                        text(properties, "geo.bbox", ""),
                        intValue(properties, "geo.maxCandidates", 5),
                        bool(properties, "geo.cache.enabled", true),
                        intValue(properties, "geo.cache.maxEntries", 10000)),
                new CalculationConfig.Distance(
                        text(properties, "distance.mode", "ROUTE"),
                        text(properties, "distance.fallback", "HAVERSINE"),
                        intValue(properties, "distance.decimals", 2),
                        bool(properties, "route.cache.enabled", true),
                        intValue(properties, "route.cache.maxEntries", 10000)),
                new CalculationConfig.Http(
                        text(properties, "photon.baseUrl", "http://localhost:2322"),
                        text(properties, "osrm.baseUrl", "http://localhost:5000"),
                        intValue(properties, "http.timeoutMs", 3000),
                        intValue(properties, "http.retries", 1),
                        intValue(properties, "http.maxConsecutiveFailures", 20),
                        bool(properties, "healthcheck.enabled", true)),
                new CalculationConfig.Services(
                        text(properties, "services.mode", "EXTERNAL"),
                        text(properties, "services.host", "127.0.0.1"),
                        intValue(properties, "services.osrm.port", 5000),
                        intValue(properties, "services.photon.port", 2322),
                        intValue(properties, "services.startTimeoutSeconds", 120),
                        text(properties, "services.osrm.executable", "./runtime/osrm/osrm-routed"),
                        text(properties, "services.osrm.data", "./data/osrm/argentina.osrm"),
                        text(properties, "services.osrm.algorithm", "mld"),
                        text(properties, "services.photon.java", "./runtime/jre/bin/java"),
                        text(properties, "services.photon.jar", "./runtime/photon/photon.jar"),
                        text(properties, "services.photon.dataDir", "./data/photon"),
                        text(properties, "services.photon.heap", "")),
                text(properties, "factors.file", "./config/emission-factors.properties"));

        ConfigValidator.validate(config);
        return config;
    }

    private static String text(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key, fallback).trim();
        return value.isEmpty() ? fallback : value;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int intValue(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException(ErrorCode.INVALID_CONFIG, key + " debe ser un número entero");
        }
    }

    private static Double doubleOrNull(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException(ErrorCode.INVALID_CONFIG, key + " debe ser un número decimal");
        }
    }

    private static List<String> aliases(Properties properties, String key, String fallback) {
        String value = text(properties, key, fallback);
        List<String> aliases = new ArrayList<>();
        for (String alias : value.split(",")) {
            String trimmed = alias.trim();
            if (!trimmed.isEmpty()) {
                aliases.add(trimmed);
            }
        }
        return List.copyOf(aliases);
    }
}
