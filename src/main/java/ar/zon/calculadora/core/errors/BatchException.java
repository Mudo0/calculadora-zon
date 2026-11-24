package ar.zon.calculadora.core.errors;

/** Error fatal del lote, con el código que debe devolver la CLI. */
public class BatchException extends Exception {

    private final ErrorCode code;

    /**
     * Crea el error fatal con su código y mensaje.
     *
     * @param code código de error fatal
     * @param message descripción del fallo
     */
    public BatchException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Devuelve el código asociado al fallo.
     *
     * @return código de error fatal
     */
    public ErrorCode code() {
        return code;
    }
}
