package io.datapulse.bidding.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidActionAttemptRepository
    extends JpaRepository<BidActionAttemptEntity, Long> {

  List<BidActionAttemptEntity> findByBidActionIdOrderByAttemptNumberDesc(
      long bidActionId);
}
