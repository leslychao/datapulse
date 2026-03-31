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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.datapulse.etl.domain.JobMonitoringService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class JobController {

    private final JobMonitoringService jobMonitoringService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/connections/{connectionId}/jobs")
    public Page<JobExecutionResponse> listJobs(@PathVariable("connectionId") Long connectionId,
                                               JobFilter filter,
                                               Pageable pageable) {
        return jobMonitoringService.listJobs(connectionId, workspaceContext.getWorkspaceId(),
                filter, pageable);
    }

    @GetMapping("/jobs/{jobId}")
    public JobExecutionResponse getJob(@PathVariable("jobId") Long jobId) {
        return jobMonitoringService.getJob(jobId, workspaceContext.getWorkspaceId());
    }

    @GetMapping("/jobs/{jobId}/items")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public List<JobItemResponse> getJobItems(@PathVariable("jobId") Long jobId) {
        return jobMonitoringService.getJobItems(jobId, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/jobs/{jobId}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public JobRetryResponse retryJob(@PathVariable("jobId") Long jobId) {
        return jobMonitoringService.retryJob(jobId, workspaceContext.getWorkspaceId());
    }
}
