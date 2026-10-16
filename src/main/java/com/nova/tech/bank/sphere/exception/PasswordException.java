package com.nova.tech.bank.sphere.exception;

public class PasswordException extends RuntimeException {

    private final String code;
    public PasswordException(String code, String message) {
        this.code = code;
        super(message);
    }

    public String getCode() { return code; }
}
