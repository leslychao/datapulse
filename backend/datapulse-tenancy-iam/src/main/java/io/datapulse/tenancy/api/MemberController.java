package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.MemberService;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<MemberResponse> listMembers(@PathVariable("workspaceId") Long workspaceId) {
        return memberService.listMembers(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public MemberResponse changeRole(@PathVariable("workspaceId") Long workspaceId,
                                     @PathVariable("userId") Long userId,
                                     @Valid @RequestBody UpdateMemberRoleRequest request) {
        WorkspaceMemberEntity member = memberService.changeRole(
                workspaceId, userId, workspaceContext.getUserId(),
                workspaceContext.getRole(), request.role());
        return toResponse(member);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void removeMember(@PathVariable("workspaceId") Long workspaceId,
                             @PathVariable("userId") Long userId) {
        memberService.removeMember(workspaceId, userId, workspaceContext.getUserId());
    }

    private MemberResponse toResponse(WorkspaceMemberEntity entity) {
        return new MemberResponse(
                entity.getUser().getId(),
                entity.getUser().getEmail(),
                entity.getUser().getName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}
