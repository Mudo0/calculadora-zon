package ar.zon.calculadora.core.ingest;

import ar.zon.calculadora.core.model.FuelType;
import ar.zon.calculadora.core.model.TransportMode;

import java.util.Optional;

/** Resuelve el combustible según el transporte y el valor de la fila. */
public final class FuelResolver {

    private FuelResolver() {
    }

    /**
     * Aplica las reglas de inferencia del plan.
     *
     * @param transporte modo de transporte normalizado
     * @param combustible valor de la columna de combustible
     * @param defaultFuel valor por defecto para autos sin combustible
     * @return decisión de combustible, vacía para modos no motorizados
     */
    public static Optional<FuelDecision> resolve(TransportMode transporte, String combustible, String defaultFuel) {
        return switch (transporte) {
            case PIE, BICI -> Optional.empty();
            case MOTO -> Optional.of(new FuelDecision(FuelType.NAFTA, isBlank(combustible)));
            case COLECTIVO -> Optional.of(new FuelDecision(FuelType.GASOIL, true));
            default -> Optional.of(resolveAuto(combustible, defaultFuel));
        };
    }

    private static FuelDecision resolveAuto(String combustible, String defaultFuel) {
        FuelType provided = FuelType.fromOrNull(combustible);
        if (provided != null) {
            return new FuelDecision(provided, false);
        }
        FuelType fallback = FuelType.fromOrNull(defaultFuel);
        if (fallback == null) {
            throw new IllegalArgumentException("default.fuel.auto no es un combustible conocido: " + defaultFuel);
        }
        return new FuelDecision(fallback, true);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
