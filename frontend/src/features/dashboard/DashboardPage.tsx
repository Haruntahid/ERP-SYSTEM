import { useTenantConfig } from '../tenant/hooks';
import { useI18n } from '../../shared/i18n';

export function DashboardPage() {
  const { data: tenant } = useTenantConfig();
  const { t } = useI18n();

  return (
    <div>
      <h1>
        {t('dashboard.welcome')}, {tenant?.name}
      </h1>
      <p className="muted">Plan: {tenant?.plan}</p>
    </div>
  );
}
