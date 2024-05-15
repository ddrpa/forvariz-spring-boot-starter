package cc.ddrpa.dorian.forvariz.exception;

public class ServiceException extends IllegalStateException {
    public ServiceException(String message) {
        super(message);
    }
}