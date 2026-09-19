package com.nukkad.admin.util;

/** Every Admin list endpoint clamps its page size through this — a caller cannot force the
 *  server to load an unbounded result set into one response (e.g. {@code ?size=999999999}). */
public final class AdminPaging {

    public static final int MAX_PAGE_SIZE = 100;

    private AdminPaging() {}

    public static int clampSize(int requestedSize) {
        return Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
    }
}
