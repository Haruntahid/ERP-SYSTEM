import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTenantConfig } from '../features/tenant/hooks';
import { useSettings } from '../features/settings/hooks';
import { useI18n } from '../shared/i18n';
import { clearAuthTokens, getAccessToken } from '../shared/api/client';
import { modules } from './modules';

export function Layout() {
  const { data: tenant } = useTenantConfig();
  const { settings } = useSettings();
  const { t } = useI18n();
  const navigate = useNavigate();

  const visible = modules.filter((m) => !m.feature || tenant?.features[m.feature] === true);
  const displayName = settings['school.display_name'] ?? tenant?.name ?? '';
  const isAuthenticated = Boolean(getAccessToken());

  const handleLogout = () => {
    clearAuthTokens();
    navigate('/login');
  };

  return (
    <div className="shell">
      <aside className="sidebar">
        <div>
          <div className="brand">{displayName}</div>
          <nav>
            {visible.map((m) => (
              <NavLink key={m.key} to={m.path} end={m.path === '/'}>
                {t(m.labelKey)}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="stack" style={{ marginTop: 24 }}>
          {isAuthenticated ? (
            <button type="button" className="secondary" onClick={handleLogout}>
              {t('login.logout')}
            </button>
          ) : (
            <NavLink to="/login">{t('login.submit')}</NavLink>
          )}
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
