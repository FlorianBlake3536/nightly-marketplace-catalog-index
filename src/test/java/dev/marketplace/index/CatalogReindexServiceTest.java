package dev.marketplace.index;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogReindexServiceTest {
    @Test
    void handsOffOnlyActiveAssetsFromVerifiedSellers() {
        assertTrue(CatalogReindexService.shouldHandoff("active", true));
        assertFalse(CatalogReindexService.shouldHandoff("active", false));
        assertFalse(CatalogReindexService.shouldHandoff("suspended", true));
    }
}
