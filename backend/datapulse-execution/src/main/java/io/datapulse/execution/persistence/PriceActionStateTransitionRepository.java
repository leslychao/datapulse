package io.datapulse.execution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceActionStateTransitionRepository
    extends JpaRepository<PriceActionStateTransitionEntity, Long> {

  List<PriceActionStateTransitionEntity> findByPriceActionIdOrderByCreatedAt(
      long priceActionId);
}
