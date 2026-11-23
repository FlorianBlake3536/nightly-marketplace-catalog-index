package dev.marketplace.index;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public final class MarketplaceIndexController {
    private final CatalogReindexService service;

    public MarketplaceIndexController(CatalogReindexService service) { this.service = service; }

    @PostMapping("/internal/catalog/reindex")
    public CatalogReindexService.ReindexReceipt reindex(@RequestBody CatalogReindexService.SellerAsset asset) {
        return service.reindex(asset);
    }

    @PostMapping("/buyers/matches")
    public JsonNode matches(@RequestBody CatalogReindexService.BuyerUpdate update) {
        return service.findForBuyer(update);
    }

    @PostMapping("/admin/nightly-schedule")
    public Map<String, String> schedule() {
        return Map.of("job_id", service.provisionNightlySchedule());
    }

    @ExceptionHandler(InfraiMarketplaceClient.InfraiException.class)
    public ResponseEntity<JsonNode> rejected(InfraiMarketplaceClient.InfraiException error) {
        int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
        return ResponseEntity.status(status).body(error.error());
    }
}
