package com.nukkad.security;

import java.time.Instant;

/**
 * Manually built JSON for the two security handlers that run outside Spring MVC's
 * message-converter pipeline (entry point / access-denied). Avoids depending on either
 * Jackson 2 (com.fasterxml) or Jackson 3 (tools.jackson) ObjectMapper, since Boot 4's
 * autoconfigured bean is the latter and the two are not interchangeable.
 */
final class JsonErrorBody {

    private JsonErrorBody() {}

    static String of(String message, String errorCode, String path) {
        return "{"
                + "\"success\":false,"
                + "\"message\":\"" + escape(message) + "\","
                + "\"errorCode\":\"" + escape(errorCode) + "\","
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"path\":\"" + escape(path) + "\""
                + "}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
