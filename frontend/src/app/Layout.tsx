import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTenantConfig } from '../features/tenant/hooks';
import { useSettings } from '../features/settings/hooks';
import { useI18n } from '../shared/i18n';
import { api, clearAuthTokens, getAccessToken } from '../shared/api/client';
import { modules } from './modules';

interface CurrentUserProfile {
  id: number;
  email: string;
  fullName: string;
  tenant: string;
  roles: string[];
}

export function Layout() {
  const { data: tenant } = useTenantConfig();
  const { settings } = useSettings();
  const { t, locale, setLocale } = useI18n();
  const navigate = useNavigate();
  const [me, setMe] = useState<CurrentUserProfile | null>(null);

  const visible = modules.filter((m) => !m.feature || tenant?.features[m.feature] === true);
  const displayName = settings['school.display_name'] ?? tenant?.name ?? 'School ERP';
  const isAuthenticated = Boolean(getAccessToken());

  useEffect(() => {
    if (!isAuthenticated) {
      setMe(null);
      return;
    }
    void api
      .get<CurrentUserProfile>('/api/v1/auth/me')
      .then((res) => setMe(res.data))
      .catch(() => setMe(null));
  }, [isAuthenticated]);

  const handleLogout = () => {
    clearAuthTokens();
    setMe(null);
    navigate('/login');
  };

  const initials = displayName
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0] ?? '')
    .join('')
    .toUpperCase();

  return (
    <div className="shell">
      <aside className="sidebar">
        <div>
          <div className="brand-box">
            <div className="brand-avatar">{initials || 'S'}</div>
            <div>
              <div className="brand">{displayName}</div>
              <div className="brand-sub">
                {tenant?.slug ? `${tenant.slug} • ${tenant.plan}` : 'Multi-Tenant ERP'}
              </div>
            </div>
          </div>

          <nav>
            {visible.map((m) => (
              <NavLink key={m.key} to={m.path} end={m.path === '/'}>
                {t(m.labelKey)}
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="stack" style={{ marginTop: 24, gap: 10 }}>
          <Link
            to="/site"
            className="btn secondary"
            style={{
              background: 'rgba(255,255,255,0.08)',
              color: '#f8fafc',
              borderColor: 'rgba(255,255,255,0.16)'
            }}
          >
            🌐 {t('nav.website')}
          </Link>
          {isAuthenticated ? (
            <button type="button" className="danger" onClick={handleLogout}>
              {t('login.logout')}
            </button>
          ) : (
            <NavLink to="/login" className="btn">
              {t('login.submit')}
            </NavLink>
          )}
        </div>
      </aside>

      <div className="main-area">
        <header className="topbar">
          <div>
            <strong style={{ fontSize: '1.02rem' }}>{displayName}</strong>
            <span className="muted" style={{ marginLeft: 10 }}>
              {tenant?.slug ? `Tenant: ${tenant.slug}` : ''}
            </span>
          </div>

          <div className="topbar-actions">
            <div className="lang-switch" title={t('common.language')}>
              <button
                type="button"
                className={locale === 'en' ? 'active' : ''}
                onClick={() => setLocale('en')}
              >
                EN
              </button>
              <button
                type="button"
                className={locale === 'bn' ? 'active' : ''}
                onClick={() => setLocale('bn')}
              >
                বাংলা
              </button>
            </div>

            <Link to="/site" className="btn secondary">
              {t('dashboard.openWebsite')}
            </Link>

            {me ? (
              <Link to="/settings" className="user-chip">
                <span>{me.fullName}</span>
                <span className="badge active">
                  {me.roles?.includes('SUPER_ADMIN')
                    ? 'SUPER_ADMIN'
                    : me.roles?.[0] ?? 'ADMIN'}
                </span>
              </Link>
            ) : (
              <Link to="/login" className="btn">
                {t('login.submit')}
              </Link>
            )}
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
