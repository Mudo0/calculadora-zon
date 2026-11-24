package ar.zon.calculadora.cli;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.config.ConfigValidator;
import ar.zon.calculadora.core.emissions.EmissionFactorProvider;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.pipeline.CalculateSurveyBatchUseCase;
import ar.zon.calculadora.core.route.Router;
import ar.zon.calculadora.core.summary.BatchSummary;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import ar.zon.calculadora.infra.config.PropertiesEmissionFactorProvider;
import ar.zon.calculadora.infra.csv.CsvReportWriter;
import ar.zon.calculadora.infra.csv.CsvRowReader;
import ar.zon.calculadora.infra.maps.CoordinatesGeocoder;
import ar.zon.calculadora.infra.maps.FileGeocoder;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Punto de entrada de la CLI del lote. */
public final class Main {

    /** Versión impresa por {@code --version}. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private static final String USAGE = """
            Uso: java -jar zon-calculadora.jar --input <archivo> --output <archivo> --config <archivo>
              [--destination <texto|lat,lon>] [--event <nombre>] [--services <external|bundled>]
              [--summary <archivo>] [--force] [--log-level <INFO|DEBUG|WARN|ERROR>] [--help] [--version]""";

    private static final Set<String> LOG_LEVELS = Set.of("INFO", "DEBUG", "WARN", "ERROR");

    private Main() {
    }

    /**
     * Ejecuta la CLI y termina el proceso con el código de salida del lote.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Ejecuta la CLI sin terminar el proceso; lo usan las pruebas.
     *
     * @param args argumentos de línea de comandos
     * @return 0 correcto, 1 errores de fila, 2 error fatal
     */
    static int run(String[] args) {
        if (args.length == 0) {
            System.out.println(USAGE);
            return 0;
        }
        try {
            Arguments parsed = parse(args);
            if (parsed.help()) {
                System.out.println(USAGE);
                return 0;
            }
            if (parsed.version()) {
                System.out.println(VERSION);
                return 0;
            }
            validateDistinctPaths(parsed);
            if (parsed.logLevel() != null && !LOG_LEVELS.contains(parsed.logLevel().toUpperCase(Locale.ROOT))) {
                throw new BatchException(ErrorCode.INVALID_CONFIG, "log-level no válido: " + parsed.logLevel());
            }
            if (!parsed.output().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                throw new BatchException(ErrorCode.UNSUPPORTED_FORMAT, "Stage 2 solo genera informes .csv");
            }

            CalculationConfig config = loadConfig(parsed.config(), parsed.services());
            ConfigValidator.validate(config);
            if (config.services().mode().equals("BUNDLED")) {
                throw new BatchException(ErrorCode.SERVICE_UNAVAILABLE, "Stage 2 no incluye servicios locales");
            }
            if (!config.distance().mode().equals("HAVERSINE")) {
                throw new BatchException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Stage 2 solo funciona con distance.mode=HAVERSINE");
            }
            Geocoder geocoder = selectGeocoder(config);
            Router router = (origin, destination) -> Optional.empty();
            EmissionFactorProvider factors = loadFactors(config);

            System.err.println("Leyendo y procesando " + parsed.input());
            BatchSummary summary;
            try (var rows = new CsvRowReader().read(parsed.input(), config);
                 var report = new CsvReportWriter().open(parsed.output(), config, parsed.force())) {
                summary = new CalculateSurveyBatchUseCase(config, geocoder, router, factors)
                        .run(rows, parsed.destination(), parsed.event(), report::writeRow);
                report.commit();
            }
            System.out.println(SummaryRenderer.console(summary));
            if (parsed.summary() != null) {
                writeSummary(parsed.summary(), summary);
            }
            System.err.println("Informe escrito en " + parsed.output());
            return summary.errorRows() > 0 ? 1 : 0;
        } catch (BatchException e) {
            System.err.println("ERROR " + e.code() + ": " + e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("ERROR " + ErrorCode.INVALID_CONFIG + ": " + e.getMessage());
            return 2;
        }
    }

    private static Arguments parse(String[] args) throws BatchException {
        Path input = null;
        Path output = null;
        Path config = null;
        Path summary = null;
        String destination = null;
        String event = null;
        String services = null;
        String logLevel = null;
        boolean force = false;
        boolean help = false;
        boolean version = false;

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            switch (argument) {
                case "--input" -> input = Path.of(value(args, ++i, argument));
                case "--output" -> output = Path.of(value(args, ++i, argument));
                case "--config" -> config = Path.of(value(args, ++i, argument));
                case "--summary" -> summary = Path.of(value(args, ++i, argument));
                case "--destination" -> destination = value(args, ++i, argument);
                case "--event" -> event = value(args, ++i, argument);
                case "--services" -> services = value(args, ++i, argument);
                case "--log-level" -> logLevel = value(args, ++i, argument);
                case "--force" -> force = true;
                case "--help" -> help = true;
                case "--version" -> version = true;
                default -> throw new BatchException(ErrorCode.INVALID_CONFIG, "Opción desconocida: " + argument);
            }
        }
        if (!help && !version && (input == null || output == null || config == null)) {
            throw new BatchException(ErrorCode.INVALID_CONFIG, "--input, --output y --config son obligatorios");
        }
        return new Arguments(input, output, config, destination, event, services, summary,
                force, logLevel, help, version);
    }

