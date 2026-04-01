package io.datapulse.promotions.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoActionAttemptRepository extends JpaRepository<PromoActionAttemptEntity, Long> {

    List<PromoActionAttemptEntity> findAllByPromoActionIdOrderByAttemptNumber(Long promoActionId);
}
