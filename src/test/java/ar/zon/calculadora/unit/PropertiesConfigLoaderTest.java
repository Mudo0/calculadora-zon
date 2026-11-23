package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.ConfigValidator;
import ar.zon.calculadora.core.config.InvalidConfigurationException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesConfigLoaderTest {

    @Test
    void loadsDefaultsForEmptyProperties() {
        var config = new PropertiesConfigLoader().load(new StringReader(""));

        assertThat(config.output().format()).isEqualTo("auto");
        assertThat(config.output().csvDelimiter()).isEqualTo(";");
        assertThat(config.output().csvDecimal()).isEqualTo(",");
        assertThat(config.output().csvBom()).isTrue();
        assertThat(config.input().headerRow()).isEqualTo(1);
        assertThat(config.columns().personas()).containsExactly("personas");
        assertThat(config.validation().personasMin()).isEqualTo(1);
        assertThat(config.validation().personasMax()).isEqualTo(5);
        assertThat(config.defaults().fuelAuto()).isEqualTo("NAFTA");
        assertThat(config.geocoder().mode()).isEqualTo("PHOTON");
        assertThat(config.distance().mode()).isEqualTo("ROUTE");
        assertThat(config.distance().fallback()).isEqualTo("HAVERSINE");
        assertThat(config.distance().decimals()).isEqualTo(2);
        assertThat(config.http().timeoutMs()).isEqualTo(3000);
        assertThat(config.services().mode()).isEqualTo("EXTERNAL");
        assertThat(config.factorsFile()).isEqualTo("./config/emission-factors.properties");
    }

    @Test
    void loadsOverridesAndColumnAliases() {
        String properties = """
                output.csv.delimiter=,
                personas.max=8
                personas.max.colectivo=6
                columns.transporte=transporte, medio,medio de transporte
                destination.default=Cordoba
                destination.lat=-31.4201
                destination.lon=-64.1888
                """;

        var config = new PropertiesConfigLoader().load(new StringReader(properties));

        assertThat(config.output().csvDelimiter()).isEqualTo(",");
        assertThat(config.validation().personasMax()).isEqualTo(8);
        assertThat(config.validation().perTransportMax()).containsEntry("colectivo", 6);
        assertThat(config.columns().transporte())
                .isEqualTo(List.of("transporte", "medio", "medio de transporte"));
        assertThat(config.event().destinationLat()).isEqualTo(-31.4201);
        assertThat(config.event().destinationDefault()).isEqualTo("Cordoba");
    }

    @Test
    void rejectsUnknownGeocoderMode() {
        assertThatThrownBy(() -> new PropertiesConfigLoader()
                .load(new StringReader("geocoder.mode=GOOGLE")))
                .isInstanceOf(InvalidConfigurationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_CONFIG);
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> new PropertiesConfigLoader()
                .load(new StringReader("personas.min=not-a-number")))
                .isInstanceOf(InvalidConfigurationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_CONFIG);
    }

    @Test
    void requiresDestinationWhenNoCoordinatesOrDefaultAreConfigured() {
        var config = new PropertiesConfigLoader().load(new StringReader(""));

        assertThatThrownBy(() -> ConfigValidator.requireDestination(config))
                .isInstanceOf(InvalidConfigurationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DESTINATION_NOT_RESOLVED);
    }

    @Test
    void acceptsConfiguredDestination() {
        var config = new PropertiesConfigLoader()
                .load(new StringReader("destination.lat=-31.4201\ndestination.lon=-64.1888"));

        ConfigValidator.requireDestination(config);
    }

    @Test
    void rejectsNonFiniteDestinationCoordinates() {
        for (String value : new String[] {"NaN", "Infinity", "-Infinity"}) {
            assertThatThrownBy(() -> new PropertiesConfigLoader()
                    .load(new StringReader("destination.lat=" + value + "\ndestination.lon=-64.1888")))
                    .isInstanceOf(InvalidConfigurationException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.INVALID_CONFIG);
        }
    }
}
