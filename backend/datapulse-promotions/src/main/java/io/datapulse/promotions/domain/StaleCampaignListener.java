package io.datapulse.promotions.domain;

import io.datapulse.common.promo.StalePromoCampaignHandler;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaleCampaignListener implements StalePromoCampaignHandler {

    private final PromoActionRepository actionRepository;

    @Override
    public void onCampaignsStale(List<Long> campaignIds) {
        for (long campaignId : campaignIds) {
            expirePendingActions(campaignId);
        }
    }

    @Transactional
    public void expirePendingActions(long campaignId) {
        List<PromoActionEntity> pendingActions =
                actionRepository.findPendingActionsByCampaignId(campaignId);

        if (pendingActions.isEmpty()) {
            log.debug("No pending promo actions for stale campaign: campaignId={}", campaignId);
            return;
        }

        int expired = 0;
        for (PromoActionEntity action : pendingActions) {
            int updated = actionRepository.casUpdateStatus(
                    action.getId(), action.getStatus(), PromoActionStatus.EXPIRED);
            if (updated > 0) {
                expired++;
            }
        }

        log.info("Expired promo actions for stale campaign: campaignId={}, expired={}, total={}",
                campaignId, expired, pendingActions.size());
    }
}
