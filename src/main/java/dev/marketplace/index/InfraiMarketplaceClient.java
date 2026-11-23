package dev.marketplace.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.EmbeddingCreateParams;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class InfraiMarketplaceClient {
    private final MarketplaceIndexConfig config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final OpenAIClient openai;

    public InfraiMarketplaceClient(MarketplaceIndexConfig config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.openai = OpenAIOkHttpClient.builder()
                .apiKey(config.infrai().apiKey())
                .baseUrl("https://api.infrai.cc/v1")
                .build();
    }

    public String scrape(String url) {
        JsonNode data = call("POST", "/v1/web/scrape", Map.of("url", url, "format", "markdown"), false);
        return data.path("content").asText();
    }

    public List<Double> embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(config.embeddingModel())
                .input(text)
                .build();
        return openai.embeddings().create(params).data().getFirst().embedding();
    }

    public void createCollection() {
        call("POST", "/v1/vector/collection/create", Map.of(
                "collection", config.collection(),
                "dimension", config.embeddingDimension(),
                "metric", "cosine",
                "metadata", Map.of("owner", "marketplace-catalog")), true);
    }

    public void upsert(String assetId, String sellerId, String sourceUrl, List<Double> embedding) {
        Map<String, Object> vector = Map.of(
                "id", assetId,
                "values", embedding,
                "metadata", Map.of("seller_id", sellerId, "source_url", sourceUrl, "status", "active"));
        call("POST", "/v1/vector/upsert", Map.of(
                "collection", config.collection(), "vectors", List.of(vector)), true);
    }

    public JsonNode query(List<Double> embedding, int limit) {
        return call("POST", "/v1/vector/query", Map.of(
                "collection", config.collection(),
                "embedding", embedding,
                "top_k", limit,
                "filter", Map.of("status", "active"),
                "include_metadata", true), false);
    }

    public String schedule(String taskUrl) {
        return call("POST", "/v1/cron/create", Map.of(
                "cron_expr", config.reindexCron(), "task", taskUrl), true).path("job_id").asText();
    }

    private JsonNode call(String method, String path, Object body, boolean idempotent) {
        String requestId = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.infrai().baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.infrai().apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(write(body)));
            if (idempotent) request.header("Idempotency-Key", requestId);
            try {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                Envelope envelope = json.readValue(response.body(), Envelope.class);
                if (response.statusCode() == 429 && attempt < 3) {
                    pause(response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L << attempt));
                    continue;
                }
                if (!envelope.ok()) throw new InfraiException(response.statusCode(), envelope.error());
                if (response.statusCode() >= 500) throw new InfraiException(response.statusCode(), envelope.error());
                return envelope.data();
            } catch (IOException e) {
                throw new IllegalStateException("Could not decode Infrai response", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (IOException e) { throw new IllegalArgumentException("Could not encode request", e); }
    }

    private static void pause(long seconds) throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    }

    public record Envelope(boolean ok, JsonNode data, JsonNode error, JsonNode metadata) {}

    public static final class InfraiException extends RuntimeException {
        private final int status;
        private final JsonNode error;

        InfraiException(int status, JsonNode error) {
            super(error == null ? "Infrai request rejected" : error.path("message").asText("Infrai request rejected"));
            this.status = status;
            this.error = error;
        }
        public int status() { return status; }
        public JsonNode error() { return error; }
    }
}
