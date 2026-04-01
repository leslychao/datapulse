package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.MemberService;
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
        return memberService.listMembers(workspaceId);
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public MemberResponse changeRole(@PathVariable("workspaceId") Long workspaceId,
                                     @PathVariable("userId") Long userId,
                                     @Valid @RequestBody UpdateMemberRoleRequest request) {
        return memberService.changeRole(
                workspaceId, userId, workspaceContext.getUserId(),
                workspaceContext.getRole(), request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void removeMember(@PathVariable("workspaceId") Long workspaceId,
                             @PathVariable("userId") Long userId) {
        memberService.removeMember(workspaceId, userId, workspaceContext.getUserId());
    }

}
