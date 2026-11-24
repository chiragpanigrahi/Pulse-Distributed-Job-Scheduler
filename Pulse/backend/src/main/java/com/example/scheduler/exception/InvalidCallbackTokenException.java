package com.example.scheduler.exception;

public class InvalidCallbackTokenException extends RuntimeException {
    public InvalidCallbackTokenException(String message) {
        super(message);
    }
}
