export type DrrTrend = 'UP' | 'DOWN' | 'FLAT';

export interface CampaignSummary {
  id: number;
  externalCampaignId: string;
  name: string;
  sourcePlatform: string;
  campaignType: string;
  status: string;
  dailyBudget: number | null;
  spendForPeriod: number | null;
  ordersForPeriod: number | null;
  drrPct: number | null;
  drrTrend: DrrTrend | null;
}

export interface CampaignDashboardFilter {
  sourcePlatform?: string;
  period?: string;
  status?: string;
}
