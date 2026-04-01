package io.datapulse.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.datapulse.integration.api.ConnectionController;
import io.datapulse.integration.api.ConnectionResponse;
import io.datapulse.integration.api.ConnectionSummaryResponse;
import io.datapulse.integration.domain.ConnectionService;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConnectionController.class)
@EnableMethodSecurity
class ConnectionControllerSliceTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ConnectionService connectionService;

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
  @DisplayName("GET /api/connections")
  class ListConnections {

    @Test
    @WithMockUser(roles = "VIEWER")
    void should_returnConnections_when_authenticated() throws Exception {
      when(connectionService.listConnections(1L)).thenReturn(List.of(
          new ConnectionSummaryResponse(1L, "WB", "My WB", "ACTIVE",
              null, OffsetDateTime.now(), null)
      ));

      mockMvc.perform(get("/api/connections"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].marketplaceType").value("WB"))
          .andExpect(jsonPath("$[0].name").value("My WB"));
    }

    @Test
    void should_return401_when_notAuthenticated() throws Exception {
      mockMvc.perform(get("/api/connections"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("POST /api/connections")
  class CreateConnection {

    @Test
    @WithMockUser(roles = "ADMIN")
    void should_return201_when_validRequest() throws Exception {
      when(connectionService.createConnection(any(), eq(1L), eq(10L)))
          .thenReturn(new ConnectionResponse(
              1L, "WB", "New Conn", "PENDING_VALIDATION", null,
              null, null, null, null, null, null, null));

      mockMvc.perform(post("/api/connections")
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "marketplaceType": "WB",
                    "name": "New Conn",
                    "credentials": {"apiToken": "token-123"}
                  }
                  """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("New Conn"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void should_return403_when_insufficientRole() throws Exception {
      mockMvc.perform(post("/api/connections")
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "marketplaceType": "WB",
                    "name": "New Conn",
                    "credentials": {"apiToken": "token"}
                  }
                  """))
          .andExpect(status().isForbidden());
    }
  }
}
