import { useI18n } from '../../shared/i18n';

export function ModulePlaceholder({ titleKey }: { titleKey: string }) {
  const { t } = useI18n();
  return (
    <div>
      <h1>{t(titleKey)}</h1>
      <p className="muted">{t('common.comingSoon')}</p>
    </div>
  );
}
