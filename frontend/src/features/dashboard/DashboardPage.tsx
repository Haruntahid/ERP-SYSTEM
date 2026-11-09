import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTenantConfig } from '../tenant/hooks';
import { useI18n } from '../../shared/i18n';
import { api } from '../../shared/api/client';
import { StorageQuotaWidget } from '../files/StorageQuotaWidget';

interface PublicStatsDto {
  stats: {
    totalStudents: number;
    totalTeachers: number;
    totalClasses: number;
    totalCampuses: number;
  };
}

export function DashboardPage() {
  const { data: tenant } = useTenantConfig();
  const { t } = useI18n();
  const [stats, setStats] = useState({
    totalStudents: 0,
    totalTeachers: 0,
    totalClasses: 0,
    totalCampuses: 1
  });

  useEffect(() => {
    void api
      .get<PublicStatsDto>('/api/v1/public/school-site')
      .then((res) => {
        if (res.data?.stats) {
          setStats(res.data.stats);
        }
      })
      .catch(() => {
        // ignore
      });
  }, []);

  const phaseOneCards = [
    { key: 'website', path: '/site', title: t('nav.website'), desc: 'Public school homepage, faculty showcase, notices & online admission form.' },
    { key: 'academic', path: '/academic', title: t('nav.academic'), desc: t('academic.subtitle') },
    { key: 'staff', path: '/staff', title: t('nav.staff'), desc: t('staff.subtitle') },
    { key: 'students', path: '/students', title: t('nav.students'), desc: t('students.subtitle') },
    { key: 'attendance', path: '/attendance', title: t('nav.attendance'), desc: t('attendance.subtitle') },
    { key: 'fees', path: '/fees', title: t('nav.fees'), desc: t('fees.subtitle') },
    { key: 'notices', path: '/notices', title: t('nav.notices'), desc: t('notices.subtitle') },
    { key: 'settings', path: '/settings', title: t('nav.settings'), desc: t('settings.subtitle') }
  ];

  return (
    <div className="stack">
      <div className="card page-header" style={{ background: 'linear-gradient(135deg, #0f172a 0%, var(--color-primary) 100%)', color: '#fff' }}>
        <div>
          <span className="badge" style={{ background: 'rgba(255,255,255,0.2)', color: '#fff', marginBottom: 8 }}>
            {tenant?.plan ?? 'ENTERPRISE'} PLAN • SUPERADMIN WORKSPACE
          </span>
          <h1 style={{ color: '#fff', marginBottom: 4 }}>
            {t('dashboard.welcome')}, {tenant?.name}
          </h1>
          <p style={{ color: 'rgba(255,255,255,0.85)', margin: 0 }}>
            {t('dashboard.subtitle')}
          </p>
        </div>
        <div className="action-row">
          <Link
            to="/site"
            className="btn"
            style={{ background: 'var(--color-accent)', color: '#0f172a', fontWeight: 700 }}
          >
            🌐 {t('dashboard.openWebsite')}
          </Link>
          <Link
            to="/settings"
            className="btn secondary"
            style={{ background: 'rgba(255,255,255,0.14)', color: '#fff', borderColor: 'rgba(255,255,255,0.3)' }}
          >
            ⚙️ {t('dashboard.openSettings')}
          </Link>
        </div>
      </div>

      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-value">{stats.totalStudents}</div>
          <div className="kpi-label">{t('website.stats.students')}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{stats.totalTeachers}</div>
          <div className="kpi-label">{t('website.stats.teachers')}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{stats.totalClasses}</div>
          <div className="kpi-label">{t('website.stats.classes')}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{stats.totalCampuses}</div>
          <div className="kpi-label">{t('website.stats.campuses')}</div>
        </div>
      </div>

      <div className="grid-cards">
        {phaseOneCards.map((c) => (
          <Link
            key={c.key}
            to={c.path}
            className="module-card"
            style={{ textDecoration: 'none', color: 'inherit' }}
          >
            <div className="page-header">
              <h3 style={{ marginBottom: 0 }}>{c.title}</h3>
              <span style={{ color: 'var(--color-primary)', fontWeight: 700 }}>&rarr;</span>
            </div>
            <p className="muted" style={{ margin: 0 }}>{c.desc}</p>
          </Link>
        ))}
      </div>

      <StorageQuotaWidget />
    </div>
  );
}
