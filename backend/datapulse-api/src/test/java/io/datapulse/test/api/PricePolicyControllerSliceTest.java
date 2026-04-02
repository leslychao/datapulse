package io.datapulse.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.datapulse.platform.security.WorkspaceAccessService;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.pricing.api.PricePolicyController;
import io.datapulse.pricing.api.PricePolicyResponse;
import io.datapulse.pricing.api.PricePolicySummaryResponse;
import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricePolicyService;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PricePolicyController.class)
@Import(WorkspaceAccessService.class)
@EnableMethodSecurity
class PricePolicyControllerSliceTest {

  private static final String BASE_URL = "/api/workspaces/1/pricing/policies";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PricePolicyService policyService;

  @MockitoBean
  private io.datapulse.pricing.domain.ImpactPreviewService impactPreviewService;

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
  @DisplayName("GET /api/workspaces/{workspaceId}/pricing/policies")
  class ListPolicies {

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_returnPolicies_when_authenticated() throws Exception {
      var now = OffsetDateTime.now();
      var summary = new PricePolicySummaryResponse(
          1L, "Margin Policy", PolicyStatus.ACTIVE,
          PolicyType.TARGET_MARGIN, ExecutionMode.RECOMMENDATION,
          1, 1, 5, now, now);
      var page = new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1);

      when(policyService.listPoliciesPaged(eq(1L), isNull(), isNull(), any(Pageable.class)))
          .thenReturn(page);

      mockMvc.perform(get(BASE_URL))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].id").value(1))
          .andExpect(jsonPath("$.content[0].name").value("Margin Policy"))
          .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }
  }

  @Nested
  @DisplayName("POST /api/workspaces/{workspaceId}/pricing/policies")
  class CreatePolicy {

    @Test
    @WithMockUser(roles = "PRICING_MANAGER")
    void should_return201_when_validRequest() throws Exception {
      when(policyService.createPolicy(any(), eq(1L), eq(10L)))
          .thenReturn(new PricePolicyResponse(
              1L, "New Policy", PolicyStatus.DRAFT, PolicyType.TARGET_MARGIN,
              Collections.emptyMap(), BigDecimal.TEN, BigDecimal.valueOf(15), null, null,
              Collections.emptyMap(),
              ExecutionMode.RECOMMENDATION, 24, 1, 1, 10L,
              OffsetDateTime.now(), OffsetDateTime.now()));

      mockMvc.perform(post(BASE_URL)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "name": "New Policy",
                    "strategyType": "TARGET_MARGIN",
                    "strategyParams": "{}",
                    "executionMode": "RECOMMENDATION",
                    "priority": 1
                  }
                  """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("New Policy"))
          .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void should_return403_when_viewerTriesToCreate() throws Exception {
      mockMvc.perform(post(BASE_URL)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "name": "Blocked",
                    "strategyType": "TARGET_MARGIN",
                    "strategyParams": "{}",
                    "executionMode": "RECOMMENDATION",
                    "priority": 1
                  }
                  """))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("POST /api/workspaces/{workspaceId}/pricing/policies/{id}/activate")
  class ActivatePolicy {

    @Test
    @WithMockUser(roles = "ADMIN")
    void should_return204_when_activated() throws Exception {
      doNothing().when(policyService).activatePolicy(1L, 1L);

      mockMvc.perform(post(BASE_URL + "/1/activate")
              .with(csrf()))
          .andExpect(status().isNoContent());

      verify(policyService).activatePolicy(1L, 1L);
    }
  }

  @Nested
  @DisplayName("POST /api/workspaces/{workspaceId}/pricing/policies/{id}/archive")
  class ArchivePolicy {

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return204_when_archived() throws Exception {
      doNothing().when(policyService).archivePolicy(1L, 1L);

      mockMvc.perform(post(BASE_URL + "/1/archive")
              .with(csrf()))
          .andExpect(status().isNoContent());

      verify(policyService).archivePolicy(1L, 1L);
    }
  }
}
