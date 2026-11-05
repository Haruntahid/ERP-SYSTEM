import { useI18n } from '../../shared/i18n';

/** Placeholder. Replace through the "Authentication" feature plan. */
export function LoginPage() {
  const { t } = useI18n();
  return (
    <div className="center-card">
      <h2>{t('login.title')}</h2>
      <p className="muted">{t('login.notReady')}</p>
    </div>
  );
}
