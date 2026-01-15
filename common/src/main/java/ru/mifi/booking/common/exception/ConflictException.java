package ru.mifi.booking.common.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(409, message);
    }
}
