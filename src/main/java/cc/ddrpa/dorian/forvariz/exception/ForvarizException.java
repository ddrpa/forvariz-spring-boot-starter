package cc.ddrpa.dorian.forvariz.exception;

public abstract class ForvarizException extends Exception {

    public ForvarizException(String message) {
        super(message);
    }

    public ForvarizException(String message, Throwable cause) {
        super(message, cause);
    }
}