import { NavLink, Outlet } from 'react-router-dom';
import { useTenantConfig } from '../features/tenant/hooks';
import { useSettings } from '../features/settings/hooks';
import { useI18n } from '../shared/i18n';
import { modules } from './modules';

export function Layout() {
  const { data: tenant } = useTenantConfig();
  const { settings } = useSettings();
  const { t } = useI18n();

  const visible = modules.filter((m) => !m.feature || tenant?.features[m.feature] === true);
  const displayName = settings['school.display_name'] ?? tenant?.name ?? '';

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">{displayName}</div>
        <nav>
          {visible.map((m) => (
            <NavLink key={m.key} to={m.path} end={m.path === '/'}>
              {t(m.labelKey)}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
