package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoDecisionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoDecisionRepository extends JpaRepository<PromoDecisionEntity, Long> {

    Optional<PromoDecisionEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Page<PromoDecisionEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PromoDecisionEntity> findAllByWorkspaceIdAndDecisionType(
            Long workspaceId, PromoDecisionType decisionType, Pageable pageable);

    Page<PromoDecisionEntity> findAllByWorkspaceIdAndCanonicalPromoProductId(
            Long workspaceId, Long promoProductId, Pageable pageable);
}
