import { FormEvent, ReactNode, useState } from 'react';
import { useTenantConfig } from '../features/tenant/hooks';
import { getErrorMessage } from '../shared/api/client';
import { clearDevTenant, resolveTenantSlug, setDevTenant } from '../shared/tenant/tenant';

/** Makes sure a school is selected and reachable before rendering school pages. */
export function TenantGate({ children }: { children: ReactNode }) {
  const slug = resolveTenantSlug();
  const config = useTenantConfig();
  const [input, setInput] = useState('');

  if (slug === null) {
    if (!import.meta.env.DEV) {
      return <div className="center-card">No school selected.</div>;
    }
    const submit = (event: FormEvent) => {
      event.preventDefault();
      if (input.trim()) {
        setDevTenant(input.trim());
        window.location.reload();
      }
    };
    return (
      <form className="center-card" onSubmit={submit}>
        <h2>Select school (development)</h2>
        <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="school slug" />
        <button type="submit">Continue</button>
        <p className="muted">
          Create schools in the <a href="/platform">platform console</a>.
        </p>
      </form>
    );
  }

  if (config.isLoading) {
    return <div className="center-card">Loading...</div>;
  }

  if (config.isError) {
    return (
      <div className="center-card">
        <h2>Cannot open school "{slug}"</h2>
        <p>{getErrorMessage(config.error)}</p>
        {import.meta.env.DEV && (
          <button
            onClick={() => {
              clearDevTenant();
              window.location.reload();
            }}
          >
            Choose another school
          </button>
        )}
      </div>
    );
  }

  return <>{children}</>;
}
