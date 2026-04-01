package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoPolicyAssignmentRepository extends JpaRepository<PromoPolicyAssignmentEntity, Long> {

    List<PromoPolicyAssignmentEntity> findAllByPromoPolicyId(Long promoPolicyId);

    boolean existsByPromoPolicyIdAndMarketplaceConnectionIdAndScopeType(
            Long promoPolicyId, Long connectionId, PromoScopeType scopeType);

    List<PromoPolicyAssignmentEntity> findAllByMarketplaceConnectionId(Long connectionId);
}
