package ar.zon.calculadora.core.pipeline;

import ar.zon.calculadora.core.summary.BatchSummary;

import java.util.List;
import java.util.Map;

/** Filas ya formateadas para el informe y resumen del lote. */
public record BatchOutcome(List<Map<String, String>> reportRows, BatchSummary summary) {
}
