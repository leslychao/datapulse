package io.datapulse.etl.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.datapulse.etl.domain.CostProfileService;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/cost-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CostProfileController {

    private final CostProfileService costProfileService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<CostProfileResponse> listCostProfiles(CostProfileFilter filter, Pageable pageable) {
        return costProfileService.listCurrentProfiles(
                workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')")
    public CostProfileResponse createCostProfile(@Valid @RequestBody CreateCostProfileRequest request) {
        return costProfileService.createProfile(request, workspaceContext.getUserId());
    }

    @PostMapping(value = "/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')")
    public BulkImportResponse bulkImport(@RequestParam("file") MultipartFile file) {
        return costProfileService.bulkImport(file,
                workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
    }

    @GetMapping("/{sellerSkuId}/history")
    public List<CostProfileResponse> getHistory(@PathVariable("sellerSkuId") Long sellerSkuId) {
        return costProfileService.getHistory(sellerSkuId);
    }
}
