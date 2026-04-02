export type JobStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'MATERIALIZING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'RETRY_SCHEDULED'
  | 'FAILED'
  | 'STALE';

export type JobItemStatus = 'CAPTURED' | 'PROCESSED' | 'FAILED' | 'EXPIRED';

export type EtlEventType =
  | 'CATEGORY_DICT'
  | 'WAREHOUSE_DICT'
  | 'PRODUCT_DICT'
  | 'PRICE_SNAPSHOT'
  | 'INVENTORY_FACT'
  | 'SALES_FACT'
  | 'FACT_FINANCE'
  | 'PROMO_SYNC'
  | 'ADVERTISING_FACT';

export interface JobSummary {
  id: number;
  connectionId: number;
  eventType: EtlEventType;
  status: JobStatus;
  startedAt: string | null;
  completedAt: string | null;
  errorDetails: unknown | null;
  createdAt: string;
}

export interface JobItem {
  id: number;
  sourceId: string;
  pageNumber: number;
  s3Key: string;
  status: JobItemStatus;
  recordCount: number | null;
  byteSize: number;
  capturedAt: string;
  processedAt: string | null;
}

export interface JobRetryResult {
  jobId: number;
  message: string;
}

export interface JobFilter {
  status?: JobStatus;
  from?: string;
  to?: string;
}
