package com.my.exceptions;

public class FileLoadingException extends RuntimeException {
    public FileLoadingException(String message) {
        super(message);
    }
}
