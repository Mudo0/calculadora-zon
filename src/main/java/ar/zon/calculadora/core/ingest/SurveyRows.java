package ar.zon.calculadora.core.ingest;

import ar.zon.calculadora.core.model.SurveyRow;

import java.io.IOException;
import java.util.List;

/** Lectura incremental junto con las columnas de la fuente. */
public interface SurveyRows extends Iterable<SurveyRow>, AutoCloseable {
    List<String> headers();

    @Override
    void close() throws IOException;
}
