export interface AppModule {
  key: string;
  path: string;
  labelKey: string;
  /** Feature flag that must be enabled for the school. Omit for always available modules. */
  feature?: string;
}

/**
 * Single registry of navigable modules. When a module is built, point its route to the real page
 * in App.tsx. Navigation is filtered by the school's enabled features.
 */
export const modules: AppModule[] = [
  { key: 'dashboard', path: '/', labelKey: 'nav.dashboard' },
  { key: 'academic', path: '/academic', labelKey: 'academic.nav.title', feature: 'academic' },
  { key: 'staff', path: '/staff', labelKey: 'staff.nav.title', feature: 'staff' },
  { key: 'students', path: '/students', labelKey: 'nav.students', feature: 'students' },
  { key: 'attendance', path: '/attendance', labelKey: 'nav.attendance', feature: 'attendance' },
  { key: 'fees', path: '/fees', labelKey: 'nav.fees', feature: 'fees' },
  { key: 'notices', path: '/notices', labelKey: 'nav.notices', feature: 'notices' },
  { key: 'exams', path: '/exams', labelKey: 'nav.exams', feature: 'exams' },
  { key: 'library', path: '/library', labelKey: 'nav.library', feature: 'library' },
  { key: 'transport', path: '/transport', labelKey: 'nav.transport', feature: 'transport' },
  { key: 'hr', path: '/hr', labelKey: 'nav.hr', feature: 'hr' },
  { key: 'analytics', path: '/analytics', labelKey: 'nav.analytics', feature: 'analytics' },
  { key: 'messaging', path: '/messaging', labelKey: 'nav.messaging' },
];
