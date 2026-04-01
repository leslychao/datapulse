package io.datapulse.test.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.security.WorkspaceAccessService;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.sellerops.api.OfferController;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.sellerops.api.OfferDetailResponse;
import io.datapulse.sellerops.api.OfferDetailResponse.ActionInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.DecisionInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.LockInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.PolicyInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.PromoInfo;
import io.datapulse.sellerops.domain.OfferService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OfferController.class)
@Import(WorkspaceAccessService.class)
@EnableMethodSecurity
class OfferControllerSliceTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OfferService offerService;

  @MockitoBean
  private WorkspaceContext workspaceContext;

  @MockitoBean
  private AppUserRepository appUserRepository;

  @MockitoBean
  private WorkspaceMemberRepository workspaceMemberRepository;

  @BeforeEach
  void setUp() {
    when(workspaceContext.getWorkspaceId()).thenReturn(1L);
    when(workspaceContext.getUserId()).thenReturn(10L);
  }

  @Nested
  @DisplayName("GET /api/workspaces/{workspaceId}/offers/{offerId}")
  class GetOfferDetail {

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_return_offer_detail_200() throws Exception {
      var now = OffsetDateTime.now();
      var response = new OfferDetailResponse(
          100L, "SKU-001", "Test Product", "WB", "WB Main",
          "ACTIVE", "Electronics",
          new BigDecimal("1000"), new BigDecimal("900"), new BigDecimal("500"),
          new BigDecimal("50.00"), 100,
          new BigDecimal("45"), "LOW",
          new BigDecimal("50000"), new BigDecimal("15000"),
          new BigDecimal("3.5"), new BigDecimal("2.1"),
          new PolicyInfo(10L, "Target Margin", "TARGET_MARGIN", "SEMI_AUTO"),
          new DecisionInfo(20L, "CHANGE", new BigDecimal("1000"),
              new BigDecimal("1100"), "Margin below target", now),
          new ActionInfo(30L, "SUCCEEDED", new BigDecimal("1100"), "LIVE", now),
          new PromoInfo(true, "Spring Sale", new BigDecimal("850"), now.plusDays(7)),
          new LockInfo(new BigDecimal("999"), "Ожидание акции", now.minusDays(1)),
          new BigDecimal("1200"), new BigDecimal("20.00"),
          now.minusHours(1), "FRESH"
      );

      when(offerService.getOfferDetail(1L, 100L)).thenReturn(response);

      mockMvc.perform(get("/api/workspaces/1/offers/100"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.offerId").value(100))
          .andExpect(jsonPath("$.skuCode").value("SKU-001"))
          .andExpect(jsonPath("$.productName").value("Test Product"))
          .andExpect(jsonPath("$.marketplaceType").value("WB"))
          .andExpect(jsonPath("$.currentPrice").value(1000))
          .andExpect(jsonPath("$.activePolicy.name").value("Target Margin"))
          .andExpect(jsonPath("$.lastDecision.decisionType").value("CHANGE"))
          .andExpect(jsonPath("$.lastAction.status").value("SUCCEEDED"))
          .andExpect(jsonPath("$.promoStatus.participating").value(true))
          .andExpect(jsonPath("$.manualLock.reason").value("Ожидание акции"))
          .andExpect(jsonPath("$.dataFreshness").value("FRESH"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_return_404_when_offer_not_found() throws Exception {
      when(offerService.getOfferDetail(1L, 999L))
          .thenThrow(NotFoundException.entity("marketplace_offer", 999L));

      mockMvc.perform(get("/api/workspaces/1/offers/999"))
          .andExpect(status().isNotFound());
    }

    @Test
    void should_return_401_when_not_authenticated() throws Exception {
      mockMvc.perform(get("/api/workspaces/1/offers/100"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_return_403_when_wrong_workspace() throws Exception {
      when(workspaceContext.getWorkspaceId()).thenReturn(999L);

      mockMvc.perform(get("/api/workspaces/1/offers/100"))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_serialize_null_sections_correctly() throws Exception {
      var response = new OfferDetailResponse(
          100L, "SKU-001", "Test Product", "WB", "WB Main",
          "ACTIVE", null,
          new BigDecimal("1000"), null, null,
          null, null,
          null, null, null, null, null, null,
          null, null, null, null, null,
          null, null,
          null, "STALE"
      );

      when(offerService.getOfferDetail(1L, 100L)).thenReturn(response);

      mockMvc.perform(get("/api/workspaces/1/offers/100"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.activePolicy").doesNotExist())
          .andExpect(jsonPath("$.lastDecision").doesNotExist())
          .andExpect(jsonPath("$.lastAction").doesNotExist())
          .andExpect(jsonPath("$.promoStatus").doesNotExist())
          .andExpect(jsonPath("$.manualLock").doesNotExist())
          .andExpect(jsonPath("$.category").doesNotExist())
          .andExpect(jsonPath("$.revenue30d").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_serialize_bigdecimal_as_number() throws Exception {
      var response = new OfferDetailResponse(
          100L, "SKU-001", "Product", "WB", "WB Main",
          "ACTIVE", null,
          new BigDecimal("1000.50"), null, null,
          new BigDecimal("42.35"), null,
          null, null, null, null, null, null,
          null, null, null, null, null,
          null, null,
          null, "FRESH"
      );

      when(offerService.getOfferDetail(1L, 100L)).thenReturn(response);

      mockMvc.perform(get("/api/workspaces/1/offers/100"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currentPrice").value(1000.50))
          .andExpect(jsonPath("$.marginPct").value(42.35));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_serialize_offsetdatetime_as_iso() throws Exception {
      var syncAt = OffsetDateTime.parse("2025-06-15T10:30:00+03:00");
      var response = new OfferDetailResponse(
          100L, "SKU-001", "Product", "WB", "WB Main",
          "ACTIVE", null,
          null, null, null, null, null,
          null, null, null, null, null, null,
          null, null, null, null, null,
          null, null,
          syncAt, "FRESH"
      );

      when(offerService.getOfferDetail(1L, 100L)).thenReturn(response);

      mockMvc.perform(get("/api/workspaces/1/offers/100"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.lastSyncAt").isString());
    }
  }
}
