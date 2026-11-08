import { Link } from 'react-router-dom';
import { useTenantConfig } from '../tenant/hooks';
import { useI18n } from '../../shared/i18n';
import { StorageQuotaWidget } from '../files/StorageQuotaWidget';
import { SecurityAndCacheSettingsPanel } from '../settings/SecurityAndCacheSettingsPanel';

export function DashboardPage() {
  const { data: tenant } = useTenantConfig();
  const { t } = useI18n();

  const phaseOneCards = [
    { key: 'academic', path: '/academic', title: t('nav.academic'), desc: t('academic.subtitle') },
    { key: 'staff', path: '/staff', title: t('nav.staff'), desc: t('staff.subtitle') },
    { key: 'students', path: '/students', title: t('nav.students'), desc: t('students.subtitle') },
    { key: 'attendance', path: '/attendance', title: t('nav.attendance'), desc: t('attendance.subtitle') },
    { key: 'fees', path: '/fees', title: t('nav.fees'), desc: t('fees.subtitle') },
    { key: 'notices', path: '/notices', title: t('nav.notices'), desc: t('notices.subtitle') }
  ];

  return (
    <div className="stack">
      <div>
        <h1>
          {t('dashboard.welcome')}, {tenant?.name}
        </h1>
        <p className="muted">Plan: {tenant?.plan}</p>
      </div>

      <div className="grid-cards">
        {phaseOneCards.map((c) => (
          <Link
            key={c.key}
            to={c.path}
            className="card"
            style={{ textDecoration: 'none', color: 'inherit' }}
          >
            <h3>{c.title}</h3>
            <p className="muted">{c.desc}</p>
          </Link>
        ))}
      </div>

      <StorageQuotaWidget />
      <SecurityAndCacheSettingsPanel />
    </div>
  );
}
