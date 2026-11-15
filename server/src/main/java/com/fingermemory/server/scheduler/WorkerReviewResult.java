package com.fingermemory.server.scheduler;

import tools.jackson.databind.node.ObjectNode;

public record WorkerReviewResult(
        String packageVersion,
        String modelVersion,
        String configHash,
        ObjectNode config,
        ObjectNode card,
        ObjectNode revlog) {
}
