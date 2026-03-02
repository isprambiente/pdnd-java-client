package it.isprambiente.pdnd;

public class PdndException extends Exception {
    private final int errorCode;

    public PdndException(String message) {
        super(message);
        this.errorCode = 0;
    }

    public PdndException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PdndException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    public int getErrorCode() {
        return errorCode;
    }
}