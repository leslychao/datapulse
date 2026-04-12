import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  BidActionSummary,
  BidDecisionDetail,
  BidDecisionFilter,
  BidDecisionSummary,
  BiddingDashboard,
  BidPolicyAssignment,
  BidPolicyDetail,
  BidPolicyFilter,
  BidPolicySummary,
  BiddingRunSummary,
  CreateBidAssignmentRequest,
  CreateBidPolicyRequest,
  CreateManualBidLockRequest,
  ManualBidLock,
  Page,
  UpdateBidPolicyRequest,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class BiddingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // ── Policies ──

  listPolicies(
    workspaceId: number,
    filter?: BidPolicyFilter,
    page = 0,
    size = 50,
    sort = 'createdAt,desc',
  ): Observable<Page<BidPolicySummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter?.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter?.strategyType) {
      params = params.set('strategyType', filter.strategyType);
    }
    if (filter?.executionMode?.length) {
      params = params.set('executionMode', filter.executionMode.join(','));
    }

    return this.http.get<Page<BidPolicySummary>>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies`,
      { params },
    );
  }

  getPolicy(
    workspaceId: number,
    policyId: number,
  ): Observable<BidPolicyDetail> {
    return this.http.get<BidPolicyDetail>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}`,
    );
  }

  createPolicy(
    workspaceId: number,
    req: CreateBidPolicyRequest,
  ): Observable<BidPolicyDetail> {
    return this.http.post<BidPolicyDetail>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies`,
      { ...req, config: JSON.stringify(req.config) },
    );
  }

  updatePolicy(
    workspaceId: number,
    policyId: number,
    req: UpdateBidPolicyRequest,
  ): Observable<BidPolicyDetail> {
    return this.http.put<BidPolicyDetail>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}`,
      { ...req, config: JSON.stringify(req.config) },
    );
  }

  activatePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/activate`,
      {},
    );
  }

  pausePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/pause`,
      {},
    );
  }

  archivePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/archive`,
      {},
    );
  }

  // ── Assignments ──

  listAssignments(
    workspaceId: number,
    policyId: number,
    page = 0,
    size = 50,
  ): Observable<Page<BidPolicyAssignment>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<BidPolicyAssignment>>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/assignments`,
      { params },
    );
  }

  createAssignment(
    workspaceId: number,
    policyId: number,
    req: CreateBidAssignmentRequest,
  ): Observable<BidPolicyAssignment> {
    return this.http.post<BidPolicyAssignment>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/assignments`,
      req,
    );
  }

  bulkAssign(
    workspaceId: number,
    policyId: number,
    offerIds: number[],
    scope: string,
  ): Observable<BidPolicyAssignment[]> {
    return this.http.post<BidPolicyAssignment[]>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/assignments/bulk`,
      { offerIds, scope },
    );
  }

  deleteAssignment(
    workspaceId: number,
    policyId: number,
    assignmentId: number,
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/assignments/${assignmentId}`,
    );
  }

  // ── Decisions ──

  listDecisions(
    workspaceId: number,
    filter?: BidDecisionFilter,
    page = 0,
    size = 50,
    sort = 'createdAt,desc',
  ): Observable<Page<BidDecisionSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter?.bidPolicyId) {
      params = params.set('bidPolicyId', filter.bidPolicyId);
    }
    if (filter?.biddingRunId) {
      params = params.set('biddingRunId', filter.biddingRunId);
    }
    if (filter?.marketplaceOfferId) {
      params = params.set('marketplaceOfferId', filter.marketplaceOfferId);
    }
    if (filter?.decisionType?.length) {
      params = params.set('decisionType', filter.decisionType.join(','));
    }
    if (filter?.dateFrom) {
      params = params.set('dateFrom', filter.dateFrom);
    }
    if (filter?.dateTo) {
      params = params.set('dateTo', filter.dateTo);
    }

    return this.http.get<Page<BidDecisionSummary>>(
      `${this.base}/workspaces/${workspaceId}/bidding/decisions`,
      { params },
    );
  }

  getDecision(
    workspaceId: number,
    decisionId: number,
  ): Observable<BidDecisionDetail> {
    return this.http.get<BidDecisionDetail>(
      `${this.base}/workspaces/${workspaceId}/bidding/decisions/${decisionId}`,
    );
  }

  // ── Runs ──

  listRuns(
    workspaceId: number,
    policyId?: number,
    page = 0,
    size = 50,
  ): Observable<Page<BiddingRunSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (policyId) {
      params = params.set('bidPolicyId', policyId);
    }
    return this.http.get<Page<BiddingRunSummary>>(
      `${this.base}/workspaces/${workspaceId}/bidding/runs`,
      { params },
    );
  }

  getRunDetail(
    workspaceId: number,
    runId: number,
  ): Observable<BiddingRunSummary> {
    return this.http.get<BiddingRunSummary>(
      `${this.base}/workspaces/${workspaceId}/bidding/runs/${runId}`,
    );
  }

  triggerRun(workspaceId: number, bidPolicyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/runs`,
      { bidPolicyId },
    );
  }

  // ── Locks ──

  listLocks(
    workspaceId: number,
    page = 0,
    size = 50,
  ): Observable<Page<ManualBidLock>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ManualBidLock>>(
      `${this.base}/workspaces/${workspaceId}/bidding/locks`,
      { params },
    );
  }

  createLock(
    workspaceId: number,
    req: CreateManualBidLockRequest,
  ): Observable<ManualBidLock> {
    return this.http.post<ManualBidLock>(
      `${this.base}/workspaces/${workspaceId}/bidding/locks`,
      req,
    );
  }

  deleteLock(workspaceId: number, lockId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/locks/${lockId}`,
    );
  }

  bulkCreateLocks(
    workspaceId: number,
    requests: CreateManualBidLockRequest[],
  ): Observable<ManualBidLock[]> {
    return this.http.post<ManualBidLock[]>(
      `${this.base}/workspaces/${workspaceId}/bidding/locks/bulk`,
      requests,
    );
  }

  bulkRemoveLocks(
    workspaceId: number,
    lockIds: number[],
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/locks/bulk-unlock`,
      { lockIds },
    );
  }

  bulkUnassign(
    workspaceId: number,
    policyId: number,
    offerIds: number[],
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/policies/${policyId}/assignments/bulk-unassign`,
      { marketplaceOfferIds: offerIds },
    );
  }

  // ── Actions ──

  listActions(
    workspaceId: number,
    filter?: { status?: string[]; executionMode?: string[] },
    page = 0,
    size = 50,
    sort = 'createdAt,desc',
  ): Observable<Page<BidActionSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter?.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter?.executionMode?.length) {
      params = params.set('executionMode', filter.executionMode.join(','));
    }

    return this.http.get<Page<BidActionSummary>>(
      `${this.base}/workspaces/${workspaceId}/bidding/actions`,
      { params },
    );
  }

  approveAction(workspaceId: number, actionId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/actions/${actionId}/approve`,
      {},
    );
  }

  rejectAction(workspaceId: number, actionId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/actions/${actionId}/reject`,
      {},
    );
  }

  bulkApproveActions(
    workspaceId: number,
    actionIds: number[],
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/actions/bulk-approve`,
      { actionIds },
    );
  }

  bulkRejectActions(
    workspaceId: number,
    actionIds: number[],
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/bidding/actions/bulk-reject`,
      { actionIds },
    );
  }

  // ── Dashboard ──

  getDashboard(workspaceId: number): Observable<BiddingDashboard> {
    return this.http.get<BiddingDashboard>(
      `${this.base}/workspaces/${workspaceId}/bidding/dashboard`,
    );
  }

  // ── CSV Export ──

  exportDecisionsCsv(
    workspaceId: number,
    filter?: BidDecisionFilter,
  ): Observable<Blob> {
    let params = new HttpParams();
    if (filter?.bidPolicyId) {
      params = params.set('bidPolicyId', filter.bidPolicyId);
    }
    if (filter?.dateFrom) {
      params = params.set('dateFrom', filter.dateFrom);
    }
    if (filter?.dateTo) {
      params = params.set('dateTo', filter.dateTo);
    }
    return this.http.get(
      `${this.base}/workspaces/${workspaceId}/bidding/decisions/export`,
      { params, responseType: 'blob' },
    );
  }
}
