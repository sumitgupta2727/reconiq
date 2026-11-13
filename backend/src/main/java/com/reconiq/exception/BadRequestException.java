package com.reconiq.exception;

/** Raised when the request is syntactically valid but cannot be processed. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
