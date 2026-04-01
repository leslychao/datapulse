package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestConnectionBuilder.aConnection;
import static io.datapulse.test.builder.TestSecretReferenceBuilder.aSecretReference;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.etl.domain.JobExecutionStatus;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobExecutionRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private JobExecutionRepository jobExecutionRepository;

  @Autowired
  private MarketplaceConnectionRepository connectionRepository;

  @Autowired
  private SecretReferenceRepository secretRefRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  private Long connectionId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(user.getId()).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(user.getId()).build());
    var secret = secretRefRepository.save(
        aSecretReference().withWorkspaceId(ws.getId()).build());
    var conn = connectionRepository.save(
        aConnection().withWorkspaceId(ws.getId())
            .withSecretReferenceId(secret.getId()).build());
    connectionId = conn.getId();
  }

  @Nested
  @DisplayName("insert")
  class Insert {

    @Test
    void should_insertJob_and_returnGeneratedId() {
      long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");

      assertThat(jobId).isPositive();

      var found = jobExecutionRepository.findById(jobId);
      assertThat(found).isPresent();
      assertThat(found.get().getConnectionId()).isEqualTo(connectionId);
      assertThat(found.get().getEventType()).isEqualTo("FULL_SYNC");
      assertThat(found.get().getStatus()).isEqualTo("PENDING");
    }
  }

  @Nested
  @DisplayName("casStatus")
  class CasStatus {

    @Test
    void should_transitionStatus_when_expectedMatches() {
      long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");

      boolean result = jobExecutionRepository.casStatus(
          jobId, JobExecutionStatus.PENDING, JobExecutionStatus.IN_PROGRESS);

      assertThat(result).isTrue();

      var job = jobExecutionRepository.findById(jobId).orElseThrow();
      assertThat(job.getStatus()).isEqualTo("IN_PROGRESS");
      assertThat(job.getStartedAt()).isNotNull();
    }

    @Test
    void should_failTransition_when_expectedDoesNotMatch() {
      long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");

      boolean result = jobExecutionRepository.casStatus(
          jobId, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.COMPLETED);

      assertThat(result).isFalse();

      var job = jobExecutionRepository.findById(jobId).orElseThrow();
      assertThat(job.getStatus()).isEqualTo("PENDING");
    }
  }

  @Nested
  @DisplayName("existsActiveForConnection")
  class ExistsActive {

    @Test
    void should_returnTrue_when_pendingJobExists() {
      jobExecutionRepository.insert(connectionId, "FULL_SYNC");

      assertThat(jobExecutionRepository.existsActiveForConnection(connectionId)).isTrue();
    }

    @Test
    void should_returnFalse_when_noActiveJobs() {
      long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");
      jobExecutionRepository.casStatus(
          jobId, JobExecutionStatus.PENDING, JobExecutionStatus.IN_PROGRESS);
      jobExecutionRepository.casStatus(
          jobId, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.COMPLETED);

      assertThat(jobExecutionRepository.existsActiveForConnection(connectionId)).isFalse();
    }
  }

  @Nested
  @DisplayName("findByConnectionId")
  class FindByConnectionId {

    @Test
    void should_returnJobs_filteredByConnection() {
      jobExecutionRepository.insert(connectionId, "FULL_SYNC");
      jobExecutionRepository.insert(connectionId, "INCREMENTAL");

      var jobs = jobExecutionRepository.findByConnectionId(
          connectionId, null, null, null, 10, 0);

      assertThat(jobs).hasSize(2);
    }

    @Test
    void should_filterByStatus() {
      long j1 = jobExecutionRepository.insert(connectionId, "FULL_SYNC");
      jobExecutionRepository.insert(connectionId, "INCREMENTAL");
      jobExecutionRepository.casStatus(
          j1, JobExecutionStatus.PENDING, JobExecutionStatus.IN_PROGRESS);

      var jobs = jobExecutionRepository.findByConnectionId(
          connectionId, "IN_PROGRESS", null, null, 10, 0);

      assertThat(jobs).hasSize(1);
      assertThat(jobs.get(0).getStatus()).isEqualTo("IN_PROGRESS");
    }
  }

  @Nested
  @DisplayName("updateCheckpoint")
  class UpdateCheckpoint {

    @Test
    void should_updateCheckpointJson() {
      long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");

      jobExecutionRepository.updateCheckpoint(jobId, "{\"cursor\": \"abc\"}");

      var job = jobExecutionRepository.findById(jobId).orElseThrow();
      assertThat(job.getCheckpoint()).contains("abc");
    }
  }
}
