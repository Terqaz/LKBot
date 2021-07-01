package com.my.exceptions;

public class UserTriesLimitExhaustedException extends RuntimeException {
    public UserTriesLimitExhaustedException () {
        super();
    }

    public UserTriesLimitExhaustedException (String message) {
        super(message);
    }
}
