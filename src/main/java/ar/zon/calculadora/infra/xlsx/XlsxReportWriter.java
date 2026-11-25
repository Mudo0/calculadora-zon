package ar.zon.calculadora.infra.xlsx;

import ar.zon.calculadora.core.errors.BatchException;
import ar.zon.calculadora.core.errors.ErrorCode;
import ar.zon.calculadora.core.model.SurveyRow;
import ar.zon.calculadora.core.pipeline.ReportSink;
import ar.zon.calculadora.core.summary.BatchSummary;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ar.zon.calculadora.infra.csv.CsvReportWriter.HEADERS;

/** Escribe los datos de entrada, los resultados y los totales en un libro XLSX. */
public final class XlsxReportWriter {
    private static final int FLUSH_INTERVAL = 512;
    private static final String GREEN = "164B43";
    private static final List<String> CALCULATION_LABELS = List.of(
            "Fila de origen", "Estado", "Motivo del error", "Detalle del error", "Evento",
            "Origen declarado", "Origen identificado", "Latitud de origen", "Longitud de origen",
            "Destino declarado", "Destino identificado", "Latitud de destino", "Longitud de destino",
            "Medio de transporte", "Combustible", "Combustible inferido", "Personas",
            "Distancia (km)", "Línea recta (km)", "Método de distancia", "Usó respaldo",
            "Motivo del respaldo", "Consumo", "Unidad de consumo", "Emisiones (kg CO₂e)",
            "Base del factor", "Clave del factor", "Fuente del factor", "Factor de prueba", "Observaciones");
    private static final Set<String> NUMERIC_COLUMNS = Set.of(
            "row_number", "origen_lat", "origen_lon", "destino_lat", "destino_lon", "personas",
            "distance_km", "distance_straight_km", "consumption_value", "emissions_kgco2e");

