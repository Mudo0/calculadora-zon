package ar.zon.calculadora.core.pipeline;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.distance.HaversineCalculator;
import ar.zon.calculadora.core.emissions.EmissionFactorProvider;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.geocode.CandidateSelector;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.model.NormalizedTrip;
import ar.zon.calculadora.core.model.RowResult;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.processing.RowProcessor;
import ar.zon.calculadora.core.route.Router;
import ar.zon.calculadora.core.summary.BatchSummary;
import ar.zon.calculadora.core.summary.SummaryAggregator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Ejecuta el lote completo reutilizando el procesador de filas y el agregador del núcleo. */
public final class CalculateSurveyBatchUseCase {

    private final CalculationConfig config;
    private final Geocoder geocoder;
    private final EmissionFactorProvider factors;
    private final RowProcessor processor;

    /**
     * Crea el caso de uso con los puertos ya seleccionados por la capa externa.
     *
     * @param config configuración del lote
     * @param geocoder puerto de geocodificación
     * @param router puerto de rutas
     * @param factors proveedor de factores
     */
    public CalculateSurveyBatchUseCase(CalculationConfig config, Geocoder geocoder, Router router,
                                       EmissionFactorProvider factors) {
        this.config = config;
        this.geocoder = geocoder;
        this.factors = factors;
        this.processor = new RowProcessor(config, geocoder, router, factors);
    }

    /**
     * Procesa las filas en forma incremental y entrega cada una al informe.
     *
     * @param rows filas leídas de la encuesta
     * @param destinationOverride destino de la CLI, o {@code null}
     * @param eventOverride nombre del evento de la CLI, o {@code null}
     * @param sink destino de cada fila del informe
     * @return resumen del lote
     * @throws BatchException si no se puede resolver el destino o escribir una fila
     */
    public BatchSummary run(Iterable<SurveyRow> rows, String destinationOverride, String eventOverride,
                            ReportSink sink) throws BatchException {
        ResolvedDestination destination = resolveDestination(destinationOverride);
        String event = eventOverride == null || eventOverride.isBlank()
                ? config.event().eventName()
                : eventOverride;

        SummaryAggregator.Accumulator accumulator = new SummaryAggregator.Accumulator();
        for (SurveyRow row : rows) {
            Optional<RowResult> processed = processor.process(row, destination.point());
            if (processed.isPresent()) {
                RowResult result = processed.get();
                accumulator.accept(result);
                sink.accept(row, toCells(row, result, destination, event));
            }
        }
        return accumulator.summary();
    }

    private ResolvedDestination resolveDestination(String override) throws BatchException {
        if (override != null && !override.isBlank()) {
            GeoPoint coordinates = parseCoordinates(override);
            if (coordinates != null) {
                return new ResolvedDestination(coordinates, override.trim());
            }
            return geocodeDestination(override);
        }
        if (config.event().destinationLat() != null && config.event().destinationLon() != null) {
            GeoPoint point = new GeoPoint(config.event().destinationLat(), config.event().destinationLon());
            return new ResolvedDestination(point, format(point.latitude(), 4) + "," + format(point.longitude(), 4));
        }
        String text = config.event().destinationDefault();
        if (text == null || text.isBlank()) {
            throw new BatchException(ErrorCode.DESTINATION_NOT_RESOLVED, "No se resolvió el destino del evento");
        }
        return geocodeDestination(text);
    }

    private ResolvedDestination geocodeDestination(String text) throws BatchException {
        try {
            var selected = CandidateSelector.select(geocoder.geocode(text));
            if (selected.isPresent()) {
                return new ResolvedDestination(selected.get().point(), text.trim());
            }
        } catch (RuntimeException e) {
            throw new BatchException(ErrorCode.DESTINATION_NOT_RESOLVED,
                    "No se pudo geocodificar el destino '" + text + "': " + e.getMessage());
        }
        throw new BatchException(ErrorCode.DESTINATION_NOT_RESOLVED,
                "No se pudo geocodificar el destino '" + text + "'");
    }

