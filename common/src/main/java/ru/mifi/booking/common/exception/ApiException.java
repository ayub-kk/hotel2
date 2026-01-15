package ru.mifi.booking.common.exception;

public abstract class ApiException extends RuntimeException {

    private final int statusCode;

    protected ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
