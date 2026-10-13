package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class RoleTestController {

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("id", me.id(), "email", me.email());
    }

    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, String> customer() { return Map.of("area", "customer"); }

    @GetMapping("/employee")
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public Map<String, String> employee() { return Map.of("area", "employee"); }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> admin() { return Map.of("area", "admin"); }
}
