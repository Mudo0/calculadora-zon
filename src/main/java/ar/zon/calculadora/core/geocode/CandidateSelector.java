package ar.zon.calculadora.core.geocode;

import ar.zon.calculadora.core.model.TextNormalizer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Elige un candidato con criterios estables, sin depender del orden de respuesta. */
public final class CandidateSelector {

    private static final Comparator<GeoCandidate> ORDER = Comparator
            .comparingDouble(GeoCandidate::score).reversed()
            .thenComparing(candidate -> TextNormalizer.normalize(candidate.label()))
            .thenComparing(GeoCandidate::provider);

    private CandidateSelector() {
    }

    /**
     * Devuelve el mejor candidato según puntaje, etiqueta y proveedor.
     *
     * @param candidates candidatos disponibles
     * @return candidato seleccionado, vacío si no hay resultados
     */
    public static Optional<GeoCandidate> select(List<GeoCandidate> candidates) {
        return candidates.stream().min(ORDER);
    }
}
