import { useI18n } from '../../shared/i18n';

export function AcademicStructurePage() {
  const { t } = useI18n();
  return (
    <div>
      <h1>{t('academic.nav.title')}</h1>
      <p className="muted">{t('common.comingSoon')}</p>
    </div>
  );
}

