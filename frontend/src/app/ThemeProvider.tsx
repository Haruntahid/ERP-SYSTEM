import { ReactNode, useEffect } from 'react';
import { useSettings } from '../features/settings/hooks';

/** Applies the school's branding (settings) as CSS variables. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { settings } = useSettings();
  const primary = settings['brand.primary_color'];

  useEffect(() => {
    if (primary && /^#[0-9a-fA-F]{6}$/.test(primary)) {
      document.documentElement.style.setProperty('--color-primary', primary);
    }
  }, [primary]);

  return <>{children}</>;
}
