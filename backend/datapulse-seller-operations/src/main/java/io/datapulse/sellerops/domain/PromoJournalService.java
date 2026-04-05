package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.PromoJournalEntryResponse;
import io.datapulse.sellerops.persistence.PromoJournalJdbcRepository;
import io.datapulse.sellerops.persistence.PromoJournalRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoJournalService {

    private final PromoJournalJdbcRepository journalRepository;

    public Page<PromoJournalEntryResponse> getJournal(long workspaceId, long offerId,
                                                       LocalDate from, LocalDate to,
                                                       String decisionType, String actionStatus,
                                                       Pageable pageable) {
        Page<PromoJournalRow> page = journalRepository.findByOfferId(
                workspaceId, offerId, from, to, decisionType, actionStatus, pageable);

        List<PromoJournalEntryResponse> entries = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    private PromoJournalEntryResponse toResponse(PromoJournalRow row) {
        return new PromoJournalEntryResponse(
                row.getDecisionId(),
                row.getDecisionDate(),
                row.getPromoName(),
                row.getPromoType(),
                row.getPeriodFrom(),
                row.getPeriodTo(),
                row.getEvaluationResult(),
                row.getParticipationDecision(),
                row.getActionStatus(),
                row.getRequiredPrice(),
                row.getMarginAtPromoPrice(),
                row.getMarginDeltaPct(),
                row.getExplanationSummary()
        );
    }
}
