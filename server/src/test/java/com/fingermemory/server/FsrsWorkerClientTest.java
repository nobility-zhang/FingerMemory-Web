package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.scheduler.FsrsWorkerClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FsrsWorkerClientTest {
    @Test
    void failsClosedWhenTheIsolatedSchedulerIsUnavailable() {
        FsrsWorkerClient client = new FsrsWorkerClient(
                new ObjectMapper(),
                "http://127.0.0.1:1",
                100,
                250);

        assertThatThrownBy(() -> client.review(null, "study-memory", "good", 1_763_200_000_000L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.code())
                            .isEqualTo("FSRS_WORKER_UNAVAILABLE");
                });
    }
}
