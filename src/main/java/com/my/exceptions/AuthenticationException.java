package com.my.exceptions;

public class AuthenticationException extends RuntimeException {

    public AuthenticationException (String message) {
        super(message);
    }

    public AuthenticationException(Throwable cause) {
        super(cause);
    }
}