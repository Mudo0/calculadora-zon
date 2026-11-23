package ar.zon.calculadora.core.emissions;

import java.util.Optional;

/** Provee el factor aplicable a un transporte y combustible normalizados. */
public interface EmissionFactorProvider {

    /**
     * Busca el factor de la combinación indicada.
     *
     * @param transporte transporte canónico en minúsculas
     * @param combustible combustible canónico en minúsculas
     * @return el factor, o vacío si no existe
     */
    Optional<EmissionFactor> find(String transporte, String combustible);
}
