package com.nukkad.common.paging;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestsTest {

    @Test
    void aRequestedSizeWithinTheLimitIsUnchanged() {
        assertThat(PageRequests.of(0, 20).getPageSize()).isEqualTo(20);
        assertThat(PageRequests.clampSize(20)).isEqualTo(20);
    }

    @Test
    void aClientCannotForceAnUnboundedPage() {
        // e.g. ?size=999999999 — a single request must not be able to pull a whole table into memory.
        assertThat(PageRequests.of(0, 1_000_000).getPageSize()).isEqualTo(PageRequests.MAX_SIZE);
        assertThat(PageRequests.clampSize(1_000_000)).isEqualTo(PageRequests.MAX_SIZE);
    }

    @Test
    void aZeroOrNegativeSizeIsRaisedToOneRatherThanFailing() {
        assertThat(PageRequests.of(0, 0).getPageSize()).isEqualTo(1);
        assertThat(PageRequests.of(0, -5).getPageSize()).isEqualTo(1);
    }

    @Test
    void aNegativePageIsFlooredAtZeroRatherThanFailing() {
        assertThat(PageRequests.of(-3, 10).getPageNumber()).isZero();
    }

    @Test
    void sortIsPreservedThroughTheClamp() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        assertThat(PageRequests.of(0, 500, sort).getSort()).isEqualTo(sort);
    }
}
