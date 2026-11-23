package ar.zon.calculadora.unit;

import ar.zon.calculadora.core.config.CalculationConfig;
import ar.zon.calculadora.core.ingest.ColumnMapping;
import ar.zon.calculadora.core.ingest.FuelResolver;
import ar.zon.calculadora.core.ingest.PeopleCountParser;
import ar.zon.calculadora.core.ingest.RowRules;
import ar.zon.calculadora.core.model.FuelType;
import ar.zon.calculadora.core.model.TextNormalizer;
import ar.zon.calculadora.core.model.RowStatus;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.model.TransportMode;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.infra.config.PropertiesConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RowNormalizationTest {

    private CalculationConfig config(String properties) {
        return new PropertiesConfigLoader().load(new StringReader(properties));
    }

    @Test
    void normalizesTextForHeaderMatching() {
        assertThat(TextNormalizer.normalize("  Medio   de  Transporte  ")).isEqualTo("medio de transporte");
        assertThat(TextNormalizer.normalize("Automóvil")).isEqualTo("automovil");
    }

    @Test
    void resolvesColumnAliasesAfterNormalization() {
        var config = config("columns.transporte=transporte, medio de transporte\ncolumns.personas=personas, cantidad");
        var mapping = ColumnMapping.from(
                List.of("Origen", " MEDIO  DE TRANSPORTE ", "Cantidad"), config.columns());

        assertThat(mapping.index("transporte")).isEqualTo(1);
        assertThat(mapping.index("personas")).isEqualTo(2);
        assertThat(mapping.missingRequired()).isEmpty();
        assertThat(ColumnMapping.from(List.of("Cantidad"), config.columns()).missingRequired())
                .containsExactly("origen", "transporte");
    }

    @Test
    void normalizesTransportAliasesAndUnknownValues() {
        assertThat(TransportMode.from(" Coche ")).isEqualTo(TransportMode.AUTO);
        assertThat(TransportMode.from(" AUTOMÓVIL ")).isEqualTo(TransportMode.AUTO);
        assertThat(TransportMode.from("motocicleta")).isEqualTo(TransportMode.MOTO);
        assertThat(TransportMode.from("bondi")).isEqualTo(TransportMode.COLECTIVO);
        assertThat(TransportMode.from("a pie")).isEqualTo(TransportMode.PIE);
        assertThat(TransportMode.from("bici")).isEqualTo(TransportMode.BICI);
        assertThat(TransportMode.from("")).isEqualTo(TransportMode.MISSING_TRANSPORT);
        assertThat(TransportMode.from("eléctrico")).isEqualTo(TransportMode.UNKNOWN_TRANSPORT);
    }

    @Test
    void infersFuelByTransport() {
        assertThat(FuelResolver.resolve(TransportMode.AUTO, "", "NAFTA").orElseThrow())
                .satisfies(decision -> {
                    assertThat(decision.fuel()).isEqualTo(FuelType.NAFTA);
                    assertThat(decision.inferred()).isTrue();
                });
        assertThat(FuelResolver.resolve(TransportMode.AUTO, "diesel", "NAFTA").orElseThrow().fuel())
                .isEqualTo(FuelType.GASOIL);
        assertThat(FuelResolver.resolve(TransportMode.AUTO, "gnc", "NAFTA").orElseThrow().fuel())
                .isEqualTo(FuelType.GNC);
        assertThat(FuelResolver.resolve(TransportMode.MOTO, "gasoil", "NAFTA").orElseThrow().fuel())
                .isEqualTo(FuelType.NAFTA);
        assertThat(FuelResolver.resolve(TransportMode.COLECTIVO, "", "NAFTA").orElseThrow())
                .satisfies(decision -> {
                    assertThat(decision.fuel()).isEqualTo(FuelType.GASOIL);
                    assertThat(decision.inferred()).isTrue();
                });
        assertThat(FuelResolver.resolve(TransportMode.BICI, "nafta", "NAFTA")).isEmpty();
    }

    @Test
    void parsesIntegerPeopleCountsFromTextAndDecimalForms() {
        assertThat(PeopleCountParser.parse("2", 1, 5)).hasValue(2);
        assertThat(PeopleCountParser.parse("2.0", 1, 5)).hasValue(2);
        assertThat(PeopleCountParser.parse("2,0", 1, 5)).hasValue(2);
        assertThat(PeopleCountParser.parse("", 1, 5)).isEmpty();
        assertThat(PeopleCountParser.parse("0", 1, 5)).isEmpty();
        assertThat(PeopleCountParser.parse("6", 1, 5)).isEmpty();
        assertThat(PeopleCountParser.parse("2,5", 1, 5)).isEmpty();
    }

    @Test
    void appliesConfiguredRangeAndPerTransportOverride() {
        var config = config("personas.min=2\npersonas.max=8\npersonas.max.colectivo=3");
        var row = new SurveyRow(3, Map.of("transporte", "colectivo", "origen", "Cordoba", "personas", "4"));

        assertThat(RowRules.analyze(row, config).error()).isEqualTo(ErrorCode.INVALID_PEOPLE_COUNT);
    }

    @Test
    void skippedRowsAreNotValidatedFurther() {
        var row = new SurveyRow(3, Map.of("transporte", "bici", "origen", "", "personas", ""));

        var decision = RowRules.analyze(row, config(""));

        assertThat(decision.status()).isEqualTo(RowStatus.SKIPPED_NON_MOTORIZED);
        assertThat(decision.error()).isNull();
        assertThat(decision.transporte()).isEqualTo(TransportMode.BICI);
    }

    @Test
    void reportsErrorsInOrder() {
        var valid = config("");
        assertThat(RowRules.analyze(new SurveyRow(1, Map.of()), valid).error())
                .isEqualTo(ErrorCode.MISSING_TRANSPORT);
        assertThat(RowRules.analyze(new SurveyRow(1, Map.of("transporte", "electrico")), valid).error())
                .isEqualTo(ErrorCode.UNKNOWN_TRANSPORT);
        assertThat(RowRules.analyze(new SurveyRow(1, Map.of("transporte", "auto", "personas", "0")), valid).error())
                .isEqualTo(ErrorCode.INVALID_PEOPLE_COUNT);
        assertThat(RowRules.analyze(new SurveyRow(1, Map.of("transporte", "auto", "personas", "2")), valid).error())
                .isEqualTo(ErrorCode.MISSING_ORIGIN);
    }

    @Test
    void validRowKeepsNormalizedTransportFuelAndPeople() {
        var row = new SurveyRow(4, Map.of("transporte", "AUTOMÓVIL", "origen", "Alta Gracia", "personas", "2.0"));

        var decision = RowRules.analyze(row, config(""));

        assertThat(decision.status()).isEqualTo(RowStatus.OK);
        assertThat(decision.transporte()).isEqualTo(TransportMode.AUTO);
        assertThat(decision.fuel()).isEqualTo(FuelType.NAFTA);
        assertThat(decision.personas()).isEqualTo(2);
    }
}
