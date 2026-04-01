package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.tenancy.domain.MemberRole;
import io.datapulse.tenancy.domain.MemberStatus;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantEntity;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  @Autowired
  private WorkspaceMemberRepository memberRepository;

  private TenantEntity tenant;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    tenant = tenantRepository.save(aTenant().withOwnerUserId(user.getId()).build());
  }

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    void should_persistWorkspace_withTenantRelation() {
      var ws = aWorkspace().withTenant(tenant).withOwnerUserId(tenant.getOwnerUserId()).build();

      var saved = workspaceRepository.save(ws);

      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getTenant().getId()).isEqualTo(tenant.getId());
    }
  }

  @Nested
  @DisplayName("findByTenant_IdAndSlug")
  class FindByTenantIdAndSlug {

    @Test
    void should_returnWorkspace_when_tenantAndSlugMatch() {
      var ws = workspaceRepository.save(
          aWorkspace().withTenant(tenant).withSlug("ws-slug")
              .withOwnerUserId(tenant.getOwnerUserId()).build());

      var found = workspaceRepository.findByTenant_IdAndSlug(tenant.getId(), "ws-slug");

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(ws.getId());
    }

    @Test
    void should_returnEmpty_when_slugNotInTenant() {
      assertThat(workspaceRepository.findByTenant_IdAndSlug(tenant.getId(), "nope")).isEmpty();
    }
  }

  @Nested
  @DisplayName("WorkspaceMemberRepository")
  class MemberTests {

    @Test
    void should_findMember_byWorkspaceAndUser() {
      var ws = workspaceRepository.save(
          aWorkspace().withTenant(tenant).withOwnerUserId(tenant.getOwnerUserId()).build());
      var user = appUserRepository.save(aUser().withEmail("member@test.com").build());

      var member = new WorkspaceMemberEntity();
      member.setWorkspace(ws);
      member.setUser(user);
      member.setRole(MemberRole.ADMIN);
      member.setStatus(MemberStatus.ACTIVE);
      memberRepository.save(member);

      var found = memberRepository.findByWorkspace_IdAndUser_Id(ws.getId(), user.getId());
      assertThat(found).isPresent();
      assertThat(found.get().getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    void should_countByRole() {
      var ws = workspaceRepository.save(
          aWorkspace().withTenant(tenant).withOwnerUserId(tenant.getOwnerUserId()).build());
      var u1 = appUserRepository.save(aUser().build());
      var u2 = appUserRepository.save(aUser().build());

      saveMember(ws, u1, MemberRole.ADMIN);
      saveMember(ws, u2, MemberRole.VIEWER);

      assertThat(memberRepository.countByWorkspace_IdAndRoleAndStatus(
          ws.getId(), MemberRole.ADMIN, MemberStatus.ACTIVE)).isEqualTo(1);
      assertThat(memberRepository.countByWorkspace_IdAndStatus(
          ws.getId(), MemberStatus.ACTIVE)).isEqualTo(2);
    }

    private void saveMember(
        io.datapulse.tenancy.persistence.WorkspaceEntity ws,
        io.datapulse.tenancy.persistence.AppUserEntity user,
        MemberRole role) {
      var m = new WorkspaceMemberEntity();
      m.setWorkspace(ws);
      m.setUser(user);
      m.setRole(role);
      m.setStatus(MemberStatus.ACTIVE);
      memberRepository.save(m);
    }
  }
}
