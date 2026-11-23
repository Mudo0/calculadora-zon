package ar.zon.calculadora.core.model;

import ar.zon.calculadora.core.distance.GeoPoint;

/** Datos normalizados de un viaje, listos para geocodificar y calcular. */
public record NormalizedTrip(int number, TransportMode transporte, FuelType fuel, boolean fuelInferred,
                             int personas, GeoPoint origin, GeoPoint destination) {
}
