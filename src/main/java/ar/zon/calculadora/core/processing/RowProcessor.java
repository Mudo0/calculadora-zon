package ar.zon.calculadora.core.processing;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.distance.DistanceResult;
import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.distance.HaversineCalculator;
import ar.zon.calculadora.core.emissions.ConsumptionCalculator;
import ar.zon.calculadora.core.emissions.EmissionCalculator;
import ar.zon.calculadora.core.emissions.EmissionFactorProvider;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.geocode.CandidateSelector;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.ingest.RowAnalysis;
import ar.zon.calculadora.core.ingest.RowRules;
import ar.zon.calculadora.core.model.NormalizedTrip;
import ar.zon.calculadora.core.model.RowResult;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.route.Router;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Aplica el orden de reglas de la sección 9.5 usando solo puertos del núcleo. */
public final class RowProcessor {

    private final CalculationConfig config;
    private final Geocoder geocoder;
    private final Router router;
    private final EmissionFactorProvider factors;

    /**
     * Crea el procesador con los puertos ya compuestos por la capa externa.
     *
     * @param config configuración del lote
     * @param geocoder puerto de geocodificación
     * @param router puerto de rutas
     * @param factors proveedor de factores
     */
    public RowProcessor(CalculationConfig config, Geocoder geocoder, Router router,
                        EmissionFactorProvider factors) {
        this.config = config;
        this.geocoder = geocoder;
        this.router = router;
        this.factors = factors;
    }

    /**
     * Procesa una fila desde su estado crudo hasta su resultado final.
     *
     * @param row fila mapeada a columnas lógicas
     * @param destination destino ya resuelto para el lote
     * @return resultado vacío cuando la fila está completamente vacía
     */
    public Optional<RowResult> process(SurveyRow row, GeoPoint destination) {
        if (isFullyEmpty(row)) {
            return Optional.empty();
        }

        RowAnalysis analysis = RowRules.analyze(row, config);
        if (analysis.status() == RowStatus.ERROR || analysis.status() == RowStatus.SKIPPED_NON_MOTORIZED) {
            return Optional.of(result(row, analysis.status(), analysis.error(), null, null));
        }

        List<ar.zon.calculadora.core.geocode.GeoCandidate> candidates;
        try {
            candidates = geocoder.geocode(row.cells().getOrDefault("origen", ""));
        } catch (RuntimeException e) {
            return Optional.of(result(row, RowStatus.ERROR, ErrorCode.GEOCODER_ERROR, null, null));
        }
        var selected = CandidateSelector.select(candidates);
        if (selected.isEmpty()) {
            return Optional.of(result(row, RowStatus.ERROR, ErrorCode.LOCATION_NOT_FOUND, null, null));
        }
        GeoPoint origin = selected.get().point();

        DistanceOutcome distance = resolveDistance(origin, destination);
        if (distance.error() != null) {
            return Optional.of(result(row, RowStatus.ERROR, distance.error(), origin, null));
        }

        var factor = factors.find(analysis.transporte().name().toLowerCase(Locale.ROOT),
                analysis.fuel().name().toLowerCase(Locale.ROOT)).orElse(null);
        if (factor == null) {
            return Optional.of(result(row, RowStatus.ERROR, ErrorCode.FACTOR_NOT_FOUND, origin, null));
        }

        var consumption = ConsumptionCalculator.calculate(distance.value().km(), factor);
        var emission = EmissionCalculator.calculate(distance.value().km(), analysis.personas(), factor).orElseThrow();
        NormalizedTrip trip = new NormalizedTrip(row.number(), analysis.transporte(), analysis.fuel(),
                analysis.fuelInferred(), analysis.personas(), origin, destination);
        RowStatus status = distance.value().fallback() ? RowStatus.OK_FALLBACK_HAVERSINE : RowStatus.OK;
        return Optional.of(new RowResult(row.number(), status, null, trip,
                distance.value(), consumption, emission));
    }

    private DistanceOutcome resolveDistance(GeoPoint origin, GeoPoint destination) {
        String mode = config.distance().mode();
        boolean routePrimary = mode.equals("ROUTE") || mode.equals("BOTH");
        if (!routePrimary) {
            return new DistanceOutcome(new DistanceResult(HaversineCalculator.distanceKm(origin, destination), false), null);
        }

        Optional<ar.zon.calculadora.core.route.RouteResult> route;
        try {
            route = router.route(origin, destination);
        } catch (RuntimeException e) {
            if (!fallbackEnabled()) {
                return new DistanceOutcome(null, ErrorCode.ROUTER_ERROR);
            }
            route = Optional.empty();
        }
        if (route.isPresent()) {
            return new DistanceOutcome(new DistanceResult(route.get().km(), false), null);
        }
        if (fallbackEnabled()) {
            return new DistanceOutcome(new DistanceResult(HaversineCalculator.distanceKm(origin, destination), true), null);
        }
        return new DistanceOutcome(null, ErrorCode.ROUTE_NOT_FOUND);
    }

    private boolean fallbackEnabled() {
        return config.distance().fallback().equals("HAVERSINE");
    }

    private static RowResult result(SurveyRow row, RowStatus status, ErrorCode error,
                                    GeoPoint origin, DistanceResult distance) {
        return new RowResult(row.number(), status, error, null, distance, null, null);
    }

    private static boolean isFullyEmpty(SurveyRow row) {
        return row.cells().values().stream().allMatch(value -> value == null || value.isBlank());
    }

    private record DistanceOutcome(DistanceResult value, ErrorCode error) {
    }
}
