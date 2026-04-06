package io.datapulse.pricing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitorObservationRepository
        extends JpaRepository<CompetitorObservationEntity, Long> {

    List<CompetitorObservationEntity> findAllByCompetitorMatchIdOrderByObservedAtDesc(
            long competitorMatchId);

    void deleteAllByCompetitorMatchId(long competitorMatchId);
}
