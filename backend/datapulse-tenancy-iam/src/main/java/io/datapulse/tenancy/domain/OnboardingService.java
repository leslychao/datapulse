package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditPublisher;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantEntity;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int MAX_SLUG_RETRIES = 3;
    private static final int MAX_TENANTS_PER_USER = 3;

    private final TenantRepository tenantRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final AppUserRepository appUserRepository;
    private final AuditPublisher auditPublisher;

    @Transactional
    public TenantEntity createTenant(String name, Long ownerUserId) {
        long ownedCount = tenantRepository.countByOwnerUserId(ownerUserId);
        if (ownedCount >= MAX_TENANTS_PER_USER) {
            throw BadRequestException.of("tenant.limit.exceeded", MAX_TENANTS_PER_USER);
        }

        String trimmed = name.trim();
        String slug = generateUniqueSlug(trimmed, tenantRepository::existsBySlug);

        var tenant = new TenantEntity();
        tenant.setName(trimmed);
        tenant.setSlug(slug);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setOwnerUserId(ownerUserId);

        tenantRepository.save(tenant);
        auditPublisher.publish("tenant.create", "tenant", String.valueOf(tenant.getId()));
        return tenant;
    }

    @Transactional(readOnly = true)
    public TenantEntity getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.entity("Tenant", tenantId));
    }

    @Transactional
    public WorkspaceSummary createWorkspace(Long tenantId, String name, Long ownerUserId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.entity("Tenant", tenantId));

        if (!tenant.getOwnerUserId().equals(ownerUserId)) {
            throw BadRequestException.of("tenant.not.owner");
        }

        String trimmed = name.trim();
        String slug = generateUniqueSlug(trimmed,
                s -> workspaceRepository.existsByTenant_IdAndSlug(tenantId, s));

        AppUserEntity owner = appUserRepository.findById(ownerUserId)
                .orElseThrow(() -> NotFoundException.entity("AppUser", ownerUserId));

        var workspace = new WorkspaceEntity();
        workspace.setTenant(tenant);
        workspace.setName(trimmed);
        workspace.setSlug(slug);
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        workspace.setOwnerUserId(ownerUserId);
        workspaceRepository.save(workspace);

        var member = new WorkspaceMemberEntity();
        member.setWorkspace(workspace);
        member.setUser(owner);
        member.setRole(MemberRole.OWNER);
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);
        auditPublisher.publish("workspace.create", "workspace", String.valueOf(workspace.getId()));

        return new WorkspaceSummary(
                workspace.getId(), workspace.getName(), workspace.getSlug(),
                workspace.getStatus(), tenant.getId(), tenant.getName(),
                0, 1);
    }

    private String generateUniqueSlug(String name, java.util.function.Predicate<String> existsCheck) {
        String baseSlug = SlugUtils.generateSlug(name);
        if (!existsCheck.test(baseSlug)) {
            return baseSlug;
        }
        for (int i = 0; i < MAX_SLUG_RETRIES; i++) {
            String candidate = SlugUtils.appendSuffix(baseSlug);
            if (!existsCheck.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique slug after %d retries for name: %s"
                .formatted(MAX_SLUG_RETRIES, name));
    }
}
