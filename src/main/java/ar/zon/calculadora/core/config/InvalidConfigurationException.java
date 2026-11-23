package ar.zon.calculadora.core.config;

import ar.zon.calculadora.core.errors.ErrorCode;

/** Señala una configuración inválida o un destino no resuelto. */
public class InvalidConfigurationException extends RuntimeException {

    private final ErrorCode code;

    /**
     * Crea la excepción con el código y el detalle indicados.
     *
     * @param code código de error fatal
     * @param message detalle legible del problema
     */
    public InvalidConfigurationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** Devuelve el código de error fatal. */
    public ErrorCode code() {
        return code;
    }
}
