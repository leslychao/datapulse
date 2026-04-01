package io.datapulse.execution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceActionAttemptRepository extends JpaRepository<PriceActionAttemptEntity, Long> {

    List<PriceActionAttemptEntity> findByPriceActionIdOrderByAttemptNumber(long priceActionId);

    Optional<PriceActionAttemptEntity> findByPriceActionIdAndAttemptNumber(long priceActionId, int attemptNumber);
}
