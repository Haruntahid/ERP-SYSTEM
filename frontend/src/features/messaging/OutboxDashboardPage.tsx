import { useCallback, useEffect, useState } from 'react';
import { useI18n } from '../../shared/i18n';
import {
  fetchOutboxEventDetail,
  fetchOutboxEvents,
  fetchOutboxStats,
  replayOutboxBatch,
  replayOutboxEvent,
  type OutboxEventDetail,
  type OutboxEventSummary,
  type OutboxStats,
  type OutboxStatus,
} from './api';
import { OutboxEventDrawer } from './OutboxEventDrawer';

const STATUS_OPTIONS: Array<OutboxStatus | ''> = [
  '',
  'PENDING',
  'PROCESSING',
  'PUBLISHED',
  'FAILED',
  'DEAD_LETTERED',
];

export function OutboxDashboardPage() {
  const { t } = useI18n();
  const [stats, setStats] = useState<OutboxStats | null>(null);
  const [events, setEvents] = useState<OutboxEventSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState<OutboxStatus | ''>('');
  const [eventTypeFilter, setEventTypeFilter] = useState('');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [activeDetail, setActiveDetail] = useState<OutboxEventDetail | null>(null);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [statsResp, eventsResp] = await Promise.all([
        fetchOutboxStats(),
        fetchOutboxEvents({
          status: statusFilter,
          eventType: eventTypeFilter,
          page: 0,
          size: 25,
        }),
      ]);
      setStats(statsResp);
      setEvents(eventsResp.content);
    } catch {
      // Handled gracefully when unauthenticated or offline in dev preview
    } finally {
      setLoading(false);
    }
  }, [statusFilter, eventTypeFilter]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleInspect = async (eventId: string) => {
    const detail = await fetchOutboxEventDetail(eventId);
    setActiveDetail(detail);
  };

  const handleReplaySingle = async (eventId: string) => {
    await replayOutboxEvent(eventId);
    setStatusBanner(t('messaging.outbox.replay_success'));
    if (activeDetail?.eventId === eventId) {
      const updated = await fetchOutboxEventDetail(eventId);
      setActiveDetail(updated);
    }
    await loadData();
  };

  const handleReplaySelected = async () => {
    if (selectedIds.length === 0) return;
    await replayOutboxBatch(selectedIds);
    setSelectedIds([]);
    setStatusBanner(t('messaging.outbox.replay_success'));
    await loadData();
  };

  const toggleSelect = (eventId: string) => {
    setSelectedIds((prev) =>
      prev.includes(eventId) ? prev.filter((id) => id !== eventId) : [...prev, eventId],
    );
  };

  return (
    <section style={{ padding: 16 }}>
      <h2>{t('messaging.outbox.title')}</h2>

      {stats && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: 12,
            marginBottom: 16,
          }}
        >
          <div style={{ padding: 12, border: '1px solid #e5e7eb', borderRadius: 8 }}>
            <div style={{ fontSize: 12, color: '#6b7280' }}>
              {t('messaging.outbox.stats.pending')}
            </div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{stats.pendingCount}</div>
          </div>
          <div style={{ padding: 12, border: '1px solid #fde68a', borderRadius: 8 }}>
            <div style={{ fontSize: 12, color: '#92400e' }}>
              {t('messaging.outbox.stats.failed')}
            </div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{stats.failedCount}</div>
          </div>
          <div style={{ padding: 12, border: '1px solid #fecaca', borderRadius: 8 }}>
            <div style={{ fontSize: 12, color: '#991b1b' }}>
              {t('messaging.outbox.stats.dead_lettered')}
            </div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{stats.deadLetteredCount}</div>
          </div>
          <div style={{ padding: 12, border: '1px solid #bbf7d0', borderRadius: 8 }}>
            <div style={{ fontSize: 12, color: '#166534' }}>
              {t('messaging.outbox.stats.published_24h')}
            </div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{stats.publishedLast24hCount}</div>
          </div>
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12 }}>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as OutboxStatus | '')}
        >
          {STATUS_OPTIONS.map((st) => (
            <option key={st || 'ALL'} value={st}>
              {st ? t(`messaging.outbox.status.${st}`) : 'All Statuses'}
            </option>
          ))}
        </select>

        <input
          type="text"
          placeholder="Event Type (e.g. FEE_PAYMENT_RECEIVED)"
          value={eventTypeFilter}
          onChange={(e) => setEventTypeFilter(e.target.value)}
        />

        {selectedIds.length > 0 && (
          <button type="button" onClick={() => void handleReplaySelected()}>
            {t('messaging.outbox.replay_batch_btn')} ({selectedIds.length})
          </button>
        )}
      </div>

      {statusBanner && (
        <p role="status" style={{ color: '#047857', fontWeight: 500 }}>
          {statusBanner}
        </p>
      )}

      {loading ? (
        <p>{t('common.loading')}</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '1px solid #e5e7eb' }}>
              <th style={{ padding: 8 }} />
              <th style={{ padding: 8 }}>Event ID</th>
              <th style={{ padding: 8 }}>Type</th>
              <th style={{ padding: 8 }}>Aggregate</th>
              <th style={{ padding: 8 }}>Priority</th>
              <th style={{ padding: 8 }}>Status</th>
              <th style={{ padding: 8 }}>Attempts</th>
              <th style={{ padding: 8 }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {events.map((ev) => {
              const replayable = ev.status === 'FAILED' || ev.status === 'DEAD_LETTERED';
              return (
                <tr key={ev.eventId} style={{ borderBottom: '1px solid #f3f4f6' }}>
                  <td style={{ padding: 8 }}>
                    {replayable && (
                      <input
                        type="checkbox"
                        checked={selectedIds.includes(ev.eventId)}
                        onChange={() => toggleSelect(ev.eventId)}
                      />
                    )}
                  </td>
                  <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>
                    <button
                      type="button"
                      style={{ background: 'none', border: 'none', color: '#2563eb', cursor: 'pointer' }}
                      onClick={() => void handleInspect(ev.eventId)}
                    >
                      {ev.eventId}
                    </button>
                  </td>
                  <td style={{ padding: 8 }}>{ev.eventType}</td>
                  <td style={{ padding: 8 }}>
                    {ev.aggregateType}:{ev.aggregateId}
                  </td>
                  <td style={{ padding: 8 }}>{ev.priority}</td>
                  <td style={{ padding: 8 }}>{t(`messaging.outbox.status.${ev.status}`)}</td>
                  <td style={{ padding: 8 }}>
                    {ev.attempts}/{ev.maxAttempts}
                  </td>
                  <td style={{ padding: 8 }}>
                    {replayable && (
                      <button type="button" onClick={() => void handleReplaySingle(ev.eventId)}>
                        {t('messaging.outbox.replay_btn')}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <OutboxEventDrawer
        event={activeDetail}
        onClose={() => setActiveDetail(null)}
        onReplay={handleReplaySingle}
      />
    </section>
  );
}