    public static Report open(Path path, List<String> headers, boolean force) throws BatchException {
        if (Files.isDirectory(path)) {
            throw new BatchException(ErrorCode.OUTPUT_WRITE_ERROR, "La salida es un directorio: " + path);
        }
        if (!force && Files.exists(path)) {
            throw new BatchException(ErrorCode.OUTPUT_EXISTS, "La salida ya existe: " + path);
        }
        Path parent = path.toAbsolutePath().getParent();
        Path temporary = null;
        Path sourceRows = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".zon-report-", ".tmp");
            sourceRows = Files.createTempFile(parent, ".zon-data-", ".tmp");
            return new Report(path, temporary, sourceRows, headers, force);
        } catch (IOException | RuntimeException e) {
            deleteTemporary(sourceRows);
            deleteTemporary(temporary);
            throw writeError(path, e);
        }
    }

    private static void headers(Worksheet sheet, List<String> values) {
        for (int column = 0; column < values.size(); column++) {
            String value = values.get(column);
            sheet.value(0, column, value);
            sheet.style(0, column).bold().fontColor("FFFFFF").fillColor(GREEN).set();
            sheet.width(column, Math.max(14, value.length() + 5));
        }
    }

    private static void table(Worksheet sheet) {
        sheet.hideGridLines();
        sheet.freezePane(1, 1);
    }

    private static String display(String key, String value) {
        return switch (key) {
            case "status", "error_code", "distance_mode", "distance_fallback_reason", "emissions_basis",
                    "transporte_normalizado", "combustible_normalizado", "factor_source",
                    "fuel_inferred", "distance_fallback_used", "factor_is_placeholder", "calculation_notes" -> switch (value) {
                case "OK" -> "Calculado";
                case "OK_FALLBACK_HAVERSINE" -> "Calculado con respaldo";
                case "SKIPPED_NON_MOTORIZED" -> "Omitido (sin motor)";
                case "ERROR" -> "Con error";
                case "LOCATION_NOT_FOUND" -> "Ubicación sin coincidencia";
                case "INVALID_PEOPLE_COUNT" -> "Cantidad de personas inválida";
                case "MISSING_ORIGIN" -> "Falta el origen";
                case "MISSING_TRANSPORT" -> "Falta el transporte";
                case "UNKNOWN_TRANSPORT" -> "Transporte no reconocido";
                case "FACTOR_NOT_FOUND" -> "Factor no disponible";
                case "GEOCODER_ERROR" -> "Error al buscar el origen";
                case "ROUTE_NOT_FOUND", "ROUTE_UNAVAILABLE" -> "Ruta no disponible";
                case "ROUTER_ERROR" -> "Error al calcular la ruta";
                case "NOT_APPLICABLE" -> "No aplica";
                case "HAVERSINE" -> "Línea recta (Haversine)";
                case "ROUTE" -> "Por ruta";
                case "VEHICLE_KM" -> "Vehículo-kilómetro";
                case "PASSENGER_KM" -> "Pasajero-kilómetro";
                case "PLACEHOLDER_NOT_REAL" -> "Factor de prueba";
                case "consumption_not_applicable" -> "Consumo no aplicable";
                case "true" -> "Sí";
                case "false" -> "No";
                case "AUTO" -> "Auto";
                case "MOTO" -> "Moto";
                case "COLECTIVO" -> "Colectivo";
                case "BICI" -> "Bicicleta";
                case "PIE" -> "A pie";
                case "NAFTA" -> "Nafta";
                case "GASOIL" -> "Gasoil";
                default -> value;
            };
            default -> value;
        };
    }

    private static String consumptionUnit(String unit) {
        return switch (unit.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "l" -> "L";
            case "m3", "m³" -> "m³";
            default -> unit.trim();
        };
    }

    private static BatchException writeError(Path path, Exception e) {
        return new BatchException(ErrorCode.OUTPUT_WRITE_ERROR, "No se pudo escribir " + path + ": " + e.getMessage());
    }

    private static void deleteTemporary(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // El sistema puede bloquear el acceso al temporal.
            }
        }
    }

    /** Sesión de escritura incremental que publica el libro solo al confirmarse. */
    public static final class Report implements ReportSink, AutoCloseable {
        private final Path path;
        private final Path temporary;
        private final Path sourceRows;
        private final List<String> sourceHeaders;
        private final boolean force;
        private final OutputStream output;
        private final CsvWriter source;
        private final Workbook workbook;
        private final Worksheet data;
        private final Worksheet calculations;
        private final Worksheet summary;
        private final Map<String, BigDecimal> emissionsByTransport = new LinkedHashMap<>();
        private final Map<String, BigDecimal> consumptionByUnit = new LinkedHashMap<>();
        private int nextRow = 1;

        private Report(Path path, Path temporary, Path sourceRows, List<String> headers, boolean force)
                throws IOException {
            this.path = path;
            this.temporary = temporary;
            this.sourceRows = sourceRows;
            this.sourceHeaders = headers;
            this.force = force;
            OutputStream file = Files.newOutputStream(temporary);
            try {
                workbook = new Workbook(file, "ZON", "1.0");
                summary = workbook.newWorksheet("Resumen");
                data = workbook.newWorksheet("Datos");
                calculations = workbook.newWorksheet("Cálculos");
                summary.keepInActiveTab();
                summary.setTabColor(GREEN);
                headers(calculations, CALCULATION_LABELS);
                table(calculations);
                calculations.width(1, 26);
                calculations.width(2, 35);
                source = CsvWriter.builder().build(Files.newBufferedWriter(sourceRows, StandardCharsets.UTF_8));
                output = file;
            } catch (IOException | RuntimeException e) {
                try {
                    file.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        }

        @Override
        public void accept(SurveyRow input, Map<String, String> cells) throws BatchException {
            List<String> record = new ArrayList<>(input.sourceCells().size() + 1);
            record.add(Integer.toString(input.number()));
            record.addAll(input.sourceCells());
            try {
                source.writeRecord(record);
                accept(cells);
            } catch (RuntimeException e) {
                throw writeError(path, e);
            }
        }

        @Override
        public void accept(Map<String, String> cells) throws BatchException {
            try {
                String transport = cells.get("transporte_normalizado");
                String emissions = cells.get("emissions_kgco2e");
                String unit = consumptionUnit(cells.getOrDefault("consumption_unit", ""));
                String consumption = cells.getOrDefault("consumption_value", "");
                if (!unit.isEmpty() && !consumption.isBlank()) {
                    consumptionByUnit.merge(unit, new BigDecimal(consumption.replace(',', '.')), BigDecimal::add);
                }
                if (transport != null && emissions != null && !emissions.isBlank()) {
                    emissionsByTransport.merge(display("transporte_normalizado", transport),
                            new BigDecimal(emissions.replace(',', '.')), BigDecimal::add);
                }
                for (int column = 0; column < HEADERS.size(); column++) {
                    String header = HEADERS.get(column);
                    String value = cells.getOrDefault(header, "");
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (NUMERIC_COLUMNS.contains(header)) {
                        calculations.value(nextRow, column, new BigDecimal(value.replace(',', '.')));
                        if (!header.equals("row_number") && !header.equals("personas")) {
                            String numberFormat = switch (header) {
                                case "distance_km", "distance_straight_km" -> "#,##0.00#### \"km\"";
                                case "emissions_kgco2e" -> "#,##0.00#### \"kg CO₂e\"";
                                case "consumption_value" -> switch (unit) {
                                    case "L" -> "#,##0.00#### \"L\"";
                                    case "m³" -> "#,##0.00#### \"m³\"";
                                    default -> "#,##0.00####";
                                };
                                default -> "#,##0.00####";
                            };
                            calculations.style(nextRow, column).format(numberFormat).set();
                        }
                    } else {
                        calculations.inlineString(nextRow, column,
                                header.equals("consumption_unit") ? unit : display(header, value));
                        if (header.equals("status")) {
                            String fill = switch (value) {
                                case "ERROR" -> "FCE8E5";
                                case "OK" -> "DDF2E8";
                                default -> "FFF2CF";
                            };
                            calculations.style(nextRow, column).bold().fillColor(fill).set();
                        }
                    }
                }
                if (nextRow++ % FLUSH_INTERVAL == 0) {
                    calculations.flush();
                }
            } catch (IOException | RuntimeException e) {
                throw writeError(path, e);
            }
        }

        public void commit(BatchSummary totals) throws BatchException {
            try {
                calculations.setAutoFilter(0, 0, nextRow - 1, HEADERS.size() - 1);
                calculations.finish();
                source.close();
                writeData();
                writeSummary(totals);
                workbook.finish();
                output.close();
                if (force) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, path);
                }
            } catch (FileAlreadyExistsException e) {
                throw new BatchException(ErrorCode.OUTPUT_EXISTS, "La salida ya existe: " + path);
            } catch (IOException | RuntimeException e) {
                throw writeError(path, e);
            }
        }

        private void writeData() throws IOException {
            // Se vuelcan las hojas grandes por separado porque comparten el mismo ZIP.
            List<String> columns = new ArrayList<>(sourceHeaders.size() + 1);
            columns.add("Fila de origen");
            for (String header : sourceHeaders) {
                columns.add(switch (header) {
                    case "pais" -> "País";
                    case "capital" -> "¿Córdoba capital?";
                    case "barrio" -> "Barrio";
                    case "localidad" -> "Localidad";
                    case "origen" -> "Origen";
                    case "medio", "transporte" -> "Medio de transporte";
                    case "combustible" -> "Combustible";
                    case "personas" -> "Personas";
                    case "marca_vehiculo" -> "Marca del vehículo";
                    case "modelo_vehiculo" -> "Modelo del vehículo";
                    default -> header;
                });
            }
            headers(data, columns);
            table(data);
            try (var reader = CsvReader.builder()
                    .allowMissingFields(true).allowExtraFields(true)
                    .ofCsvRecord(Files.newInputStream(sourceRows), StandardCharsets.UTF_8)) {
                int row = 1;
                for (var record : reader) {
                    List<String> fields = record.getFields();
                    data.value(row, 0, Integer.parseInt(fields.getFirst()));
                    for (int column = 1; column < fields.size(); column++) {
                        if (!fields.get(column).isEmpty()) {
                            String value = fields.get(column);
                            if (column <= sourceHeaders.size() && sourceHeaders.get(column - 1).equals("capital")) {
                                value = display("fuel_inferred", value);
                            }
                            data.inlineString(row, column, value);
                        }
                    }
                    if (row++ % FLUSH_INTERVAL == 0) {
                        data.flush();
                    }
                }
                data.setAutoFilter(0, 0, row - 1, columns.size() - 1);
            }
            data.finish();
        }

        private void writeSummary(BatchSummary totals) throws IOException {
            summary.hideGridLines();
            summary.width(0, 45);
            summary.width(1, 20);
            summary.width(2, 60);
            summary.value(0, 0, "ZON  /  ITEC   ·   Movilidad y emisiones");
            summary.range(0, 0, 0, 2).merge();
            summary.range(0, 0, 0, 2).style().bold().fontColor("FFFFFF").fillColor(GREEN).set();
            metric(1, "Respuestas procesadas", totals.okRows() + totals.errorRows() + totals.skippedRows());
            metric(2, "Respuestas correctas", totals.okRows());
            metric(3, "Respuestas con distancia de respaldo", totals.fallbackRows());
            metric(4, "Respuestas omitidas", totals.skippedRows());
            metric(5, "Respuestas con errores", totals.errorRows());
            metric(6, "Distancia total (km)", totals.totalKm());
            metric(7, "Emisiones estimadas (kg CO₂e)", totals.totalKgCo2e());
            summary.value(9, 0, "Cómo leer este informe");
            summary.style(9, 0).bold().fontColor(GREEN).set();
            summary.value(10, 0, "Usá «Fila de origen» para cruzar Datos y Cálculos.");
            summary.value(11, 0, "El modelo, si se informó, queda registrado en Datos.");
            summary.value(12, 0, "La estimación usa factores por transporte y combustible.");
            int row = 14;
            summary.value(row, 0, "Consumo de combustible por unidad");
            summary.style(row++, 0).bold().fontColor(GREEN).set();
            for (var entry : consumptionByUnit.entrySet()) {
                metric(row++, "Consumo (" + entry.getKey() + ")", entry.getValue());
            }
            if (consumptionByUnit.isEmpty()) {
                summary.value(row++, 0, "Sin consumos con unidad en este lote.");
            }
            summary.value(row++, 0, "El consumo suma los valores redondeados de Cálculos.");
            for (String warning : totals.warnings()) {
                summary.inlineString(row, 0, "USING_PLACEHOLDER_FACTORS".equals(warning)
                        ? "Factores de prueba. No usar como inventario oficial." : warning);
                summary.range(row, 0, row, 2).merge();
                summary.range(row, 0, row, 2).style().bold().fillColor("FFF2CF").set();
                row++;
            }
            var statuses = new LinkedHashMap<String, BigDecimal>();
            statuses.put("Correctas", BigDecimal.valueOf(totals.okRows() - totals.fallbackRows()));
            statuses.put("Con distancia de respaldo", BigDecimal.valueOf(totals.fallbackRows()));
            statuses.put("Omitidas", BigDecimal.valueOf(totals.skippedRows()));
            statuses.put("Con error", BigDecimal.valueOf(totals.errorRows()));
            byte[] statusChart = ReportChart.bars("Respuestas por estado", statuses, 0);
            byte[] emissionsChart = ReportChart.bars("Emisiones por transporte (kg CO₂e)", emissionsByTransport, 4);
            int chartRow = row + 1;
            int statusHeight = ReportChart.height(statuses.size());
            int emissionsHeight = ReportChart.height(emissionsByTransport.size());
            int emissionsRow = chartRow + chartRows(statusHeight);
            summary.addImage(chartRow, 0, statusChart, ReportChart.WIDTH, statusHeight);
            summary.addImage(emissionsRow, 0, emissionsChart, ReportChart.WIDTH, emissionsHeight);
            summary.value(emissionsRow + chartRows(emissionsHeight), 0,
                    "El gráfico de emisiones suma los valores redondeados de Cálculos.");
        }

        private static int chartRows(int height) {
            // Cada fila de altura predeterminada ocupa 20 píxeles. Se deja una fila libre.
            return Math.ceilDiv(height, 20) + 1;
        }

        private void metric(int row, String name, Number value) {
            summary.value(row, 0, name);
            summary.value(row, 1, value);
            summary.range(row, 0, row, 1).style().fillColor(row % 2 == 0 ? "F2F7F5" : "FFFFFF").set();
            if (row == 1 || row >= 6) {
                summary.style(row, 1).bold().fontColor(GREEN)
                        .format(row == 1 ? "0" : "#,##0.00").fillColor("DDF2E8").set();
            }
        }

        @Override
        public void close() {
            try (output; source) {
                // Se cierran ambos temporales también si el lote falla.
            } catch (IOException | RuntimeException ignored) {
                // El intento de limpieza continúa aunque un cierre falle.
            } finally {
                deleteTemporary(sourceRows);
                deleteTemporary(temporary);
            }
        }
    }
}
