package ar.zon.calculadora.unit;

import ar.zon.calculadora.cli.Main;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MainTest {

    @Test
    void startsWithoutExternalServices() {
        assertThatCode(() -> Main.main(new String[0])).doesNotThrowAnyException();
    }
}
