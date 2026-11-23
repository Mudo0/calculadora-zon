package ar.zon.calculadora.core.model;

/** Combustibles canónicos. */
public enum FuelType {

    NAFTA,
    GASOIL,
    GNC;

    /**
     * Convierte un alias de combustible al valor canónico.
     *
     * @param raw valor leído de la encuesta
     * @return combustible canónico, o nulo si el alias no es conocido
     */
    public static FuelType fromOrNull(String raw) {
        return switch (TextNormalizer.normalize(raw)) {
            case "nafta", "gasolina" -> NAFTA;
            case "gasoil", "diesel" -> GASOIL;
            case "gnc", "gas" -> GNC;
            default -> null;
        };
    }
}
