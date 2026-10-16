package com.nova.tech.bank.sphere.util;

public final class PasswordRules {

    public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";

    public static final String MESSAGE =
            "Password needs 8-64 characters with upper, lower, digit and special character";

    private PasswordRules() {}
}
