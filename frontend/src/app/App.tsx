import { Route, Routes } from 'react-router-dom';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ModulePlaceholder } from '../features/dashboard/ModulePlaceholder';
import { LoginPage } from '../features/auth/LoginPage';
import { OutboxDashboardPage } from '../features/messaging/OutboxDashboardPage';
import { PlatformTenantsPage } from '../features/platform/PlatformTenantsPage';
import { Layout } from './Layout';
import { modules } from './modules';
import { TenantGate } from './TenantGate';
import { ThemeProvider } from './ThemeProvider';
import { AcademicSessionsPage } from '../features/academic/AcademicSessionsPage';
import { AcademicStructurePage } from '../features/academic/AcademicStructurePage';

export function App() {
  return (
    <Routes>
      <Route path="/platform" element={<PlatformTenantsPage />} />
      <Route
        path="/*"
        element={
          <TenantGate>
            <ThemeProvider>
              <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route element={<Layout />}>
                  <Route index element={<DashboardPage />} />
                  <Route path="/messaging" element={<OutboxDashboardPage />} />
                  <Route path="/academic/sessions" element={<AcademicSessionsPage />} />
                  <Route path="/academic/structure" element={<AcademicStructurePage />} />
                  <Route path="/staff" element={<ModulePlaceholder titleKey="staff.nav.title" />} />
                  {modules
                    .filter((m) => m.path !== '/' && m.path !== '/messaging' && m.path !== '/academic' && m.path !== '/staff')
                    .map((m) => (
                      <Route key={m.key} path={m.path} element={<ModulePlaceholder titleKey={m.labelKey} />} />
                    ))}
                </Route>
              </Routes>
            </ThemeProvider>
          </TenantGate>
        }
      />
    </Routes>
  );
}
