package ar.zon.calculadora.core.errors;

/** Error fatal de infraestructura: el lote debe detenerse con {@code SERVICE_UNAVAILABLE}. */
public final class ServiceUnavailableException extends RuntimeException {

    /**
     * Crea el error con el detalle observado.
     *
     * @param message descripción del fallo del servicio
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
