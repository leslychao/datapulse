package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.PriceJournalEntryResponse;
import io.datapulse.sellerops.persistence.PriceJournalJdbcRepository;
import io.datapulse.sellerops.persistence.PriceJournalRow;
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
public class PriceJournalService {

    private final PriceJournalJdbcRepository journalRepository;

    @Transactional(readOnly = true)
    public Page<PriceJournalEntryResponse> getJournal(long workspaceId, long offerId,
                                                       LocalDate from, LocalDate to,
                                                       String decisionType, String actionStatus,
                                                       Pageable pageable) {
        Page<PriceJournalRow> page = journalRepository.findByOfferId(
                workspaceId, offerId, from, to, decisionType, actionStatus, pageable);

        List<PriceJournalEntryResponse> entries = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    private PriceJournalEntryResponse toResponse(PriceJournalRow row) {
        return new PriceJournalEntryResponse(
                row.getDecisionId(),
                row.getDecisionDate(),
                row.getDecisionType(),
                row.getSkipReason(),
                row.getPolicyName(),
                row.getPolicyVersion(),
                row.getCurrentPrice(),
                row.getTargetPrice(),
                row.getPriceChangePct(),
                row.getActionStatus(),
                row.getExecutionMode(),
                row.getActualPrice(),
                row.getReconciliationSource(),
                row.getExplanationSummary()
        );
    }
}
