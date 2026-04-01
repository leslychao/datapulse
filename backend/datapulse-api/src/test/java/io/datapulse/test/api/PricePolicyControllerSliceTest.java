package io.datapulse.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.pricing.api.PricePolicyController;
import io.datapulse.pricing.api.PricePolicyResponse;
import io.datapulse.pricing.api.PricePolicySummaryResponse;
import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricePolicyService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PricePolicyController.class)
class PricePolicyControllerSliceTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private PricePolicyService policyService;

  @MockBean
  private WorkspaceContext workspaceContext;

  @BeforeEach
  void setUp() {
    when(workspaceContext.getWorkspaceId()).thenReturn(1L);
    when(workspaceContext.getUserId()).thenReturn(10L);
  }

  @Nested
  @DisplayName("GET /api/pricing/policies")
  class ListPolicies {

    @Test
    @WithMockUser(roles = "ANALYST")
    void should_returnPolicies_when_authenticated() throws Exception {
      when(policyService.listPolicies(1L, null, null)).thenReturn(List.of(
          new PricePolicySummaryResponse(1L, "Margin Policy", PolicyStatus.ACTIVE,
              PolicyType.TARGET_MARGIN, ExecutionMode.RECOMMENDATION, 1, 1,
              OffsetDateTime.now())
      ));

      mockMvc.perform(get("/api/pricing/policies"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].name").value("Margin Policy"))
          .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }
  }

  @Nested
  @DisplayName("POST /api/pricing/policies")
  class CreatePolicy {

    @Test
    @WithMockUser(roles = "PRICING_MANAGER")
    void should_return201_when_validRequest() throws Exception {
      when(policyService.createPolicy(any(), eq(1L), eq(10L)))
          .thenReturn(new PricePolicyResponse(
              1L, "New Policy", PolicyStatus.DRAFT, PolicyType.TARGET_MARGIN,
              "{}", BigDecimal.TEN, BigDecimal.valueOf(15), null, null, "{}",
              ExecutionMode.RECOMMENDATION, 24, 1, 1, 10L,
              OffsetDateTime.now(), OffsetDateTime.now()));

      mockMvc.perform(post("/api/pricing/policies")
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
      mockMvc.perform(post("/api/pricing/policies")
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
  @DisplayName("POST /api/pricing/policies/{id}/activate")
  class ActivatePolicy {

    @Test
    @WithMockUser(roles = "ADMIN")
    void should_return204_when_activated() throws Exception {
      doNothing().when(policyService).activatePolicy(1L, 1L);

      mockMvc.perform(post("/api/pricing/policies/1/activate")
              .with(csrf()))
          .andExpect(status().isNoContent());

      verify(policyService).activatePolicy(1L, 1L);
    }
  }

  @Nested
  @DisplayName("POST /api/pricing/policies/{id}/archive")
  class ArchivePolicy {

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return204_when_archived() throws Exception {
      doNothing().when(policyService).archivePolicy(1L, 1L);

      mockMvc.perform(post("/api/pricing/policies/1/archive")
              .with(csrf()))
          .andExpect(status().isNoContent());

      verify(policyService).archivePolicy(1L, 1L);
    }
  }
}
