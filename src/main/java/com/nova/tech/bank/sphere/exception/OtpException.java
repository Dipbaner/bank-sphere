package com.nova.tech.bank.sphere.exception;

public class OtpException extends RuntimeException {

    private final String code;
    public OtpException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
