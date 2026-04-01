package io.datapulse.sellerops.api;

import io.datapulse.sellerops.domain.PriceJournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/offers/{offerId}/price-journal",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PriceJournalController {

    private final PriceJournalService journalService;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PriceJournalEntryResponse> getPriceJournal(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("offerId") Long offerId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "decisionType", required = false) String decisionType,
            @RequestParam(value = "actionStatus", required = false) String actionStatus) {
        return journalService.getJournal(
                workspaceId, offerId, from, to, decisionType, actionStatus,
                PageRequest.of(page, Math.min(size, 100)));
    }
}
