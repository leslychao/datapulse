package ru.vkim.datapulse.dwh.repo;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.vkim.datapulse.dwh.model.RawFileEntity;

import java.time.OffsetDateTime;

public interface RawFileRepository extends ReactiveCrudRepository<RawFileEntity, Long> {

    Flux<RawFileEntity> findByMarketplaceAndShopIdOrderByCreatedAtDesc(String marketplace, String shopId);

    Flux<RawFileEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime from, OffsetDateTime to);

    Flux<RawFileEntity> findByMarketplaceAndCreatedAtBetweenOrderByCreatedAtDesc(
            String marketplace, OffsetDateTime from, OffsetDateTime to
    );
}
