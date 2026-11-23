package dev.marketplace.index;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketplaceIndexApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketplaceIndexApplication.class, args);
    }
}
