package com.nova.tech.bank.sphere.exception;

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException() {

        super("Invalid or expired code");
    }
}
