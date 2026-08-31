package io.github.khezyapp.a2a.core.error;

/** Base unchecked exception for all A2A core errors. */
public class A2ACoreException extends RuntimeException {

    public A2ACoreException(final String message) {
        super(message);
    }

    public A2ACoreException(final String message,
                            final Throwable cause) {
        super(message, cause);
    }
}
