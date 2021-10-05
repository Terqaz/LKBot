package com.my.exceptions;

public class LoginNeedsException extends RuntimeException {
    public LoginNeedsException() {
        super();
    }

    public LoginNeedsException(String message) {
        super(message);
    }
}
