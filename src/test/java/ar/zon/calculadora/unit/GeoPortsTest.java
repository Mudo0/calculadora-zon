package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.geocode.CandidateSelector;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.geocode.CachingGeocoder;
import ar.zon.calculadora.core.route.CachingRouter;
import ar.zon.calculadora.core.route.Router;
import ar.zon.calculadora.core.route.RouteResult;
import ar.zon.calculadora.infra.maps.CoordinateMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoPortsTest {

    @Test
    void selectsHighestScoreAndBreaksTiesDeterministically() {
        var candidates = List.of(
                new GeoCandidate("Villa El Libertador", new GeoPoint(-31.4, -64.1), 0.8, "photon"),
                new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 0.8, "photon"),
                new GeoCandidate("Cordoba Norte", new GeoPoint(-31.3, -64.1), 0.9, "photon"));

        var selected = CandidateSelector.select(candidates).orElseThrow();

        assertThat(selected.label()).isEqualTo("Cordoba Norte");
        assertThat(CandidateSelector.select(List.of(candidates.get(1), candidates.get(0))).orElseThrow().label())
                .isEqualTo("Cordoba");
    }

    @Test
    void geocoderCacheHitAvoidsSecondCall() {
        var fake = new FakeGeocoder();
        fake.results.add(new GeoCandidate("Cordoba", new GeoPoint(-31.42, -64.18), 1.0, "photon"));
        var cache = new CachingGeocoder(fake, 10);

        cache.geocode(" C\u00f3rdoba ");
        cache.geocode("cordoba");

        assertThat(fake.calls).isEqualTo(1);
    }

    @Test
    void routerCacheHitAvoidsSecondCall() {
        var fake = new FakeRouter();
        fake.result = Optional.of(new RouteResult(12.3));
        var cache = new CachingRouter(fake, 10);
        var origin = new GeoPoint(-31.42, -64.18);
        var destination = new GeoPoint(-31.65, -64.43);

        cache.route(origin, destination);
        cache.route(origin, destination);

        assertThat(fake.calls).isEqualTo(1);
    }

    @Test
    void mapsPhotonAndOsrmCoordinatesFromLonLat() {
        List<Number> raw = List.of(-64.1888, -31.4201);

        var photon = CoordinateMapper.fromPhoton(raw);
        var osrm = CoordinateMapper.fromOsrm(raw);

        assertThat(photon.latitude()).isCloseTo(-31.4201, within(0.000001));
        assertThat(photon.longitude()).isCloseTo(-64.1888, within(0.000001));
        assertThat(osrm.latitude()).isEqualTo(photon.latitude());
        assertThat(osrm.longitude()).isEqualTo(photon.longitude());
    }

    private static class FakeGeocoder implements Geocoder {
        private final List<GeoCandidate> results = new ArrayList<>();
        private int calls;

        @Override
        public List<GeoCandidate> geocode(String text) {
            calls++;
            return results;
        }
    }

    private static class FakeRouter implements Router {
        private Optional<RouteResult> result = Optional.empty();
        private int calls;

        @Override
        public Optional<RouteResult> route(GeoPoint origin, GeoPoint destination) {
            calls++;
            return result;
        }
    }
}
