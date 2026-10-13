package com.nova.tech.bank.sphere.entity;

public enum AuditAction {
    LOGIN_PASSWORD_OK,   // step 1 passed, pre-auth token issued
    LOGIN_FAILED,        // wrong password / unknown account
    LOGIN_BLOCKED,       // correct password but locked, disabled or unverified
    ACCOUNT_LOCKED       // lock just triggered by too many failures
    // later steps add: OTP_SUCCESS, OTP_FAILED, LOGIN_SUCCESS, LOGOUT, PASSWORD_RESET,
}
