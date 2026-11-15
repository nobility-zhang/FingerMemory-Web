package com.fingermemory.server.scheduler;

import tools.jackson.databind.node.ObjectNode;

public record WorkerPreviewResult(
        String packageVersion,
        String modelVersion,
        String configHash,
        ObjectNode config,
        ObjectNode previews) {
}
