package io.sonarswift.plugin.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class IssueTapTest {

    @BeforeEach
    void reset() {
        IssueTap.clear();
    }

    @Test
    void records_and_snapshots_findings_in_order() {
        IssueTap.record(new IssueTap.Finding("S1001", "MAJOR", "BUG", "msg-1", "/a.swift", 1, 1));
        IssueTap.record(new IssueTap.Finding("S1904", "MAJOR", "BUG", "msg-2", "/b.swift", 2, 2));

        List<IssueTap.Finding> snap = IssueTap.snapshot();
        assertThat(IssueTap.size()).isEqualTo(2);
        assertThat(snap).extracting(IssueTap.Finding::ruleKey)
                .containsExactly("S1001", "S1904");
    }

    @Test
    void snapshot_is_an_immutable_copy() {
        IssueTap.record(new IssueTap.Finding("S1", "MAJOR", "BUG", "m", "/a.swift", 1, 1));
        List<IssueTap.Finding> snap = IssueTap.snapshot();

        // Snapshot is List.copyOf(...) — adding to the tap doesn't affect existing snap
        IssueTap.record(new IssueTap.Finding("S2", "MAJOR", "BUG", "m", "/a.swift", 1, 1));
        assertThat(snap).hasSize(1);
        assertThat(IssueTap.size()).isEqualTo(2);
    }

    @Test
    void clear_drops_all_findings() {
        IssueTap.record(new IssueTap.Finding("S1", "MAJOR", "BUG", "m", "/a.swift", 1, 1));
        IssueTap.clear();
        assertThat(IssueTap.size()).isZero();
        assertThat(IssueTap.snapshot()).isEmpty();
    }

    @Test
    void concurrent_writes_do_not_corrupt_the_buffer() throws InterruptedException {
        int writers = 8;
        int perWriter = 500;
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(writers);

        for (int w = 0; w < writers; w++) {
            int wid = w;
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ignored) { return; }
                for (int i = 0; i < perWriter; i++) {
                    IssueTap.record(new IssueTap.Finding(
                            "S" + wid, "MAJOR", "BUG", "m", "/a.swift", i, i));
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(IssueTap.size()).isEqualTo(writers * perWriter);
    }
}
