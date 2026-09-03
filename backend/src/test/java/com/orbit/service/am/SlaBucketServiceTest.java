package com.orbit.service.am;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canonical SLA classifier. These pin the 3-bucket boundaries
 * so the definition can only change deliberately, with this test.
 */
class SlaBucketServiceTest {

    private final SlaBucketService svc = new SlaBucketService();

    @Test
    void classifyBoundaries() {
        // target 40 → near band opens at 0.75*40 = 30 (exclusive), breach at 40 (exclusive)
        assertThat(svc.classify(0, 40)).isEqualTo(SlaBucketService.Bucket.MET);
        assertThat(svc.classify(30, 40)).isEqualTo(SlaBucketService.Bucket.MET);   // exactly 0.75t → still met
        assertThat(svc.classify(31, 40)).isEqualTo(SlaBucketService.Bucket.NEAR);
        assertThat(svc.classify(40, 40)).isEqualTo(SlaBucketService.Bucket.NEAR);  // exactly at target → near, not met
        assertThat(svc.classify(41, 40)).isEqualTo(SlaBucketService.Bucket.BREACHED);
    }

    @Test
    void untrackedStageReturnsNull() {
        assertThat(svc.classify(999, null)).isNull();
    }

    @Test
    void computeAggregatesAndDerivesAdherence() {
        record Row(long age, Integer target) {}
        var rows = List.of(
            new Row(5, 45),   // met
            new Row(20, 45),  // met
            new Row(40, 45),  // near (last 25% of window)
            new Row(70, 45),  // breached
            new Row(3, null)  // untracked → skipped
        );
        var b = svc.compute(rows, Row::age, Row::target);
        assertThat(b.met()).isEqualTo(2);
        assertThat(b.near()).isEqualTo(1);
        assertThat(b.breached()).isEqualTo(1);
        assertThat(b.tracked()).isEqualTo(4);        // untracked row excluded
        assertThat(b.adherencePct()).isEqualTo(50);  // met / tracked = 2/4
    }

    @Test
    void adherenceIsNullWhenNothingTracked() {
        record Row(long age, Integer target) {}
        var b = svc.compute(List.of(new Row(10, null)), Row::age, Row::target);
        assertThat(b.tracked()).isZero();
        assertThat(b.adherencePct()).isNull();
    }
}
