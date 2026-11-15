package com.fingermemory.server.scheduler;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FsrsWorkerClient {
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final URI baseUri;
    private final Duration requestTimeout;

    public FsrsWorkerClient(
            ObjectMapper objectMapper,
            @Value("${app.fsrs.worker-url:http://127.0.0.1:8090}") String workerUrl,
            @Value("${app.fsrs.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${app.fsrs.request-timeout-ms:1500}") long requestTimeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUri = URI.create(workerUrl);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    public WorkerReviewResult review(ObjectNode card, String studyItemId, String rating,
                                     long reviewedAtMs) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("studyItemId", studyItemId);
        if (card == null) {
            request.putNull("card");
        } else {
            request.set("card", card);
        }
        request.put("rating", rating);
        request.put("reviewedAt", reviewedAtMs);
        JsonNode response = post("/v1/review", request);
        return new WorkerReviewResult(
                requireText(response, "packageVersion"),
                requireText(response, "modelVersion"),
                requireText(response, "configHash"),
                requireObject(response, "config"),
                requireObject(response, "card"),
                requireObject(response, "revlog"));
    }

    public WorkerPreviewResult preview(ObjectNode card, String studyItemId, long reviewedAtMs) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("studyItemId", studyItemId);
        if (card == null) {
            request.putNull("card");
        } else {
            request.set("card", card);
        }
        request.put("reviewedAt", reviewedAtMs);
        JsonNode response = post("/v1/preview", request);
        return new WorkerPreviewResult(
                requireText(response, "packageVersion"),
                requireText(response, "modelVersion"),
                requireText(response, "configHash"),
                requireObject(response, "config"),
                requireObject(response, "previews"));
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw workerFailure();
            }
            JsonNode parsed = objectMapper.readTree(response.body());
            if (!parsed.isObject() || !"6.0.0".equals(parsed.path("packageVersion").asText())
                    || !"fsrs-7".equals(parsed.path("modelVersion").asText())) {
                throw workerFailure();
            }
            return parsed;
        } catch (ApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw workerFailure();
        } catch (Exception exception) {
            throw workerFailure();
        }
    }

    private ObjectNode requireObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw workerFailure();
        }
        ObjectNode object = ((ObjectNode) value).deepCopy();
        if ("config".equals(field)) {
            String expectedHash = requireText(parent, "configHash");
            if (!expectedHash.equals(JsonSupport.fingerprint(objectMapper, object))) {
                throw workerFailure();
            }
        }
        return object;
    }

    private String requireText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw workerFailure();
        }
        return value.textValue();
    }

    private ApiException workerFailure() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "FSRS_WORKER_UNAVAILABLE", "调度组件暂时不可用，请保留答案后重试");
    }
}
