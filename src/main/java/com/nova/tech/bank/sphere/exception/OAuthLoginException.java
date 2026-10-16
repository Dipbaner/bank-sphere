package com.nova.tech.bank.sphere.exception;

public class OAuthLoginException extends RuntimeException {

    private final String code;

    public OAuthLoginException(String code) {
        this.code = code;
        super(code);
    }

    public String getCode() { return code; }
}
