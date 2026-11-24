package com.example.scheduler.exception;

public class InvalidJobConfigException extends RuntimeException {
    public InvalidJobConfigException(String message) {
        super(message);
    }
}
