package com.nova.tech.bank.sphere.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {

        super("Session Expired. Please login again.");
    }
}