    private static String value(String[] args, int index, String flag) throws BatchException {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new BatchException(ErrorCode.INVALID_CONFIG, "Falta el valor de " + flag);
        }
        return args[index];
    }

    private static CalculationConfig loadConfig(Path path, String services) throws BatchException {
        try {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            if (services != null) {
                properties.setProperty("services.mode", services.toUpperCase(Locale.ROOT));
            }
            StringWriter serialized = new StringWriter();
            properties.store(serialized, null);
            return new PropertiesConfigLoader().load(new StringReader(serialized.toString()));
        } catch (Exception e) {
            throw new BatchException(ErrorCode.INVALID_CONFIG, "No se pudo leer " + path + ": " + e.getMessage());
        }
    }

    private static EmissionFactorProvider loadFactors(CalculationConfig config) throws BatchException {
        Path path = Path.of(config.factorsFile());
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new PropertiesEmissionFactorProvider(reader);
        } catch (Exception e) {
            throw new BatchException(ErrorCode.INVALID_CONFIG, "No se pudieron leer los factores: " + path);
        }
    }

    private static Geocoder selectGeocoder(CalculationConfig config) throws BatchException {
        return switch (config.geocoder().mode()) {
            case "COORDINATES" -> new CoordinatesGeocoder();
            case "FILE" -> {
                try {
                    yield new FileGeocoder(Path.of(config.geocoder().file()));
                } catch (BatchException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BatchException(ErrorCode.INVALID_CONFIG, "geocoder.file no es válido");
                }
            }
            default -> throw new BatchException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Stage 2 no incluye el geocoder " + config.geocoder().mode());
        };
    }

    private static void validateDistinctPaths(Arguments args) throws BatchException {
        if (args.summary() == null) {
            return;
        }
        Path summary = absolute(args.summary());
        if (summary.equals(absolute(args.input())) || summary.equals(absolute(args.output()))
                || summary.equals(absolute(args.config()))) {
            throw new BatchException(ErrorCode.INVALID_CONFIG,
                    "--summary no puede apuntar a la entrada, la salida ni la configuración");
        }
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void writeSummary(Path path, BatchSummary summary) throws BatchException {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, SummaryRenderer.json(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BatchException(ErrorCode.OUTPUT_WRITE_ERROR, "No se pudo escribir " + path);
        }
    }

    private record Arguments(Path input, Path output, Path config, String destination, String event,
                             String services, Path summary, boolean force, String logLevel,
                             boolean help, boolean version) {
    }
}
