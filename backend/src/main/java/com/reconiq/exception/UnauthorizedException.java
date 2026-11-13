package com.reconiq.exception;

/** Raised when credentials are missing or invalid. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
