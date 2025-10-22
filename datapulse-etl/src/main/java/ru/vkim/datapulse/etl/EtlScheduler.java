package ru.vkim.datapulse.etl;

import lombok.RequiredArgsConstructor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.vkim.datapulse.dwh.model.ShopEntity;
import ru.vkim.datapulse.dwh.repo.ShopRepository;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class EtlScheduler {

    private final DirectChannel wbSalesInput;
    private final DirectChannel wbStocksInput;
    private final DirectChannel wbFinanceInput;
    private final DirectChannel wbReviewsInput;
    private final DirectChannel wbAdsInput;
    private final ShopRepository shopRepository;

    @Scheduled(cron = "0 5 * * * *") // продажи — каждый час в 05
    public void runWbSalesHourly() {
        shopRepository.findByEnabledTrue()
                .filter(s -> "wb".equalsIgnoreCase(s.getMarketplace()))
                .doOnNext(s -> wbSalesInput.send(
                        MessageBuilder.withPayload("start")
                                .setHeader("shopId", s.getShopId())
                                .setHeader("token", s.getToken())
                                .setHeader("from", "2025-10-22T00:00:00+03:00")
                                .setHeader("to",   "2025-10-23T00:00:00+03:00")
                                .build()
                ))
                .blockLast();
    }

    @Scheduled(cron = "0 15 * * * *") // остатки
    public void runWbStocksHourly() {
        dispatchSimple(wbStocksInput);
    }

    @Scheduled(cron = "0 */30 * * * *") // финансы
    public void runWbFinanceEvery30m() {
        dispatchSimple(wbFinanceInput);
    }

    @Scheduled(cron = "0 0 */4 * * *") // отзывы раз в 4 часа
    public void runWbReviewsEvery4h() {
        dispatchSimple(wbReviewsInput);
    }

    @Scheduled(cron = "0 0 2 * * *") // РК ежедневно
    public void runWbAdsDaily() {
        dispatchSimple(wbAdsInput);
    }

    private void dispatchSimple(DirectChannel channel) {
        shopRepository.findByEnabledTrue()
                .filter(s -> "wb".equalsIgnoreCase(s.getMarketplace()))
                .doOnNext(s -> channel.send(
                        MessageBuilder.withPayload("start")
                                .setHeader("shopId", s.getShopId())
                                .setHeader("token", s.getToken())
                                .build()
                ))
                .blockLast();
    }
}
