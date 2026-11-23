package ar.zon.calculadora.core.geocode;

import ar.zon.calculadora.core.distance.GeoPoint;

/** Candidato de geocodificación con la información necesaria para elegirlo. */
public record GeoCandidate(String label, GeoPoint point, double score, String provider) {
}
