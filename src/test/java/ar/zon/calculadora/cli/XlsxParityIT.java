package ar.zon.calculadora.cli;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxParityIT {
    @TempDir
    Path directory;

    @Test
    void processesQuestionnaireDataWithTheSameResultsFromCsvAndXlsx() throws Exception {
        Path csv = Path.of("samples/input-questionnaire.csv");
        Path xlsx = directory.resolve("input.xlsx");
        try (var out = Files.newOutputStream(xlsx); var workbook = new Workbook(out, "ZON", "1.0")) {
            workbook.newWorksheet("Portada").value(0, 0, "Encuesta");
            var sheet = workbook.newWorksheet("Encuesta");
            int row = 1;
            for (String line : Files.readAllLines(csv)) {
                String[] cells = line.replace("\"", "").split(";", -1);
                for (int column = 0; column < cells.length; column++) {
                    if (row > 1 && column == 7 && !cells[column].isEmpty()) {
                        sheet.value(row, column, Integer.parseInt(cells[column]));
                    } else {
                        sheet.value(row, column, cells[column]);
                    }
                }
                row++;
            }
            sheet.value(row + 1, 0, "");
        }
        Path locations = directory.resolve("locations.csv");
        Files.writeString(locations, """
                text;lat;lon
                Alberdi, Córdoba;-31.41;-64.21
                Alta Gracia;-31.65;-64.43
                Villa Allende;-31.29;-64.29
                """);
        String settings = """
                columns.transporte=medio
                geocoder.mode=FILE
                geocoder.file=%s
                distance.mode=HAVERSINE
                factors.file=config/emission-factors.example.properties
                destination.lat=-31.36
                destination.lon=-64.24
                input.csv.delimiter=;
                input.xlsx.sheet=Encuesta
                """.formatted(locations.toString().replace('\\', '/'));
        Path config = directory.resolve("application.properties");
        Path fromCsv = directory.resolve("csv.xlsx");
        Path fromXlsx = directory.resolve("xlsx.xlsx");
        Path csvSummary = directory.resolve("csv.json");
        Path xlsxSummary = directory.resolve("xlsx.json");

        Files.writeString(config, settings);
        assertThat(run(csv, fromCsv, config, csvSummary)).isEqualTo(1);
        Files.writeString(config, settings + "input.headerRow=2\n");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", "target/zon-calculadora-0.1.0-SNAPSHOT.jar",
                "--input", xlsx.toString(), "--output", fromXlsx.toString(),
                "--config", config.toString(), "--summary", xlsxSummary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).withFailMessage("%s", output).isEqualTo(1);
        assertThat(Files.readString(xlsxSummary)).isEqualTo(Files.readString(csvSummary));
        try (var archive = new java.util.zip.ZipFile(fromXlsx.toFile())) {
            assertThat(archive.stream().filter(entry -> entry.getName().startsWith("xl/media/")).count())
                    .isEqualTo(2);
        }
        try (var csvBook = new ReadableWorkbook(fromCsv.toFile()); var xlsxBook = new ReadableWorkbook(fromXlsx.toFile())) {
            assertThat(xlsxBook.getSheets().map(sheet -> sheet.getName()).toList())
                    .containsExactly("Resumen", "Datos", "Cálculos");
            var csvRows = csvBook.findSheet("Cálculos").orElseThrow().read();
            var xlsxRows = xlsxBook.findSheet("Cálculos").orElseThrow().read();
            assertThat(xlsxRows).hasSize(6);
            assertThat(xlsxRows.getFirst().getCellText(1)).isEqualTo("Estado");
            assertThat(xlsxRows.get(1).getCellText(1)).isEqualTo("Calculado");
            assertThat(xlsxRows.get(5).getCellText(2)).isEqualTo("Ubicación sin coincidencia");
            assertThat(xlsxRows.get(1).getCellAsNumber(0)).hasValueSatisfying(
                    number -> assertThat(number.intValueExact()).isEqualTo(3));
            for (int i = 1; i < xlsxRows.size(); i++) {
                for (int column = 1; column < 30; column++) {
                    assertThat(xlsxRows.get(i).getCellText(column))
                            .isEqualTo(csvRows.get(i).getCellText(column));
                }
            }
            assertThat(xlsxRows.get(1).getCellAsNumber(17)).isPresent();
            assertThat(xlsxRows.get(1).getCellAsNumber(22)).isPresent();
            assertThat(xlsxRows.get(1).getCellText(23)).isEqualTo("L");
            var dataRows = xlsxBook.findSheet("Datos").orElseThrow().read();
            assertThat(dataRows.getFirst().getCellText(3)).isEqualTo("Barrio");
            assertThat(dataRows.get(1).getCellText(3)).isEqualTo("Alberdi");
            assertThat(dataRows.getFirst().getCellText(9)).isEqualTo("Modelo del vehículo");
            assertThat(dataRows.get(1).getCellText(9)).isEqualTo("Modelo de prueba A");
            var totals = xlsxBook.findSheet("Resumen").orElseThrow().read();
            assertThat(totals.get(7).getCellAsNumber(1)).isPresent();
            assertThat(totals.stream().filter(row -> row.getCellText(0).equals("Consumo (L)"))
                    .findFirst().orElseThrow().getCellAsNumber(1)).isPresent();
        }

        Path convertedCsv = directory.resolve("converted.csv");
        assertThat(run(xlsx, convertedCsv, config, directory.resolve("converted.json"))).isEqualTo(1);
        assertThat(Files.readString(convertedCsv)).contains("LOCATION_NOT_FOUND");
    }

    private int run(Path input, Path output, Path config, Path summary) {
        return Main.run(new String[]{"--input", input.toString(), "--output", output.toString(),
                "--config", config.toString(), "--summary", summary.toString()});
    }
}
