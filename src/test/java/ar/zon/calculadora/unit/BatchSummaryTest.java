package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.distance.DistanceResult;
import ar.zon.calculadora.core.emissions.ConsumptionResult;
import ar.zon.calculadora.core.emissions.EmissionResult;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.model.RowResult;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.summary.SummaryAggregator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BatchSummaryTest {

    private static RowResult ok(double km, double kg, boolean placeholder) {
        return new RowResult(1, RowStatus.OK, null, null,
                new DistanceResult(km, false),
                new ConsumptionResult(kg / 2, "l", null),
                new EmissionResult(kg, "factor.auto.nafta",
                        placeholder ? "PLACEHOLDER_NOT_REAL" : "REAL", placeholder));
    }

    @Test
    void totalsUseUnroundedValues() {
        var rows = List.of(
                ok(12.3456, 10.1234, false),
                ok(7.6544, 20.8766, true));

        var summary = SummaryAggregator.summarize(rows);

        assertThat(summary.totalKm()).isCloseTo(20.0, within(0.000001));
        assertThat(summary.totalKgCo2e()).isCloseTo(31.0, within(0.000001));
        assertThat(summary.okRows()).isEqualTo(2);
        assertThat(summary.errorRows()).isZero();
        assertThat(summary.skippedRows()).isZero();
        assertThat(summary.fallbackRows()).isZero();
    }

    @Test
    void warnsWhenAnyCalculatedRowUsedPlaceholderFactor() {
        var summary = SummaryAggregator.summarize(List.of(ok(1, 1, true)));

        assertThat(summary.warnings()).contains("USING_PLACEHOLDER_FACTORS");
    }

    @Test
    void ignoresErrorAndSkippedRowsInTotals() {
        var rows = List.of(
                ok(10, 5, false),
                new RowResult(2, RowStatus.ERROR, ErrorCode.FACTOR_NOT_FOUND, null, null, null, null),
                new RowResult(3, RowStatus.SKIPPED_NON_MOTORIZED, null, null, null, null, null));

        var summary = SummaryAggregator.summarize(rows);

        assertThat(summary.errorRows()).isEqualTo(1);
        assertThat(summary.skippedRows()).isEqualTo(1);
        assertThat(summary.totalKm()).isEqualTo(10);
        assertThat(summary.totalKgCo2e()).isEqualTo(5);
        assertThat(summary.warnings()).doesNotContain("USING_PLACEHOLDER_FACTORS");
    }

    @Test
    void countsFallbackRows() {
        var row = new RowResult(1, RowStatus.OK_FALLBACK_HAVERSINE, null, null,
                new DistanceResult(10, true), null, null);

        assertThat(SummaryAggregator.summarize(List.of(row)).fallbackRows()).isEqualTo(1);
    }
}
