import { useI18n } from '../../shared/i18n';

export function AcademicSessionsPage() {
  const { t } = useI18n();
  return (
    <div>
      <h1>{t('academic.sessions.title')}</h1>
      <p className="muted">{t('common.comingSoon')}</p>
    </div>
  );
}

