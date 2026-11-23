package ar.zon.calculadora.core.ingest;

import java.util.OptionalInt;

/** Convierte el valor de personas en un entero dentro del rango permitido. */
public final class PeopleCountParser {

    private PeopleCountParser() {
    }

    /**
     * Acepta enteros escritos como texto o como decimal exacto.
     *
     * @param raw valor de la columna personas
     * @param min mínimo permitido
     * @param max máximo permitido
     * @return número de personas, vacío si el valor es inválido
     */
    public static OptionalInt parse(String raw, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            double value = Double.parseDouble(raw.trim().replace(',', '.'));
            if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max) {
                return OptionalInt.empty();
            }
            return OptionalInt.of((int) value);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
