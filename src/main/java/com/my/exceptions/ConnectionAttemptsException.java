package com.my.exceptions;

public class ConnectionAttemptsException extends RuntimeException {

    public ConnectionAttemptsException (String message) {
        super(message);
    }
}
