package ar.zon.calculadora.unit;

import ar.zon.calculadora.infra.maps.CoordinatesGeocoder;
import ar.zon.calculadora.infra.maps.FileGeocoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GeocoderAdaptersTest {

    @TempDir
    Path directory;

    @Test
    void coordinatesGeocoderParsesLatitudeAndLongitude() {
        var candidates = new CoordinatesGeocoder().geocode("-31.4201, -64.1888");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).point().latitude()).isEqualTo(-31.4201);
        assertThat(candidates.get(0).point().longitude()).isEqualTo(-64.1888);
        assertThat(candidates.get(0).provider()).isEqualTo("COORDINATES");
    }

    @Test
    void coordinatesGeocoderIgnoresFreeTextAndOutOfRangeValues() {
        var geocoder = new CoordinatesGeocoder();

        assertThat(geocoder.geocode("Córdoba")).isEmpty();
        assertThat(geocoder.geocode("100,200")).isEmpty();
        assertThat(geocoder.geocode("1,2,3")).isEmpty();
    }

    @Test
    void fileGeocoderResolvesNormalizedText() throws Exception {
        Path file = directory.resolve("places.csv");
        Files.writeString(file, "Córdoba;-31.4201;-64.1888\nAlta  Gracia;-31.65;-64.4\n",
                StandardCharsets.UTF_8);
        var geocoder = new FileGeocoder(file);

        var found = geocoder.geocode("  cordoba ");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).point().latitude()).isEqualTo(-31.4201);
        assertThat(found.get(0).label()).isEqualTo("Córdoba");
        assertThat(geocoder.geocode("alta gracia").get(0).point().longitude()).isEqualTo(-64.4);
        assertThat(geocoder.geocode("Rosario")).isEmpty();
    }
}
