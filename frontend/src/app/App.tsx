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
import { AcademicPage } from '../features/academic/AcademicPage';
import { AcademicSessionsPage } from '../features/academic/AcademicSessionsPage';
import { AcademicStructurePage } from '../features/academic/AcademicStructurePage';
import { StaffPage } from '../features/staff/StaffPage';
import { StudentsPage } from '../features/students/StudentsPage';
import { AttendancePage } from '../features/attendance/AttendancePage';
import { FeesPage } from '../features/fees/FeesPage';
import { NoticesPage } from '../features/notices/NoticesPage';

const implementedPaths = new Set([
  '/',
  '/messaging',
  '/academic',
  '/staff',
  '/students',
  '/attendance',
  '/fees',
  '/notices'
]);

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
                  <Route path="/academic" element={<AcademicPage />} />
                  <Route path="/academic/sessions" element={<AcademicSessionsPage />} />
                  <Route path="/academic/structure" element={<AcademicStructurePage />} />
                  <Route path="/staff" element={<StaffPage />} />
                  <Route path="/students" element={<StudentsPage />} />
                  <Route path="/attendance" element={<AttendancePage />} />
                  <Route path="/fees" element={<FeesPage />} />
                  <Route path="/notices" element={<NoticesPage />} />
                  {modules
                    .filter((m) => !implementedPaths.has(m.path))
                    .map((m) => (
                      <Route
                        key={m.key}
                        path={m.path}
                        element={<ModulePlaceholder titleKey={m.labelKey} />}
                      />
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
