package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.emissions.EmissionFactorProvider;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.pipeline.CalculateSurveyBatchUseCase;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import ar.zon.calculadora.infra.config.PropertiesEmissionFactorProvider;
import ar.zon.calculadora.support.FakeGeocoder;
import ar.zon.calculadora.support.FakeRouteCalculator;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculateSurveyBatchUseCaseTest {

    @Test
    void calculatesHaversineRowsWithoutCallingRouter() throws Exception {
        var geocoder = new FakeGeocoder().put("-31.4,-64.2", new GeoPoint(-31.4, -64.2));
        var router = new FakeRouteCalculator();

        var outcome = useCase(config(true), geocoder, router)
                .run(List.of(row("-31.4,-64.2")), null, null);

        assertThat(router.calls()).isZero();
        assertThat(outcome.reportRows()).hasSize(1);
        Map<String, String> cells = outcome.reportRows().get(0);
        assertThat(cells).hasSize(30)
                .containsEntry("row_number", "1")
                .containsEntry("status", "OK")
                .containsEntry("evento", "Evento X")
                .containsEntry("origen_input", "-31.4,-64.2")
                .containsEntry("personas", "2")
                .containsEntry("transporte_normalizado", "AUTO")
                .containsEntry("combustible_normalizado", "NAFTA")
                .containsEntry("fuel_inferred", "false")
                .containsEntry("distance_mode", "HAVERSINE")
                .containsEntry("distance_fallback_used", "false");
        assertThat(cells.get("distance_km")).contains(",");
        assertThat(cells.get("destino_lat")).isEqualTo("-31,42");
        assertThat(outcome.summary().okRows()).isEqualTo(1);
        assertThat(outcome.summary().errorRows()).isZero();
        assertThat(outcome.summary().totalKm()).isPositive();
    }

    @Test
    void destinationOverrideIsResolvedOnceAndMissingDestinationIsFatal() throws Exception {
        var geocoder = new FakeGeocoder().put("-31.5,-64.5", new GeoPoint(-31.5, -64.5));
        var useCase = useCase(config(false), geocoder, new FakeRouteCalculator());

        var withOverride = useCase.run(List.of(row("-31.5,-64.5")), "-31.5,-64.5", null);

        assertThat(withOverride.reportRows().get(0)).containsEntry("destino_lat", "-31,50");
        assertThatThrownBy(() -> useCase.run(List.of(row("-31.5,-64.5")), null, null))
                .isInstanceOf(BatchException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DESTINATION_NOT_RESOLVED);
    }

    @Test
    void rowErrorsDoNotFailTheBatch() throws Exception {
        var outcome = useCase(config(true), new FakeGeocoder(), new FakeRouteCalculator())
                .run(List.of(row("Desconocido")), null, null);

        assertThat(outcome.reportRows().get(0))
                .containsEntry("status", "ERROR")
                .containsEntry("error_code", "LOCATION_NOT_FOUND");
        assertThat(outcome.summary().errorRows()).isEqualTo(1);
        assertThat(outcome.summary().okRows()).isZero();
    }

    private CalculateSurveyBatchUseCase useCase(CalculationConfig config, FakeGeocoder geocoder,
                                                FakeRouteCalculator router) {
        EmissionFactorProvider factors = new PropertiesEmissionFactorProvider(new StringReader("""
                factor.auto.nafta.basis=vehicle_km
                factor.auto.nafta.fuel_unit=l
                factor.auto.nafta.consumption_per_100km=10.0
                factor.auto.nafta.kgco2e_per_fuel_unit=2.0
                factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
                """));
        return new CalculateSurveyBatchUseCase(config, geocoder, router, factors);
    }

    private CalculationConfig config(boolean withDestination) {
        String destination = withDestination ? """
                destination.lat=-31.4201
                destination.lon=-64.1888
                """ : "destination.default=\n";
        return new PropertiesConfigLoader().load(new StringReader("""
                output.csv.decimal=,
                distance.mode=HAVERSINE
                distance.decimals=2
                geocoder.mode=COORDINATES
                event.name=Evento X
                default.fuel.auto=NAFTA
                """ + destination));
    }

    private SurveyRow row(String origin) {
        return new SurveyRow(1, Map.of(
                "personas", "2",
                "origen", origin,
                "transporte", "auto",
                "combustible", "nafta"));
    }
}
