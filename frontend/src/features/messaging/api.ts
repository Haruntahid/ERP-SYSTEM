import { api } from '../../shared/api/client';

export type OutboxStatus = 'PENDING' | 'PROCESSING' | 'PUBLISHED' | 'FAILED' | 'DEAD_LETTERED';
export type EventPriority = 'HIGH' | 'NORMAL' | 'BULK';

export interface OutboxEventSummary {
  eventId: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  routingKey: string;
  priority: EventPriority;
  status: OutboxStatus;
  attempts: number;
  maxAttempts: number;
  nextRetryAt: string;
  lastError?: string | null;
  createdAt: string;
  publishedAt?: string | null;
}

export interface OutboxEventDetail extends OutboxEventSummary {
  payloadJson: string;
  headersJson?: string | null;
  updatedAt: string;
}

export interface OutboxPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OutboxStats {
  pendingCount: number;
  failedCount: number;
  deadLetteredCount: number;
  publishedLast24hCount: number;
  oldestPendingAgeSeconds?: number | null;
}

export interface BatchReplayResult {
  replayedCount: number;
  skippedCount: number;
}

export interface OutboxFilterParams {
  status?: OutboxStatus | '';
  eventType?: string;
  aggregateType?: string;
  page?: number;
  size?: number;
}

export async function fetchOutboxEvents(
  filters: OutboxFilterParams = {},
): Promise<OutboxPageResponse<OutboxEventSummary>> {
  const params = new URLSearchParams();
  if (filters.status) params.set('status', filters.status);
  if (filters.eventType) params.set('eventType', filters.eventType);
  if (filters.aggregateType) params.set('aggregateType', filters.aggregateType);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));
  const response = await api.get<OutboxPageResponse<OutboxEventSummary>>(
    `/api/v1/messaging/outbox?${params.toString()}`,
  );
  return response.data;
}

export async function fetchOutboxEventDetail(eventId: string): Promise<OutboxEventDetail> {
  const response = await api.get<OutboxEventDetail>(
    `/api/v1/messaging/outbox/${encodeURIComponent(eventId)}`,
  );
  return response.data;
}

export async function replayOutboxEvent(eventId: string): Promise<OutboxEventSummary> {
  const response = await api.post<OutboxEventSummary>(
    `/api/v1/messaging/outbox/${encodeURIComponent(eventId)}/replay`,
  );
  return response.data;
}

export async function replayOutboxBatch(eventIds: string[]): Promise<BatchReplayResult> {
  const response = await api.post<BatchReplayResult>('/api/v1/messaging/outbox/replay-batch', {
    eventIds,
  });
  return response.data;
}

export async function fetchOutboxStats(): Promise<OutboxStats> {
  const response = await api.get<OutboxStats>('/api/v1/messaging/stats');
  return response.data;
}
