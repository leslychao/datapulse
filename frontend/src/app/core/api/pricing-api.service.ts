import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  CreateAssignmentRequest,
  CreateLockRequest,
  CreatePolicyRequest,
  ImpactPreviewResponse,
  ManualPriceLock,
  PolicyAssignment,
  PricingDecisionDetail,
  PricingDecisionFilter,
  PricingDecisionSummary,
  PricingFilter,
  PricingLockFilter,
  PricingPolicy,
  PricingPolicySummary,
  PricingRunDetail,
  PricingRunFilter,
  PricingRunSummary,
  UpdatePolicyRequest,
  Page,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class PricingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listPolicies(
    workspaceId: number,
    filter: PricingFilter,
    page: number,
    size: number,
    sort = 'createdAt,desc',
  ): Observable<Page<PricingPolicySummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.strategyType) {
      params = params.set('strategyType', filter.strategyType);
    }
    if (filter.executionMode?.length) {
      params = params.set('executionMode', filter.executionMode.join(','));
    }

    return this.http.get<Page<PricingPolicySummary>>(
      `${this.base}/workspace/${workspaceId}/pricing/policies`,
      { params },
    );
  }

  getPolicy(workspaceId: number, policyId: number): Observable<PricingPolicy> {
    return this.http.get<PricingPolicy>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}`,
    );
  }

  createPolicy(workspaceId: number, req: CreatePolicyRequest): Observable<PricingPolicy> {
    return this.http.post<PricingPolicy>(
      `${this.base}/workspace/${workspaceId}/pricing/policies`,
      req,
    );
  }

  updatePolicy(
    workspaceId: number,
    policyId: number,
    req: UpdatePolicyRequest,
  ): Observable<PricingPolicy> {
    return this.http.put<PricingPolicy>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}`,
      req,
    );
  }

  deletePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}`,
    );
  }

  activatePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/activate`,
      {},
    );
  }

  pausePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/pause`,
      {},
    );
  }

  archivePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/archive`,
      {},
    );
  }

  listAssignments(
    workspaceId: number,
    policyId: number,
  ): Observable<PolicyAssignment[]> {
    return this.http.get<PolicyAssignment[]>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/assignments`,
    );
  }

  createAssignment(
    workspaceId: number,
    policyId: number,
    req: CreateAssignmentRequest,
  ): Observable<PolicyAssignment> {
    return this.http.post<PolicyAssignment>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/assignments`,
      req,
    );
  }

  deleteAssignment(
    workspaceId: number,
    policyId: number,
    assignmentId: number,
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/assignments/${assignmentId}`,
    );
  }

  simulateImpact(
    workspaceId: number,
    policyId: number,
    page = 0,
    size = 50,
  ): Observable<ImpactPreviewResponse> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.post<ImpactPreviewResponse>(
      `${this.base}/workspace/${workspaceId}/pricing/policies/${policyId}/preview`,
      {},
      { params },
    );
  }

  listRuns(
    workspaceId: number,
    filter: PricingRunFilter,
    page: number,
    size: number,
    sort = 'createdAt,desc',
  ): Observable<Page<PricingRunSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.triggerType?.length) {
      params = params.set('triggerType', filter.triggerType.join(','));
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }

    return this.http.get<Page<PricingRunSummary>>(
      `${this.base}/workspace/${workspaceId}/pricing/runs`,
      { params },
    );
  }

  getRunDetail(workspaceId: number, runId: number): Observable<PricingRunDetail> {
    return this.http.get<PricingRunDetail>(
      `${this.base}/workspace/${workspaceId}/pricing/runs/${runId}`,
    );
  }

  triggerManualRun(
    workspaceId: number,
    connectionId: number,
  ): Observable<PricingRunSummary> {
    return this.http.post<PricingRunSummary>(
      `${this.base}/workspace/${workspaceId}/pricing/runs`,
      { connectionId },
    );
  }

  listDecisions(
    workspaceId: number,
    filter: PricingDecisionFilter,
    page: number,
    size: number,
    sort = 'createdAt,desc',
  ): Observable<Page<PricingDecisionSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.marketplaceOfferId) {
      params = params.set('marketplaceOfferId', filter.marketplaceOfferId);
    }
    if (filter.decisionType?.length) {
      params = params.set('decisionType', filter.decisionType.join(','));
    }
    if (filter.pricingRunId) {
      params = params.set('pricingRunId', filter.pricingRunId);
    }
    if (filter.executionMode) {
      params = params.set('executionMode', filter.executionMode);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }

    return this.http.get<Page<PricingDecisionSummary>>(
      `${this.base}/workspace/${workspaceId}/pricing/decisions`,
      { params },
    );
  }

  getDecisionDetail(
    workspaceId: number,
    decisionId: number,
  ): Observable<PricingDecisionDetail> {
    return this.http.get<PricingDecisionDetail>(
      `${this.base}/workspace/${workspaceId}/pricing/decisions/${decisionId}`,
    );
  }

  listLocks(
    workspaceId: number,
    filter: PricingLockFilter,
    page: number,
    size: number,
    sort = 'lockedAt,desc',
  ): Observable<Page<ManualPriceLock>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<ManualPriceLock>>(
      `${this.base}/workspace/${workspaceId}/pricing/locks`,
      { params },
    );
  }

  createLock(workspaceId: number, req: CreateLockRequest): Observable<ManualPriceLock> {
    return this.http.post<ManualPriceLock>(
      `${this.base}/workspace/${workspaceId}/pricing/locks`,
      req,
    );
  }

  deleteLock(workspaceId: number, lockId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspace/${workspaceId}/pricing/locks/${lockId}`,
    );
  }
}
