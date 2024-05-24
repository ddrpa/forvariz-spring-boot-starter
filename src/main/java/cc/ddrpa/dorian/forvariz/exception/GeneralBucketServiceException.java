package cc.ddrpa.dorian.forvariz.exception;

public class GeneralBucketServiceException extends ForvarizException {

    public GeneralBucketServiceException(String message) {
        super(message);
    }

    public GeneralBucketServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}