package ar.zon.calculadora.infra.config;

import ar.zon.calculadora.core.config.InvalidConfigurationException;
import ar.zon.calculadora.core.emissions.EmissionFactor;
import ar.zon.calculadora.core.emissions.EmissionFactorProvider;
import ar.zon.calculadora.core.emissions.FactorBasis;
import ar.zon.calculadora.core.errors.ErrorCode;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Lee factores con el patrón factor.transporte.combustible.atributo. */
public final class PropertiesEmissionFactorProvider implements EmissionFactorProvider {

    private static final String PREFIX = "factor.";
    private final Map<String, EmissionFactor> factors;

    /**
     * Carga los factores desde un lector de propiedades.
     *
     * @param reader lector de las propiedades de factores
     */
    public PropertiesEmissionFactorProvider(Reader reader) {
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IOException e) {
            throw new InvalidConfigurationException(ErrorCode.INVALID_CONFIG, "No se pudieron leer los factores: " + e.getMessage());
        }

        Map<String, Map<String, String>> attributes = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String[] parts = key.split("\\.");
            if (parts.length < 4) {
                continue;
            }
            String factorKey = String.join(".", parts[0], parts[1], parts[2]);
            String attribute = String.join(".", java.util.Arrays.copyOfRange(parts, 3, parts.length));
            attributes.computeIfAbsent(factorKey, ignored -> new HashMap<>())
                    .put(attribute, properties.getProperty(key).trim());
        }

        Map<String, EmissionFactor> loaded = new HashMap<>();
        attributes.forEach((key, values) -> {
            String basisText = values.get("basis");
            if (basisText == null) {
                return;
            }
            String source = values.getOrDefault("source", "");
            loaded.put(key, new EmissionFactor(
                    key,
                    FactorBasis.valueOf(basisText.toUpperCase(Locale.ROOT)),
                    values.getOrDefault("fuel_unit", ""),
                    number(values, "consumption_per_100km"),
                    number(values, "kgco2e_per_fuel_unit"),
                    number(values, "kgco2e_per_passenger_km"),
                    source,
                    source.startsWith("PLACEHOLDER")));
        });
        this.factors = Map.copyOf(loaded);
    }

    @Override
    public Optional<EmissionFactor> find(String transporte, String combustible) {
        String key = PREFIX + transporte.toLowerCase(Locale.ROOT) + "." + combustible.toLowerCase(Locale.ROOT);
        return Optional.ofNullable(factors.get(key));
    }

    private static double number(Map<String, String> values, String attribute) {
        String value = values.get(attribute);
        return value == null || value.isBlank() ? 0.0 : Double.parseDouble(value);
    }
}
