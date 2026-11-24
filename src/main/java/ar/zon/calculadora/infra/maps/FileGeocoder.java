package ar.zon.calculadora.infra.maps;

import ar.zon.calculadora.core.distance.GeoPoint;
import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.geocode.GeoCandidate;
import ar.zon.calculadora.core.geocode.Geocoder;
import ar.zon.calculadora.core.model.TextNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resuelve textos conocidos desde un archivo local ``texto;lat;lon``. */
public final class FileGeocoder implements Geocoder {

    private final Map<String, GeoCandidate> candidates;

    /**
     * Carga el archivo de lugares conocidos.
     *
     * @param file archivo con una fila ``texto;lat;lon`` por lugar
     * @throws BatchException si el archivo no se puede leer
     */
    public FileGeocoder(Path file) throws BatchException {
        Map<String, GeoCandidate> loaded = new HashMap<>();
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                String[] parts = line.replace("\uFEFF", "").split(";", -1);
                if (parts.length != 3) {
                    return;
                }
                try {
                    double latitude = Double.parseDouble(parts[1].trim());
                    double longitude = Double.parseDouble(parts[2].trim());
                    if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                        return;
                    }
                    String label = parts[0].trim();
                    loaded.put(TextNormalizer.normalize(label),
                            new GeoCandidate(label, new GeoPoint(latitude, longitude), 1.0, "FILE"));
                } catch (NumberFormatException ignored) {
                    // Una fila inválida no invalida el resto del archivo.
                }
            });
        } catch (IOException | RuntimeException e) {
            throw new BatchException(ErrorCode.INPUT_UNREADABLE, "No se pudo leer " + file + ": " + e.getMessage());
        }
        candidates = Map.copyOf(loaded);
    }

    @Override
    public List<GeoCandidate> geocode(String text) {
        return Optional.ofNullable(candidates.get(TextNormalizer.normalize(text)))
                .map(List::of)
                .orElseGet(List::of);
    }
}
