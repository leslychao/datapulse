package ru.vkim.datapulse.dwh.repo;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.vkim.datapulse.dwh.model.ShopEntity;

public interface ShopRepository extends ReactiveCrudRepository<ShopEntity, Long> {

    Flux<ShopEntity> findByEnabledTrue();
}
