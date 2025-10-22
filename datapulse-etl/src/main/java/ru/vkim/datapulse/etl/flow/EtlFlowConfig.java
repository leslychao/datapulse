package ru.vkim.datapulse.etl.flow;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import ru.vkim.datapulse.common.RawFileArchiveService;
import ru.vkim.datapulse.dwh.service.RawArchiveIndexerService;
import ru.vkim.datapulse.dwh.repo.FactSaleRepository;
import ru.vkim.datapulse.etl.client.WbClient;
import ru.vkim.datapulse.etl.parser.GsonRecordParser;
import ru.vkim.datapulse.etl.support.TokenHasher;

import java.io.File;
import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class EtlFlowConfig {

    private final WbClient wbClient;
    private final GsonRecordParser parser;
    private final RawFileArchiveService rawFiles;
    private final RawArchiveIndexerService rawIndexer;
    private final FactSaleRepository factRepo;
    private final MeterRegistry meterRegistry;

    @Bean
    public StandardIntegrationFlow wbSalesFlow() {
        return IntegrationFlows.from("wbSalesInput")
                .handle((p, h) -> pullAndStore("wb","sales",
                        (String) h.get("shopId"), (String) h.get("token"), (String) h.get("from"), (String) h.get("to")))
                .handle((p, h) -> parseAndUpsert((Path) p, (String) h.get("shopId")))
                .get();
    }

    @Bean
    public StandardIntegrationFlow wbStocksFlow() {
        return IntegrationFlows.from("wbStocksInput")
                .handle((p, h) -> pullAndStore("wb","stocks",
                        (String) h.get("shopId"), (String) h.get("token"), null, null))
                .get();
    }
    @Bean
    public StandardIntegrationFlow wbFinanceFlow() {
        return IntegrationFlows.from("wbFinanceInput")
                .handle((p, h) -> pullAndStore("wb","finance",
                        (String) h.get("shopId"), (String) h.get("token"), null, null))
                .get();
    }
    @Bean
    public StandardIntegrationFlow wbReviewsFlow() {
        return IntegrationFlows.from("wbReviewsInput")
                .handle((p, h) -> pullAndStore("wb","reviews",
                        (String) h.get("shopId"), (String) h.get("token"), null, null))
                .get();
    }
    @Bean
    public StandardIntegrationFlow wbAdsFlow() {
        return IntegrationFlows.from("wbAdsInput")
                .handle((p, h) -> pullAndStore("wb","ads",
                        (String) h.get("shopId"), (String) h.get("token"), null, null))
                .get();
    }

    private Path pullAndStore(String marketplace, String stream,
                              String shopId, String token, String from, String to) {
        String tokenHash = TokenHasher.sha256short(token);
        String logicalName = stream + (from != null ? "_" + from + "_" + to : "");

        String json = switch (stream) {
            case "sales" -> wbClient.fetchSalesJson(shopId, token, String.valueOf(from), String.valueOf(to)).block();
            case "stocks" -> wbClient.fetchStocksJson(shopId, token).block();
            case "finance" -> wbClient.fetchFinanceJson(shopId, token).block();
            case "reviews" -> wbClient.fetchReviewsJson(shopId, token).block();
            case "ads" -> wbClient.fetchAdsJson(shopId, token).block();
            default -> "[]";
        };
        try {
            File dir = rawFiles.resolveTokenDir(marketplace, tokenHash);
            var f = rawFiles.writeJson(dir, logicalName, json);
            rawIndexer.indexRaw(marketplace, shopId, tokenHash, logicalName, f).block();
            meterRegistry.counter("etl.pull.success", "marketplace", marketplace, "stream", stream).increment();
            return new File(f.absolutePath()).toPath();
        } catch (Exception e) {
            meterRegistry.counter("etl.pull.fail", "marketplace", marketplace, "stream", stream).increment();
            throw new IllegalStateException("Ошибка сохранения сырых данных: " + e.getMessage(), e);
        }
    }

    private Object parseAndUpsert(Path path, String shopId) {
        try {
            String json = java.nio.file.Files.readString(path);
            var events = parser.parseWbSales(json, shopId);
            return reactor.core.publisher.Flux.fromIterable(events)
                    .flatMap(ev -> factRepo.upsert(
                            ev.marketplace(), ev.shopId(), ev.sku(),
                            ev.eventTime(), ev.quantity(), ev.revenue()
                    ))
                    .then()
                    .blockOptional()
                    .orElse(0);
        } catch (Exception e) {
            meterRegistry.counter("etl.parse.fail", "marketplace", "wb", "stream", "sales").increment();
            throw new IllegalStateException("Ошибка парсинга/записи: " + e.getMessage(), e);
        }
    }
}
