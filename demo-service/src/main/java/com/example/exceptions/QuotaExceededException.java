package com.example.exceptions;

public class QuotaExceededException extends Exception {
    public QuotaExceededException(String message) {
        super(message);
    }
}
