package com.nukkad.common.paging;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from a client-supplied page/size the way every list endpoint should: size is
 * clamped so a caller cannot force an unbounded result set into one response (e.g. {@code ?size=999999999}),
 * and page is floored at 0 so a negative value can't reach {@link PageRequest#of}. This is the same clamp
 * {@code com.nukkad.admin.util.AdminPaging} already applied to admin list endpoints; member-facing and other
 * internal list endpoints now share it instead of building an unclamped {@code PageRequest} directly.
 */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static int clampSize(int requestedSize) {
        return Math.min(Math.max(requestedSize, 1), MAX_SIZE);
    }

    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clampSize(size));
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clampSize(size), sort);
    }
}