    private static GeoPoint parseCoordinates(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                return null;
            }
            return new GeoPoint(latitude, longitude);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> toCells(SurveyRow row, RowResult result,
                                        ResolvedDestination destination, String event) {
        Map<String, String> cells = new HashMap<>();
        String originInput = row.cells().getOrDefault("origen", "");
        cells.put("row_number", String.valueOf(result.number()));
        cells.put("status", result.status().name());
        cells.put("error_code", result.error() == null ? "" : result.error().name());
        cells.put("error_message", "");
        cells.put("evento", event == null ? "" : event);
        cells.put("origen_input", originInput);
        cells.put("origen_label", originInput);
        cells.put("destino_input", destination.input());
        cells.put("destino_label", destination.input());
        cells.put("destino_lat", format(destination.point().latitude(), 2));
        cells.put("destino_lon", format(destination.point().longitude(), 2));
        cells.put("distance_mode", result.status() == RowStatus.SKIPPED_NON_MOTORIZED
                ? "NOT_APPLICABLE" : config.distance().mode());
        if (result.status() == RowStatus.SKIPPED_NON_MOTORIZED) {
            // La sección 9.4 del plan exige ceros explícitos en estas filas.
            cells.put("distance_km", "0");
            cells.put("consumption_value", "0");
            cells.put("emissions_kgco2e", "0");
        }
        cells.put("distance_fallback_used", String.valueOf(result.distance() != null && result.distance().fallback()));
        cells.put("distance_fallback_reason",
                result.distance() != null && result.distance().fallback() ? "ROUTE_UNAVAILABLE" : "");

        NormalizedTrip trip = result.trip();
        if (trip != null) {
            cells.put("origen_lat", format(trip.origin().latitude(), 2));
            cells.put("origen_lon", format(trip.origin().longitude(), 2));
            cells.put("transporte_normalizado", trip.transporte().name());
            cells.put("combustible_normalizado", trip.fuel().name());
            cells.put("fuel_inferred", String.valueOf(trip.fuelInferred()));
            cells.put("personas", String.valueOf(trip.personas()));
            cells.put("distance_straight_km", format(HaversineCalculator.distanceKm(
                    trip.origin(), destination.point()), config.distance().decimals()));
            cells.put("emissions_basis", basis(trip));
        }
        if (result.distance() != null) {
            cells.put("distance_km", format(result.distance().km(), config.distance().decimals()));
        }
        if (result.consumption() != null) {
            if (result.consumption().amount() != null) {
                cells.put("consumption_value", format(result.consumption().amount(), config.distance().decimals()));
            }
            cells.put("consumption_unit", result.consumption().unit() == null ? "" : result.consumption().unit());
            cells.put("calculation_notes", result.consumption().note() == null ? "" : result.consumption().note());
        }
        if (result.emission() != null) {
            cells.put("emissions_kgco2e", format(result.emission().kgCo2e(), config.distance().decimals()));
            cells.put("factor_key", result.emission().factorKey() == null ? "" : result.emission().factorKey());
            cells.put("factor_source", result.emission().source() == null ? "" : result.emission().source());
            cells.put("factor_is_placeholder", String.valueOf(result.emission().placeholder()));
        }
        return cells;
    }

    private String basis(NormalizedTrip trip) {
        return factors.find(trip.transporte().name().toLowerCase(Locale.ROOT),
                        trip.fuel().name().toLowerCase(Locale.ROOT))
                .map(factor -> factor.basis().name())
                .orElse("");
    }

    private String format(double value, int decimals) {
        char separator = config.output().csvDecimal() == null || config.output().csvDecimal().isEmpty()
                ? ',' : config.output().csvDecimal().charAt(0);
        return String.format(Locale.ROOT, "%." + decimals + "f", value).replace('.', separator);
    }

    private record ResolvedDestination(GeoPoint point, String input) {
    }
}
