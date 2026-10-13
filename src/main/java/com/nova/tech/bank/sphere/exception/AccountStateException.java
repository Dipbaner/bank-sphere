package com.nova.tech.bank.sphere.exception;

public class AccountStateException extends RuntimeException {

    private final String code;

    public AccountStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
