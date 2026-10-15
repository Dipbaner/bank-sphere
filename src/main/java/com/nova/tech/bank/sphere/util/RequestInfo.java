package com.nova.tech.bank.sphere.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestInfo {

    private RequestInfo() {}

    private static HttpServletRequest current() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }

    public static String ip() {
        HttpServletRequest r = current();
        return r == null ? null : cut(r.getRemoteAddr(), 45);
    }

    public static String userAgent() {
        HttpServletRequest r = current();
        return r == null ? null : cut(r.getHeader("User-Agent"), 255);
    }

    private static String cut(String v, int max) {
        if (v == null) return null;
        String clean = v.replaceAll("[\\r\\n\\t]", " ");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
