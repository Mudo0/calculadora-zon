package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.emissions.FactorBasis;
import ar.zon.calculadora.infra.config.PropertiesEmissionFactorProvider;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesEmissionFactorProviderTest {

    private static final String FACTORS = """
            factor.auto.nafta.basis=vehicle_km
            factor.auto.nafta.fuel_unit=l
            factor.auto.nafta.consumption_per_100km=10.0
            factor.auto.nafta.kgco2e_per_fuel_unit=2.0
            factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
            factor.colectivo.gasoil.basis=passenger_km
            factor.colectivo.gasoil.kgco2e_per_passenger_km=0.1
            factor.colectivo.gasoil.source=PLACEHOLDER_NOT_REAL
            """;

    @Test
    void findsVehicleFactorWithPlaceholderFlag() {
        var provider = new PropertiesEmissionFactorProvider(new StringReader(FACTORS));

        var factor = provider.find("auto", "nafta").orElseThrow();

        assertThat(factor.key()).isEqualTo("factor.auto.nafta");
        assertThat(factor.basis()).isEqualTo(FactorBasis.VEHICLE_KM);
        assertThat(factor.fuelUnit()).isEqualTo("l");
        assertThat(factor.consumptionPer100Km()).isEqualTo(10.0);
        assertThat(factor.kgPerFuelUnit()).isEqualTo(2.0);
        assertThat(factor.source()).isEqualTo("PLACEHOLDER_NOT_REAL");
        assertThat(factor.placeholder()).isTrue();
    }

    @Test
    void findsPassengerFactor() {
        var provider = new PropertiesEmissionFactorProvider(new StringReader(FACTORS));

        var factor = provider.find("colectivo", "gasoil").orElseThrow();

        assertThat(factor.basis()).isEqualTo(FactorBasis.PASSENGER_KM);
        assertThat(factor.kgPerPassengerKm()).isEqualTo(0.1);
        assertThat(factor.consumptionPer100Km()).isZero();
    }

    @Test
    void missingFactorReturnsEmpty() {
        var provider = new PropertiesEmissionFactorProvider(new StringReader(FACTORS));

        assertThat(provider.find("auto", "electrico")).isEmpty();
    }
}
