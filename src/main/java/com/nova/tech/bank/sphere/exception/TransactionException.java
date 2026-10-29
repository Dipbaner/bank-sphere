package com.nova.tech.bank.sphere.exception;

import org.springframework.http.HttpStatus;

/** A rule of the bank was broken. The code is stable, so the React app can react to it. */
public class TransactionException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public TransactionException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public TransactionException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}