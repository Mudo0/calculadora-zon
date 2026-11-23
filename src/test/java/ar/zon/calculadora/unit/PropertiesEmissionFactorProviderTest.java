package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.InvalidConfigurationException;
import ar.zon.calculadora.core.emissions.FactorBasis;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.infra.config.PropertiesEmissionFactorProvider;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsVehicleFactorWithoutRequiredEmissionAttribute() {
        String factors = """
                factor.auto.nafta.basis=vehicle_km
                factor.auto.nafta.fuel_unit=l
                factor.auto.nafta.consumption_per_100km=10.0
                factor.auto.nafta.source=PLACEHOLDER_NOT_REAL
                """;

        assertThatThrownBy(() -> new PropertiesEmissionFactorProvider(new StringReader(factors)))
                .isInstanceOf(InvalidConfigurationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_CONFIG);
    }

    @Test
    void rejectsPassengerFactorWithoutRequiredAttribute() {
        String factors = """
                factor.colectivo.gasoil.basis=passenger_km
                factor.colectivo.gasoil.source=PLACEHOLDER_NOT_REAL
                """;

        assertThatThrownBy(() -> new PropertiesEmissionFactorProvider(new StringReader(factors)))
                .isInstanceOf(InvalidConfigurationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_CONFIG);
    }

    @Test
    void rejectsNegativeAndNonFiniteNumbers() {
        assertThatThrownBy(() -> new PropertiesEmissionFactorProvider(new StringReader(
                FACTORS.replace("kgco2e_per_fuel_unit=2.0", "kgco2e_per_fuel_unit=-1.0"))))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new PropertiesEmissionFactorProvider(new StringReader(
                FACTORS.replace("kgco2e_per_fuel_unit=2.0", "kgco2e_per_fuel_unit=NaN"))))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new PropertiesEmissionFactorProvider(new StringReader(
                FACTORS.replace("kgco2e_per_fuel_unit=2.0", "kgco2e_per_fuel_unit=Infinity"))))
                .isInstanceOf(InvalidConfigurationException.class);
    }
}
