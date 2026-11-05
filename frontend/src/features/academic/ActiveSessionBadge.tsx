import { useI18n } from '../../shared/i18n';

export function ActiveSessionBadge() {
  const { t } = useI18n();
  return <span className="badge">{t('academic.sessions.status.ACTIVE')}</span>;
}

