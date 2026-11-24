package ar.zon.calculadora.infra.http;

import ar.zon.calculadora.core.errors.ServiceUnavailableException;

/** Disyuntor simple: cuenta fallos consecutivos de un servicio. */
public final class ServiceFailureGuard {

    private final int maxConsecutiveFailures;
    private int consecutiveFailures;

    /**
     * Crea la guardia con el umbral configurado.
     *
     * @param maxConsecutiveFailures cantidad máxima de fallos consecutivos permitidos
     */
    public ServiceFailureGuard(int maxConsecutiveFailures) {
        if (maxConsecutiveFailures < 1) {
            throw new IllegalArgumentException("http.maxConsecutiveFailures debe ser mayor que cero");
        }
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    /**
     * Registra un fallo y aborta el lote cuando se alcanza el umbral.
     */
    public void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= maxConsecutiveFailures) {
            throw new ServiceUnavailableException(
                    "Servicios no disponibles tras " + consecutiveFailures + " fallos consecutivos");
        }
    }

    /** Registra una respuesta correcta y reinicia la cuenta de fallos consecutivos. */
    public void recordSuccess() {
        consecutiveFailures = 0;
    }

    /**
     * Devuelve la cuenta actual de fallos consecutivos.
     *
     * @return fallos consecutivos registrados
     */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }
}
