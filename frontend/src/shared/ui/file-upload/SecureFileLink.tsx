import { useState } from 'react';
import { fetchPresignedDownloadUrl } from '../../../features/files/api';
import { useI18n } from '../../i18n';

interface SecureFileLinkProps {
  fileUuid: string;
  label?: string;
  disposition?: 'inline' | 'attachment';
  onDelete?: (fileUuid: string) => Promise<void>;
}

export function SecureFileLink({
  fileUuid,
  label,
  disposition = 'attachment',
  onDelete,
}: SecureFileLinkProps) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);

  const handleDownload = async () => {
    setLoading(true);
    try {
      const signed = await fetchPresignedDownloadUrl(fileUuid, disposition);
      window.open(signed.url, '_blank', 'noopener,noreferrer');
    } finally {
      setLoading(false);
    }
  };

  return (
    <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
      <button type="button" disabled={loading} onClick={() => void handleDownload()}>
        {label ?? t('files.action.download')}
      </button>
      {onDelete && (
        <button type="button" onClick={() => void onDelete(fileUuid)}>
          {t('files.action.delete')}
        </button>
      )}
    </span>
  );
}

