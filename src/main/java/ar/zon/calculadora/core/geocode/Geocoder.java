package ar.zon.calculadora.core.geocode;

import java.util.List;

/** Puerto de geocodificación de texto a candidatos. */
public interface Geocoder {

    /**
     * Busca candidatos para un texto.
     *
     * @param text texto normalizado o directo a resolver
     * @return candidatos encontrados, vacío si no hay resultados
     */
    List<GeoCandidate> geocode(String text);
}
