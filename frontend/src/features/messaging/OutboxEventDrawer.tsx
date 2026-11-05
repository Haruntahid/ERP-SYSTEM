import type { OutboxEventDetail } from './api';
import { useI18n } from '../../shared/i18n';

interface OutboxEventDrawerProps {
  event: OutboxEventDetail | null;
  onClose: () => void;
  onReplay: (eventId: string) => Promise<void>;
}

export function OutboxEventDrawer({ event, onClose, onReplay }: OutboxEventDrawerProps) {
  const { t } = useI18n();

  if (!event) {
    return null;
  }

  const canReplay = event.status === 'FAILED' || event.status === 'DEAD_LETTERED';

  return (
    <aside
      aria-label="Outbox event detail drawer"
      style={{
        border: '1px solid #d1d5db',
        borderRadius: 8,
        padding: 16,
        marginTop: 16,
        background: '#f9fafb',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>
          {event.eventType} <small style={{ color: '#6b7280' }}>({event.eventId})</small>
        </h3>
        <div style={{ display: 'flex', gap: 8 }}>
          {canReplay && (
            <button type="button" onClick={() => void onReplay(event.eventId)}>
              {t('messaging.outbox.replay_btn')}
            </button>
          )}
          <button type="button" onClick={onClose}>
            ×
          </button>
        </div>
      </div>

      <dl
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: 8,
          marginTop: 12,
        }}
      >
        <div>
          <dt style={{ fontSize: 12, color: '#6b7280' }}>Aggregate</dt>
          <dd style={{ margin: 0, fontWeight: 600 }}>
            {event.aggregateType} / {event.aggregateId}
          </dd>
        </div>
        <div>
          <dt style={{ fontSize: 12, color: '#6b7280' }}>Routing Key</dt>
          <dd style={{ margin: 0 }}>{event.routingKey}</dd>
        </div>
        <div>
          <dt style={{ fontSize: 12, color: '#6b7280' }}>Status</dt>
          <dd style={{ margin: 0 }}>{t(`messaging.outbox.status.${event.status}`)}</dd>
        </div>
        <div>
          <dt style={{ fontSize: 12, color: '#6b7280' }}>Attempts</dt>
          <dd style={{ margin: 0 }}>
            {event.attempts} / {event.maxAttempts}
          </dd>
        </div>
      </dl>

      {event.lastError && (
        <div
          style={{
            marginTop: 12,
            padding: 10,
            borderRadius: 6,
            background: '#fef2f2',
            color: '#991b1b',
            fontFamily: 'monospace',
            fontSize: 12,
          }}
        >
          {event.lastError}
        </div>
      )}

      <div style={{ marginTop: 12 }}>
        <strong style={{ fontSize: 12, color: '#374151' }}>Masked Payload JSON</strong>
        <pre
          style={{
            marginTop: 4,
            padding: 10,
            borderRadius: 6,
            background: '#111827',
            color: '#f9fafb',
            fontSize: 12,
            overflowX: 'auto',
          }}
        >
          {event.payloadJson}
        </pre>
      </div>
    </aside>
  );
}

