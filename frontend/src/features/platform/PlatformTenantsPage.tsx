import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getErrorMessage } from '../../shared/api/client';
import { createTenant, listTenants, setTenantStatus } from './api';

const KEY_STORAGE = 'platformKey';

/** Operator console to create and manage schools. Temporary until real operator authentication exists. */
export function PlatformTenantsPage() {
  const queryClient = useQueryClient();
  const [key, setKey] = useState(() => window.sessionStorage.getItem(KEY_STORAGE) ?? '');
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [plan, setPlan] = useState('BASIC');

  const tenants = useQuery({
    queryKey: ['platform-tenants', key],
    queryFn: () => listTenants(key),
    enabled: key.length > 0,
    retry: false,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });

  const create = useMutation({
    mutationFn: () => createTenant(key, { slug, name, plan }),
    onSuccess: () => {
      setSlug('');
      setName('');
      return refresh();
    },
  });

  const toggle = useMutation({
    mutationFn: (args: { slug: string; status: 'ACTIVE' | 'SUSPENDED' }) =>
      setTenantStatus(key, args.slug, args.status),
    onSuccess: refresh,
  });

  const saveKey = (value: string) => {
    setKey(value);
    window.sessionStorage.setItem(KEY_STORAGE, value);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate();
  };

  return (
    <div className="page">
      <h1>Platform console</h1>

      <label>
        Platform key
        <input type="password" value={key} onChange={(e) => saveKey(e.target.value)} />
      </label>

      <h2>New school</h2>
      <form className="row" onSubmit={submit}>
        <input placeholder="slug (e.g. greenfield)" value={slug} onChange={(e) => setSlug(e.target.value)} />
        <input placeholder="School name" value={name} onChange={(e) => setName(e.target.value)} />
        <select value={plan} onChange={(e) => setPlan(e.target.value)}>
          <option value="BASIC">BASIC</option>
          <option value="PRO">PRO</option>
          <option value="ENTERPRISE">ENTERPRISE</option>
        </select>
        <button type="submit" disabled={create.isPending || !key}>
          {create.isPending ? 'Creating...' : 'Create'}
        </button>
      </form>
      {create.isError && <p className="error">{getErrorMessage(create.error)}</p>}

      <h2>Schools</h2>
      {tenants.isError && <p className="error">{getErrorMessage(tenants.error)}</p>}
      <table>
        <thead>
          <tr>
            <th>Slug</th>
            <th>Name</th>
            <th>Plan</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(tenants.data ?? []).map((t) => (
            <tr key={t.slug}>
              <td>{t.slug}</td>
              <td>{t.name}</td>
              <td>{t.plan}</td>
              <td>{t.status}</td>
              <td>
                <button
                  onClick={() =>
                    toggle.mutate({ slug: t.slug, status: t.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' })
                  }
                >
                  {t.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
