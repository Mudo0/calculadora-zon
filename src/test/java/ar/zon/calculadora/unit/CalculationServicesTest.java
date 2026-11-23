package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.distance.HaversineCalculator;
import ar.zon.calculadora.core.emissions.ConsumptionCalculator;
import ar.zon.calculadora.core.emissions.EmissionCalculator;
import ar.zon.calculadora.core.emissions.EmissionFactor;
import ar.zon.calculadora.core.emissions.FactorBasis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalculationServicesTest {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private static EmissionFactor vehicle(String fuelUnit, double consumption, double kgPerUnit) {
        return new EmissionFactor("factor.auto.nafta", FactorBasis.VEHICLE_KM, fuelUnit,
                consumption, kgPerUnit, 0.0, "PLACEHOLDER_NOT_REAL", true);
    }

    @Test
    void calculatesKnownHaversineDistance() {
        double km = HaversineCalculator.distanceKm(new GeoPoint(0, 0), new GeoPoint(0, 1));

        assertThat(km).isCloseTo(Math.PI * EARTH_RADIUS_KM / 180, within(0.0001));
    }

    @Test
    void calculatesAutoNaftaHandCase() {
        var factor = vehicle("l", 10.0, 2.0);

        var consumption = ConsumptionCalculator.calculate(50, factor);
        var emission = EmissionCalculator.calculate(50, 1, factor).orElseThrow();

        assertThat(consumption.amount()).isEqualTo(5.0);
        assertThat(consumption.unit()).isEqualTo("l");
        assertThat(emission.kgCo2e()).isEqualTo(10.0);
        assertThat(emission.placeholder()).isTrue();
    }

    @Test
    void calculatesAutoGncHandCase() {
        var factor = vehicle("m3", 10.0, 2.0);

        var consumption = ConsumptionCalculator.calculate(100, factor);
        var emission = EmissionCalculator.calculate(100, 1, factor).orElseThrow();

        assertThat(consumption.amount()).isEqualTo(10.0);
        assertThat(consumption.unit()).isEqualTo("m3");
        assertThat(emission.kgCo2e()).isEqualTo(20.0);
    }

    @Test
    void calculatesMotoNaftaHandCase() {
        var factor = vehicle("l", 4.0, 2.0);

        var consumption = ConsumptionCalculator.calculate(25, factor);
        var emission = EmissionCalculator.calculate(25, 1, factor).orElseThrow();

        assertThat(consumption.amount()).isEqualTo(1.0);
        assertThat(emission.kgCo2e()).isEqualTo(2.0);
    }

    @Test
    void calculatesColectivoHandCaseWithoutConsumption() {
        var factor = new EmissionFactor("factor.colectivo.gasoil", FactorBasis.PASSENGER_KM, "",
                0.0, 0.0, 0.1, "PLACEHOLDER_NOT_REAL", true);

        var consumption = ConsumptionCalculator.calculate(40, factor);
        var emission = EmissionCalculator.calculate(40, 3, factor).orElseThrow();

        assertThat(consumption.amount()).isNull();
        assertThat(consumption.note()).isEqualTo("consumption_not_applicable");
        assertThat(emission.kgCo2e()).isEqualTo(12.0);
    }

    @Test
    void missingFactorHasNoEmissionResult() {
        var provider = new ar.zon.calculadora.infra.config.PropertiesEmissionFactorProvider(
                new java.io.StringReader(""));

        assertThat(provider.find("auto", "nafta")).isEmpty();
    }
}
