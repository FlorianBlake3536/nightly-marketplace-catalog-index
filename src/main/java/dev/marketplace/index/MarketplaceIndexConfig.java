package dev.marketplace.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketplace")
public record MarketplaceIndexConfig(
        Infrai infrai,
        String publicBaseUrl,
        String collection,
        String embeddingModel,
        int embeddingDimension,
        String reindexCron) {
    public record Infrai(String apiKey, String baseUrl) {}
}
