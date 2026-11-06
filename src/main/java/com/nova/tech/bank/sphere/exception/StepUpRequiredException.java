package com.nova.tech.bank.sphere.exception;

import com.nova.tech.bank.sphere.entity.StepUpAction;

public class StepUpRequiredException extends RuntimeException {

    private final StepUpAction action;
    private final String context;

    public StepUpRequiredException(StepUpAction action, String context) {
        super("Please confirm with your authenticator code");
        this.action = action;
        this.context = context;
    }

    public StepUpAction getAction() { return action; }
    public String getContext() { return context; }
}