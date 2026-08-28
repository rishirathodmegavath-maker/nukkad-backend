package com.nukkad.auth.service;

/**
 * Approximate, dependency-free device-label parsing for the session list — good enough to tell
 * "Chrome on Windows" from "Safari on iPhone" without pulling in a full user-agent library.
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String label(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        String os = detectOs(ua);
        String browser = detectBrowser(ua);
        return browser + " on " + os;
    }

    private static String detectOs(String ua) {
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os")) return "Mac";
        if (ua.contains("linux")) return "Linux";
        return "an unknown device";
    }

    private static String detectBrowser(String ua) {
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome/")) return "Chrome";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("safari/") && !ua.contains("chrome/")) return "Safari";
        return "Browser";
    }
}
