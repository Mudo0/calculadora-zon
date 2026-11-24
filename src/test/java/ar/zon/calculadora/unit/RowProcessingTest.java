package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.emissions.EmissionFactorProvider;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.model.RowResult;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.processing.RowProcessor;
import ar.zon.calculadora.core.route.Router;
import ar.zon.calculadora.core.route.RouteResult;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import ar.zon.calculadora.infra.config.PropertiesEmissionFactorProvider;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RowProcessingTest {

    private static final String FACTORS = """
            factor.auto.nafta.basis=vehicle_km
            factor.auto.nafta.fuel_unit=l
            factor.auto.nafta.consumption_per_100km=10.0
            factor.auto.nafta.kgco2e_per_fuel_unit=2.0
            factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
            """;

    private static final GeoPoint DESTINATION = new GeoPoint(-31.65, -64.43);

    @Test
    void ignoresFullyEmptyRows() {
        var geo = new FakeGeocoder();
        var result = processor("", geo, new FakeRouter(), FACTORS)
                .process(new SurveyRow(1, Map.of("transporte", "", "origen", "", "personas", "")), DESTINATION);

        assertThat(result).isEmpty();
        assertThat(geo.calls).isZero();
    }

    @Test
    void skippedRowStopsBeforeOtherValidationAndServices() {
        var geo = new FakeGeocoder();
        var result = processor("", geo, new FakeRouter(), FACTORS)
                .process(new SurveyRow(1, Map.of("transporte", "bici", "origen", "", "personas", "")), DESTINATION)
                .orElseThrow();

        assertThat(result.status()).isEqualTo(RowStatus.SKIPPED_NON_MOTORIZED);
        assertThat(result.error()).isNull();
        assertThat(geo.calls).isZero();
    }

    @Test
    void missingOriginStopsBeforeGeocoding() {
        var geo = new FakeGeocoder();
        var result = processor("", geo, new FakeRouter(), FACTORS)
                .process(new SurveyRow(1, Map.of("transporte", "auto", "personas", "2")), DESTINATION)
                .orElseThrow();

        assertThat(result.error()).isEqualTo(ErrorCode.MISSING_ORIGIN);
        assertThat(geo.calls).isZero();
    }

    @Test
    void unknownLocationStopsBeforeRouting() {
        var router = new FakeRouter();
        var result = processor("", new FakeGeocoder(), router, FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.error()).isEqualTo(ErrorCode.LOCATION_NOT_FOUND);
        assertThat(router.calls).isZero();
    }

    @Test
    void usesRouteDistanceAndResolvesFactor() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));
        var router = new FakeRouter();
        router.result = Optional.of(new RouteResult(50));

        var result = processor("", geo, router, FACTORS).process(validRow(), DESTINATION).orElseThrow();

        assertThat(result.status()).isEqualTo(RowStatus.OK);
        assertThat(result.distance().km()).isEqualTo(50);
        assertThat(result.distance().fallback()).isFalse();
        assertThat(result.emission().kgCo2e()).isEqualTo(10.0);
        assertThat(result.emission().placeholder()).isTrue();
    }

    @Test
    void fallsBackToHaversineWhenRouteIsMissing() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));

        var result = processor("distance.mode=ROUTE\ndistance.fallback=HAVERSINE", geo, new FakeRouter(), FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.status()).isEqualTo(RowStatus.OK_FALLBACK_HAVERSINE);
        assertThat(result.distance().fallback()).isTrue();
        assertThat(result.distance().km()).isGreaterThan(10);
    }

    @Test
    void keepsRouterErrorAsFallbackReasonWhenFallbackIsEnabled() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));
        var router = new FakeRouter();
        router.failure = new IllegalStateException("router down");

        var result = processor("distance.fallback=HAVERSINE", geo, router, FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.status()).isEqualTo(RowStatus.OK_FALLBACK_HAVERSINE);
        assertThat(result.fallbackReason()).isEqualTo("ROUTER_ERROR");
    }

    @Test
    void reportsRouteNotFoundWhenFallbackIsDisabled() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));

        var result = processor("distance.fallback=NONE", geo, new FakeRouter(), FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.status()).isEqualTo(RowStatus.ERROR);
        assertThat(result.error()).isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
        assertThat(result.emission()).isNull();
    }

    @Test
    void usesHaversineWithoutCallingRouter() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));
        var router = new FakeRouter();

        var result = processor("distance.mode=HAVERSINE", geo, router, FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.status()).isEqualTo(RowStatus.OK);
        assertThat(result.distance().fallback()).isFalse();
        assertThat(router.calls).isZero();
    }

    @Test
    void reportsGeocoderError() {
        var geo = new FakeGeocoder();
        geo.failure = new IllegalStateException("service down");

        var result = processor("", geo, new FakeRouter(), FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.error()).isEqualTo(ErrorCode.GEOCODER_ERROR);
    }

    @Test
    void reportsRouterErrorWhenFallbackIsDisabled() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));
        var router = new FakeRouter();
        router.failure = new IllegalStateException("router down");

        var result = processor("distance.fallback=NONE", geo, router, FACTORS)
                .process(validRow(), DESTINATION)
                .orElseThrow();

        assertThat(result.error()).isEqualTo(ErrorCode.ROUTER_ERROR);
    }

    @Test
    void reportsMissingFactorWithoutSilentZero() {
        var geo = new FakeGeocoder();
        geo.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));
        var router = new FakeRouter();
        router.result = Optional.of(new RouteResult(50));

        var result = processor("", geo, router, "").process(validRow(), DESTINATION).orElseThrow();

        assertThat(result.error()).isEqualTo(ErrorCode.FACTOR_NOT_FOUND);
        assertThat(result.emission()).isNull();
    }

    private static SurveyRow validRow() {
        return new SurveyRow(4, Map.of("transporte", "auto", "origen", "C\u00f3rdoba", "personas", "2"));
    }

    private static RowProcessor processor(String properties, Geocoder geocoder, Router router, String factors) {
        CalculationConfig config = new PropertiesConfigLoader().load(new StringReader(properties));
        EmissionFactorProvider provider = new PropertiesEmissionFactorProvider(new StringReader(factors));
        return new RowProcessor(config, geocoder, router, provider);
    }

    private static class FakeGeocoder implements Geocoder {
        private final List<GeoCandidate> results = new ArrayList<>();
        private RuntimeException failure;
        private int calls;

        @Override
        public List<GeoCandidate> geocode(String text) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return results;
        }
    }

    private static class FakeRouter implements Router {
        private Optional<RouteResult> result = Optional.empty();
        private RuntimeException failure;
        private int calls;

        @Override
        public Optional<RouteResult> route(GeoPoint origin, GeoPoint destination) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
