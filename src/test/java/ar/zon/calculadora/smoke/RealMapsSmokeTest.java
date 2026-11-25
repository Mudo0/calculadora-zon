package ar.zon.calculadora.smoke;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.infra.http.HttpJsonClient;
import ar.zon.calculadora.infra.osrm.OsrmRouteCalculator;
import ar.zon.calculadora.infra.photon.PhotonGeocoder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba opcional contra Photon y OSRM reales. Se omite salvo que se active
 * explícitamente con {@code -Dzon.smoke=true}.
 */
@Tag("smoke")
@EnabledIfSystemProperty(named = "zon.smoke", matches = "true")
class RealMapsSmokeTest {

    private static final String PHOTON_URL = System.getProperty("photon.baseUrl", "http://localhost:2322");
    private static final String OSRM_URL = System.getProperty("osrm.baseUrl", "http://localhost:5000");

    @Test
    void photonGeocodesAltaGracia() {
        var geocoder = new PhotonGeocoder(client(PHOTON_URL), null, null, null, 5);

        var candidates = geocoder.geocode("Alta Gracia");

        assertThat(candidates).isNotEmpty();
        GeoPoint point = candidates.get(0).point();
        assertThat(point.latitude()).isBetween(-32.0, -31.0);
        assertThat(point.longitude()).isBetween(-65.0, -64.0);
    }

    @Test
    void osrmRoutesCordobaToVillaCarlosPaz() {
        var router = new OsrmRouteCalculator(client(OSRM_URL));

        var route = router.route(new GeoPoint(-31.420103, -64.188709), new GeoPoint(-31.42409, -64.497848));

        assertThat(route).isPresent();
        assertThat(route.get().km()).isBetween(32.73, 40.01);
    }

    private static HttpJsonClient client(String baseUrl) {
        return new HttpJsonClient(baseUrl, 10000, 1, null);
    }
}
