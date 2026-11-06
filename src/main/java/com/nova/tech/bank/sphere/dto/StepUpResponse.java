package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.entity.StepUpAction;

public record StepUpResponse(String stepUpToken, StepUpAction action, long expiresInSeconds) {}