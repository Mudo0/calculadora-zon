package ar.zon.calculadora.infra.http;

/** Fallo de una llamada HTTP agotados los reintentos; la fila decide cómo reportarlo. */
public final class ServiceCallException extends RuntimeException {

    /**
     * Crea el fallo con su causa.
     *
     * @param message descripción del fallo
     * @param cause causa original, o {@code null}
     */
    public ServiceCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
