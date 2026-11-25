package ar.zon.calculadora.infra.http;

/** Fallo de una llamada HTTP agotados los reintentos; la fila decide cómo reportarlo. */
public final class ServiceCallException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    /**
     * Crea el fallo con su causa.
     *
     * @param message descripción del fallo
     * @param cause causa original, o {@code null}
     */
    public ServiceCallException(String message, Throwable cause) {
        this(message, cause, 0, null);
    }

    /**
     * Crea el fallo con la respuesta HTTP que lo produjo.
     *
     * @param message descripción del fallo
     * @param cause causa original, o {@code null}
     * @param statusCode código HTTP de la respuesta
     * @param responseBody cuerpo de la respuesta, o {@code null}
     */
    public ServiceCallException(String message, Throwable cause, int statusCode, String responseBody) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Devuelve el código HTTP de la respuesta.
     *
     * @return código HTTP, o {@code 0} si no hubo respuesta
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Devuelve el cuerpo de la respuesta HTTP.
     *
     * @return cuerpo de la respuesta, o {@code null} si no hubo respuesta
     */
    public String responseBody() {
        return responseBody;
    }
}
