package com.nukkad.admin.util;

import com.nukkad.common.paging.PageRequests;

/** Every Admin list endpoint clamps its page size through this — a caller cannot force the
 *  server to load an unbounded result set into one response (e.g. {@code ?size=999999999}).
 *  Delegates to {@link PageRequests}, which every non-admin list endpoint now shares too. */
public final class AdminPaging {

    public static final int MAX_PAGE_SIZE = PageRequests.MAX_SIZE;

    private AdminPaging() {}

    public static int clampSize(int requestedSize) {
        return PageRequests.clampSize(requestedSize);
    }
}
