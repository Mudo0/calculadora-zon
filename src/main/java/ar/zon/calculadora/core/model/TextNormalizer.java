package ar.zon.calculadora.core.model;

import java.util.Locale;

/** Normaliza texto para comparar alias y valores de encuesta. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * Recorta, pasa a minúsculas, quita acentos y colapsa espacios.
     *
     * @param value texto a normalizar, puede ser nulo
     * @return texto normalizado, vacío si la entrada es nula
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKD);
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
