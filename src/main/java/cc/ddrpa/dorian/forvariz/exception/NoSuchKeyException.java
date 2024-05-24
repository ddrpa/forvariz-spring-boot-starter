package cc.ddrpa.dorian.forvariz.exception;

public class NoSuchKeyException extends ForvarizException {

    public NoSuchKeyException(String message) {
        super(message);
    }

    public NoSuchKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}