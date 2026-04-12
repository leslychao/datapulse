package io.datapulse.bidding.domain.guard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

@Service
public class BiddingGuardChain {

  private final List<BiddingGuard> orderedGuards;

  public BiddingGuardChain(List<BiddingGuard> guards) {
    this.orderedGuards = guards.stream()
        .sorted(Comparator.comparingInt(BiddingGuard::order))
        .toList();
  }

  public GuardChainResult evaluate(BiddingGuardContext context) {
    List<GuardEvaluation> evaluations = new ArrayList<>();

    for (BiddingGuard guard : orderedGuards) {
      BiddingGuardResult result = guard.evaluate(context);
      evaluations.add(new GuardEvaluation(
          result.guardName(), result.allowed(), result.messageKey(), result.args()));

      if (!result.allowed()) {
        return new GuardChainResult(false, result, evaluations);
      }
    }

    return new GuardChainResult(true, null, evaluations);
  }

  public record GuardChainResult(
      boolean allPassed,
      BiddingGuardResult blockingGuard,
      List<GuardEvaluation> evaluations
  ) {
  }

  public record GuardEvaluation(
      String guardName,
      boolean passed,
      String messageKey,
      java.util.Map<String, Object> args
  ) {
  }
}
