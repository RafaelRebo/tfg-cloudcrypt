package com.example.exceptions;

public class InstanceNotFoundException extends RuntimeException {
    public InstanceNotFoundException(String message) {
        super(message);
    }
}
