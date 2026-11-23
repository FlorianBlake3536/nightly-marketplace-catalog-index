package dev.marketplace.index;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class CatalogReindexService {
    private final InfraiMarketplaceClient infrai;
    private final MarketplaceIndexConfig config;

    public CatalogReindexService(InfraiMarketplaceClient infrai, MarketplaceIndexConfig config) {
        this.infrai = infrai;
        this.config = config;
    }

    public ReindexReceipt reindex(SellerAsset asset) {
        if (!shouldHandoff(asset.assetStatus(), asset.sellerVerified())) {
            return new ReindexReceipt(asset.assetId(), "excluded");
        }
        String content = infrai.scrape(asset.sourceUrl());
        List<Double> embedding = infrai.embed(asset.title() + "\n" + content);
        infrai.upsert(asset.assetId(), asset.sellerId(), asset.sourceUrl(), embedding);
        return new ReindexReceipt(asset.assetId(), "indexed");
    }

    public JsonNode findForBuyer(BuyerUpdate update) {
        return infrai.query(infrai.embed(update.searchText()), update.limit());
    }

    public String provisionNightlySchedule() {
        infrai.createCollection();
        return infrai.schedule(config.publicBaseUrl() + "/internal/catalog/reindex");
    }

    public static boolean shouldHandoff(String assetStatus, boolean sellerVerified) {
        return "active".equals(assetStatus) && sellerVerified;
    }

    public record SellerAsset(
            String assetId,
            String sellerId,
            String title,
            String sourceUrl,
            String assetStatus,
            boolean sellerVerified) {}
    public record BuyerUpdate(String buyerId, String searchText, int limit) {}
    public record ReindexReceipt(String assetId, String state) {}
}
