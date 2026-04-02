import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  CreatePromoAssignmentRequest,
  CreatePromoPolicyRequest,
  Page,
  PromoAction,
  PromoActionFilter,
  PromoCampaign,
  PromoCampaignFilter,
  PromoCampaignSummary,
  PromoDecision,
  PromoDecisionDetail,
  PromoDecisionFilter,
  PromoEvaluation,
  PromoEvaluationFilter,
  PromoPolicy,
  PromoPolicyAssignment,
  PromoPolicyFilter,
  PromoPolicySummary,
  PromoProductFilter,
  PromoProductSummary,
  UpdatePromoPolicyRequest,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class PromoApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listCampaigns(
    workspaceId: number,
    filter: PromoCampaignFilter,
    page: number,
    size: number,
    sort = 'dateFrom,desc',
  ): Observable<Page<PromoCampaignSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.marketplaceType?.length) {
      params = params.set('marketplaceType', filter.marketplaceType.join(','));
    }
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.promoType) {
      params = params.set('promoType', filter.promoType);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<PromoCampaignSummary>>(
      `${this.base}/workspaces/${workspaceId}/promo/campaigns`,
      { params },
    );
  }

  getCampaign(workspaceId: number, campaignId: number): Observable<PromoCampaign> {
    return this.http.get<PromoCampaign>(
      `${this.base}/workspaces/${workspaceId}/promo/campaigns/${campaignId}`,
    );
  }

  listCampaignProducts(
    workspaceId: number,
    campaignId: number,
    filter: PromoProductFilter,
    page: number,
    size: number,
    sort = 'participationStatus,asc',
  ): Observable<Page<PromoProductSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.participationStatus?.length) {
      params = params.set('participationStatus', filter.participationStatus.join(','));
    }
    if (filter.evaluationResult?.length) {
      params = params.set('evaluationResult', filter.evaluationResult.join(','));
    }
    if (filter.decisionType?.length) {
      params = params.set('decisionType', filter.decisionType.join(','));
    }
    if (filter.actionStatus?.length) {
      params = params.set('actionStatus', filter.actionStatus.join(','));
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<PromoProductSummary>>(
      `${this.base}/workspaces/${workspaceId}/promo/campaigns/${campaignId}/products`,
      { params },
    );
  }

  // --- Policies ---

  listPolicies(
    workspaceId: number,
    filter: PromoPolicyFilter,
    page: number,
    size: number,
    sort = 'status,asc',
  ): Observable<Page<PromoPolicySummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.participationMode?.length) {
      params = params.set('participationMode', filter.participationMode.join(','));
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<PromoPolicySummary>>(
      `${this.base}/workspaces/${workspaceId}/promo/policies`,
      { params },
    );
  }

  getPolicy(workspaceId: number, policyId: number): Observable<PromoPolicy> {
    return this.http.get<PromoPolicy>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}`,
    );
  }

  createPolicy(
    workspaceId: number,
    req: CreatePromoPolicyRequest,
  ): Observable<PromoPolicy> {
    return this.http.post<PromoPolicy>(
      `${this.base}/workspaces/${workspaceId}/promo/policies`,
      req,
    );
  }

  updatePolicy(
    workspaceId: number,
    policyId: number,
    req: UpdatePromoPolicyRequest,
  ): Observable<PromoPolicy> {
    return this.http.put<PromoPolicy>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}`,
      req,
    );
  }

  activatePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}/activate`,
      {},
    );
  }

  pausePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}/pause`,
      {},
    );
  }

  archivePolicy(workspaceId: number, policyId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}/archive`,
      {},
    );
  }

  // --- Assignments ---

  listAssignments(
    workspaceId: number,
    policyId: number,
  ): Observable<PromoPolicyAssignment[]> {
    return this.http.get<PromoPolicyAssignment[]>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}/assignments`,
    );
  }

  createAssignment(
    workspaceId: number,
    policyId: number,
    req: CreatePromoAssignmentRequest,
  ): Observable<PromoPolicyAssignment> {
    return this.http.post<PromoPolicyAssignment>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}/assignments`,
      req,
    );
  }

  deleteAssignment(
    workspaceId: number,
    policyId: number,
    assignmentId: number,
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspaces/${workspaceId}/promo/policies/${policyId}/assignments/${assignmentId}`,
    );
  }

  // --- Evaluations ---

  listEvaluations(
    workspaceId: number,
    filter: PromoEvaluationFilter,
    page: number,
    size: number,
    sort = 'evaluatedAt,desc',
  ): Observable<Page<PromoEvaluation>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.campaignId) {
      params = params.set('campaignId', filter.campaignId);
    }
    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.marketplaceType?.length) {
      params = params.set('marketplaceType', filter.marketplaceType.join(','));
    }
    if (filter.evaluationResult?.length) {
      params = params.set('evaluationResult', filter.evaluationResult.join(','));
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<PromoEvaluation>>(
      `${this.base}/workspaces/${workspaceId}/promo/evaluations`,
      { params },
    );
  }

  // --- Decisions ---

  listDecisions(
    workspaceId: number,
    filter: PromoDecisionFilter,
    page: number,
    size: number,
    sort = 'createdAt,desc',
  ): Observable<Page<PromoDecision>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.campaignId) {
      params = params.set('campaignId', filter.campaignId);
    }
    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.decisionType?.length) {
      params = params.set('decisionType', filter.decisionType.join(','));
    }
    if (filter.decidedBy && filter.decidedBy !== 'all') {
      params = params.set('decidedBy', filter.decidedBy);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<PromoDecision>>(
      `${this.base}/workspaces/${workspaceId}/promo/decisions`,
      { params },
    );
  }

  getDecision(
    workspaceId: number,
    decisionId: number,
  ): Observable<PromoDecisionDetail> {
    return this.http.get<PromoDecisionDetail>(
      `${this.base}/workspaces/${workspaceId}/promo/decisions/${decisionId}`,
    );
  }

  // --- Manual Actions ---

  participate(
    workspaceId: number,
    promoProductId: number,
    body: { targetPromoPrice?: number; reason?: string },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/products/${promoProductId}/participate`,
      body,
    );
  }

  decline(
    workspaceId: number,
    promoProductId: number,
    body: { reason?: string },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/products/${promoProductId}/decline`,
      body,
    );
  }

  approveAction(workspaceId: number, actionId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/actions/${actionId}/approve`,
      {},
    );
  }

  rejectAction(
    workspaceId: number,
    actionId: number,
    body: { reason: string },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/actions/${actionId}/reject`,
      body,
    );
  }

  cancelAction(
    workspaceId: number,
    actionId: number,
    body: { cancelReason: string },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/actions/${actionId}/cancel`,
      body,
    );
  }

  deactivate(
    workspaceId: number,
    promoProductId: number,
    body: { reason?: string },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/products/${promoProductId}/deactivate`,
      body,
    );
  }

  listActions(
    workspaceId: number,
    filter: PromoActionFilter,
    page: number,
    size: number,
    sort = 'createdAt,desc',
  ): Observable<Page<PromoAction>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.campaignId) {
      params = params.set('campaignId', filter.campaignId);
    }
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.actionType?.length) {
      params = params.set('actionType', filter.actionType.join(','));
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<Page<PromoAction>>(
      `${this.base}/workspaces/${workspaceId}/promo/actions`,
      { params },
    );
  }

  bulkApprove(
    workspaceId: number,
    body: { actionIds: number[] },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/actions/bulk-approve`,
      body,
    );
  }

  bulkReject(
    workspaceId: number,
    body: { actionIds: number[]; reason: string },
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/promo/actions/bulk-reject`,
      body,
    );
  }
}
