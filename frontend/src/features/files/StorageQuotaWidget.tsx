import { useEffect, useState } from 'react';
import { fetchStorageQuota, type StorageQuotaDto } from './api';
import { useI18n } from '../../shared/i18n';

export function StorageQuotaWidget() {
  const { t } = useI18n();
  const [quota, setQuota] = useState<StorageQuotaDto | null>(null);

  useEffect(() => {
    void fetchStorageQuota()
      .then(setQuota)
      .catch(() => {
        // Ignore when unauthenticated or lacking file.admin permission
      });
  }, []);

  if (!quota) {
    return null;
  }

  const usedMb = (quota.usedBytes / (1024 * 1024)).toFixed(2);
  const quotaMb = (quota.quotaBytes / (1024 * 1024)).toFixed(0);
  const pct = quota.quotaBytes > 0 ? Math.min(100, (quota.usedBytes / quota.quotaBytes) * 100) : 0;

  return (
    <section
      style={{
        border: '1px solid #e5e7eb',
        borderRadius: 8,
        padding: 16,
        marginTop: 16,
      }}
    >
      <h4 style={{ marginTop: 0, marginBottom: 8 }}>{t('files.quota.title')}</h4>
      <div style={{ fontSize: 13, marginBottom: 6 }}>
        {usedMb} MB / {quotaMb} MB ({pct.toFixed(1)}%)
      </div>
      <div
        style={{
          height: 8,
          borderRadius: 4,
          background: '#e5e7eb',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${pct}%`,
            height: '100%',
            background: pct >= 85 ? '#dc2626' : '#2563eb',
          }}
        />
      </div>
    </section>
  );
}

