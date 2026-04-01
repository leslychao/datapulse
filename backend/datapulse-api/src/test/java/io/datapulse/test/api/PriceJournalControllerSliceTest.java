package io.datapulse.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.datapulse.platform.security.WorkspaceAccessService;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.sellerops.api.PriceJournalController;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.sellerops.api.PriceJournalEntryResponse;
import io.datapulse.sellerops.domain.PriceJournalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PriceJournalController.class)
@Import(WorkspaceAccessService.class)
class PriceJournalControllerSliceTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PriceJournalService journalService;

  @MockitoBean
  private WorkspaceContext workspaceContext;

  @MockitoBean
  private AppUserRepository appUserRepository;

  @MockitoBean
  private WorkspaceMemberRepository workspaceMemberRepository;

  @BeforeEach
  void setUp() {
    when(workspaceContext.getWorkspaceId()).thenReturn(1L);
  }

  @Nested
  @DisplayName("GET /api/workspaces/{workspaceId}/offers/{offerId}/price-journal")
  class GetPriceJournal {

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_return_journal_page_200() throws Exception {
      var entry = new PriceJournalEntryResponse(
          1L, OffsetDateTime.now(), "CHANGE", null,
          "Target Margin", 3,
          new BigDecimal("1000"), new BigDecimal("1100"),
          new BigDecimal("10.00"), "SUCCEEDED", "LIVE",
          new BigDecimal("1100"), "API_CONFIRM",
          "Margin below target"
      );
      Page<PriceJournalEntryResponse> page =
          new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1);

      when(journalService.getJournal(
          eq(1L), eq(100L), isNull(), isNull(), isNull(), isNull(), any()))
          .thenReturn(page);

      mockMvc.perform(get("/api/workspaces/1/offers/100/price-journal"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray())
          .andExpect(jsonPath("$.content[0].decisionId").value(1))
          .andExpect(jsonPath("$.content[0].decisionType").value("CHANGE"))
          .andExpect(jsonPath("$.content[0].policyName").value("Target Margin"))
          .andExpect(jsonPath("$.content[0].currentPrice").value(1000))
          .andExpect(jsonPath("$.content[0].targetPrice").value(1100))
          .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_accept_pagination_params() throws Exception {
      when(journalService.getJournal(
          eq(1L), eq(100L), isNull(), isNull(), isNull(), isNull(), any()))
          .thenReturn(Page.empty(PageRequest.of(1, 10)));

      mockMvc.perform(get("/api/workspaces/1/offers/100/price-journal")
              .param("page", "1")
              .param("size", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_accept_filter_params() throws Exception {
      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 12, 31);

      when(journalService.getJournal(
          eq(1L), eq(100L), eq(from), eq(to), eq("SKIP"), isNull(), any()))
          .thenReturn(Page.empty(PageRequest.of(0, 20)));

      mockMvc.perform(get("/api/workspaces/1/offers/100/price-journal")
              .param("from", "2025-01-01")
              .param("to", "2025-12-31")
              .param("decisionType", "SKIP"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_return_empty_page_for_unknown_offer() throws Exception {
      when(journalService.getJournal(
          eq(1L), eq(999L), isNull(), isNull(), isNull(), isNull(), any()))
          .thenReturn(Page.empty(PageRequest.of(0, 20)));

      mockMvc.perform(get("/api/workspaces/1/offers/999/price-journal"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isEmpty())
          .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void should_return_401_without_auth() throws Exception {
      mockMvc.perform(get("/api/workspaces/1/offers/100/price-journal"))
          .andExpect(status().isUnauthorized());
    }
  }
}
