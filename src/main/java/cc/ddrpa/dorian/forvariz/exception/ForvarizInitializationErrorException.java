package cc.ddrpa.dorian.forvariz.exception;

public class ForvarizInitializationErrorException extends IllegalStateException {

    public ForvarizInitializationErrorException(String message) {
        super(message);
    }

    public ForvarizInitializationErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}