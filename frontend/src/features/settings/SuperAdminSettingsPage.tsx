import { useState, useEffect, FormEvent, ChangeEvent, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api, getErrorMessage } from '../../shared/api/client';
import { useI18n } from '../../shared/i18n';
import { SecurityAndCacheSettingsPanel } from './SecurityAndCacheSettingsPanel';
import { ConfirmDeleteModal, DeleteTarget } from '../../shared/components/ConfirmDeleteModal';

interface SettingItem {
  key: string;
  value: string;
}

interface RoleGrantView {
  userRoleId: number;
  roleCode: string;
  scopeType: string;
  scopeId: number;
}

interface UserView {
  id: number;
  email: string;
  fullName: string;
  status: string;
  roles: RoleGrantView[];
}

interface PagedUsersResponse {
  items: UserView[];
  totalElements: number;
}

interface RoleView {
  id: number;
  code: string;
  name: string;
  systemRole: boolean;
  permissions: string[];
}

type SettingsTab = 'branding' | 'policies' | 'users' | 'security';

export function SuperAdminSettingsPage() {
  const { t, locale, setLocale } = useI18n();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [activeTab, setActiveTab] = useState<SettingsTab>('branding');
  const [settingsMap, setSettingsMap] = useState<Record<string, string>>({});
  const [users, setUsers] = useState<UserView[]>([]);
  const [roles, setRoles] = useState<RoleView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  // Custom setting key-value form
  const [customKey, setCustomKey] = useState('');
  const [customVal, setCustomVal] = useState('');

  // Create User Form
  const [newEmail, setNewEmail] = useState('');
  const [newFullName, setNewFullName] = useState('');
  const [newPassword, setNewPassword] = useState('Admin@12345');
  const [newRoleCode, setNewRoleCode] = useState('SUPER_ADMIN');
  const [newScopeType, setNewScopeType] = useState<'ALL' | 'DEPARTMENT' | 'CLASS' | 'SECTION' | 'OWN'>('ALL');
  const [newScopeId, setNewScopeId] = useState<number>(0);

  // Password Change Form
  const [currentPassword, setCurrentPassword] = useState('');
  const [nextPassword, setNextPassword] = useState('');

  const loadAll = async () => {
    try {
      setError(null);
      const [settingsRes, usersRes, rolesRes] = await Promise.all([
        api.get<SettingItem[]>('/api/v1/settings'),
        api.get<PagedUsersResponse>('/api/v1/users?size=100'),
        api.get<RoleView[]>('/api/v1/roles'),
      ]);
      const map: Record<string, string> = {};
      for (const s of settingsRes.data) {
        map[s.key] = s.value;
      }
      if (map['academic.credit_hours_enabled'] === undefined) {
        map['academic.credit_hours_enabled'] = 'false';
      }
      setSettingsMap(map);
      setUsers(usersRes.data.items ?? []);
      setRoles(rolesRes.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  useEffect(() => {
    void loadAll();
  }, []);

  const updateSettingField = (key: string, value: string) => {
    setSettingsMap((prev) => ({ ...prev, [key]: value }));
  };

  const saveSettingKey = async (key: string, value: string) => {
    try {
      setError(null);
      setMessage(null);
      await api.put(`/api/v1/settings/${key}`, { value });
      if (key === 'locale.default' && (value === 'en' || value === 'bn')) {
        setLocale(value);
      }
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
      setMessage(`Saved setting "${key}".`);
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const deleteSettingKey = async (key: string) => {
    try {
      setError(null);
      setMessage(null);
      await api.delete(`/api/v1/settings/${key}`);
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
      setMessage(`Deleted setting "${key}".`);
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleExportSettingsJson = () => {
    const ordered: Record<string, string> = {};
    for (const k of Object.keys(settingsMap).sort()) {
      ordered[k] = settingsMap[k];
    }
    const jsonString = JSON.stringify(ordered, null, 2);
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `school-erp-settings-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    setMessage('Exported all ERP settings as JSON.');
  };

  const handleImportSettingsJsonFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      setError(null);
      setMessage(null);
      const rawText = await file.text();
      const parsed: unknown = JSON.parse(rawText);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('Invalid JSON format: expected a JSON object of key/value pairs.');
      }
      const payload: Record<string, string> = {};
      for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
        payload[k] = String(v ?? '');
      }
      await api.post('/api/v1/settings/import', payload);
      if (payload['locale.default'] === 'bn' || payload['locale.default'] === 'en') {
        setLocale(payload['locale.default']);
      }
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
      setMessage(`Successfully imported ${Object.keys(payload).length} setting(s) from JSON.`);
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleSaveBrandingBatch = async (e: FormEvent) => {
    e.preventDefault();
    const keysToSave = [
      'school.display_name',
      'brand.primary_color',
      'brand.accent_color',
      'brand.logo_url',
      'locale.default',
      'academic.credit_hours_enabled',
      'website.hero_title',
      'website.hero_subtitle',
      'website.about_text',
      'website.principal_name',
      'website.principal_message',
      'website.contact_phone',
      'website.contact_email',
      'website.contact_address',
      'website.established_year',
      'website.eiin_number',
    ];
    try {
      setError(null);
      setMessage(null);
      const payload: Record<string, string> = {};
      for (const key of keysToSave) {
        if (settingsMap[key] !== undefined) {
          payload[key] = settingsMap[key];
        }
      }
      await api.post('/api/v1/settings/import', payload);
      if (settingsMap['locale.default'] === 'bn' || settingsMap['locale.default'] === 'en') {
        setLocale(settingsMap['locale.default']);
      }
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
      setMessage('Branding, University Credit Hours, and Public School Website settings saved!');
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleAddCustomSetting = async (e: FormEvent) => {
    e.preventDefault();
    if (!customKey.trim()) return;
    await saveSettingKey(customKey.trim(), customVal);
    setCustomKey('');
    setCustomVal('');
  };

  const handleCreateUser = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setError(null);
      setMessage(null);
      await api.post('/api/v1/users', {
        email: newEmail,
        fullName: newFullName,
        password: newPassword,
        roleCode: newRoleCode,
        scopeType: newScopeType,
        scopeId: newScopeId,
      });
      setNewEmail('');
      setNewFullName('');
      setMessage('User created and role assigned.');
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleGrantRole = async (userId: number, roleCode: string) => {
    try {
      setError(null);
      await api.post(`/api/v1/users/${userId}/roles`, {
        roleCode,
        scopeType: 'ALL',
        scopeId: 0,
      });
      setMessage(`Granted ${roleCode} (ALL privileges) to user #${userId}.`);
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleRevokeRole = async (userId: number, userRoleId: number) => {
    try {
      setError(null);
      await api.delete(`/api/v1/users/${userId}/roles/${userRoleId}`);
      await loadAll();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleChangePassword = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setError(null);
      setMessage(null);
      await api.post('/api/v1/auth/change-password', {
        currentPassword,
        newPassword: nextPassword,
      });
      setCurrentPassword('');
      setNextPassword('');
      setMessage('Password updated.');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <section className="stack">
      <ConfirmDeleteModal target={deleteTarget} onClose={() => setDeleteTarget(null)} />

      <input
        ref={fileInputRef}
        type="file"
        accept=".json,application/json"
        style={{ display: 'none' }}
        onChange={(e) => void handleImportSettingsJsonFile(e)}
      />

      <div className="page-header">
        <div>
          <h2>{t('settings.title')}</h2>
          <p className="muted">{t('settings.subtitle')}</p>
        </div>
        <div className="action-row">
          <div className="lang-switch">
            <button
              type="button"
              className={locale === 'en' ? 'active' : ''}
              onClick={() => setLocale('en')}
            >
              English (EN)
            </button>
            <button
              type="button"
              className={locale === 'bn' ? 'active' : ''}
              onClick={() => setLocale('bn')}
            >
              বাংলা (BN)
            </button>
          </div>
          <button type="button" className="secondary" onClick={handleExportSettingsJson}>
            Export Settings (JSON)
          </button>
          <button type="button" className="secondary" onClick={() => fileInputRef.current?.click()}>
            Import Settings (JSON)
          </button>
          <Link to="/site" className="btn secondary">
            {t('dashboard.openWebsite')} &rarr;
          </Link>
        </div>
      </div>

      {error && <div className="error-text">{error}</div>}
      {message && <div className="success">{message}</div>}

      <div className="tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'branding' ? 'active' : ''}`}
          onClick={() => setActiveTab('branding')}
        >
          {t('settings.tab.branding')}
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'policies' ? 'active' : ''}`}
          onClick={() => setActiveTab('policies')}
        >
          {t('settings.tab.policies')} ({Object.keys(settingsMap).length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          {t('settings.tab.users')} ({users.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'security' ? 'active' : ''}`}
          onClick={() => setActiveTab('security')}
        >
          {t('settings.tab.security')}
        </button>
      </div>

      {activeTab === 'branding' && (
        <form className="card stack" onSubmit={handleSaveBrandingBatch}>
          <h3>Institution Identity, Academic Mode, Theme Colors & Language</h3>
          <div className="form-grid">
            <div>
              <label>School / University Display Name (`school.display_name`)</label>
              <input
                value={settingsMap['school.display_name'] ?? ''}
                onChange={(e) => updateSettingField('school.display_name', e.target.value)}
              />
            </div>
            <div>
              <label>University Credit Hours (`academic.credit_hours_enabled`)</label>
              <select
                value={settingsMap['academic.credit_hours_enabled'] ?? 'false'}
                onChange={(e) => updateSettingField('academic.credit_hours_enabled', e.target.value)}
              >
                <option value="false">Disabled — Regular School Mode (K-12)</option>
                <option value="true">Enabled — University / College Credit Hours Mode</option>
              </select>
            </div>
            <div>
              <label>Primary Brand Color (`brand.primary_color`)</label>
              <input
                type="color"
                value={settingsMap['brand.primary_color'] ?? '#2563eb'}
                onChange={(e) => updateSettingField('brand.primary_color', e.target.value)}
              />
            </div>
            <div>
              <label>Accent Color (`brand.accent_color`)</label>
              <input
                type="color"
                value={settingsMap['brand.accent_color'] ?? '#f59e0b'}
                onChange={(e) => updateSettingField('brand.accent_color', e.target.value)}
              />
            </div>
            <div>
              <label>Default Language (`locale.default`)</label>
              <select
                value={settingsMap['locale.default'] ?? 'en'}
                onChange={(e) => updateSettingField('locale.default', e.target.value)}
              >
                <option value="en">English (en)</option>
                <option value="bn">বাংলা / Bangla (bn)</option>
              </select>
            </div>
            <div>
              <label>EIIN Number (`website.eiin_number`)</label>
              <input
                value={settingsMap['website.eiin_number'] ?? '120789'}
                onChange={(e) => updateSettingField('website.eiin_number', e.target.value)}
              />
            </div>
            <div>
              <label>Established Year (`website.established_year`)</label>
              <input
                value={settingsMap['website.established_year'] ?? '1992'}
                onChange={(e) => updateSettingField('website.established_year', e.target.value)}
              />
            </div>
            <div>
              <label>Contact Phone (`website.contact_phone`)</label>
              <input
                value={settingsMap['website.contact_phone'] ?? '+880 1700-000000'}
                onChange={(e) => updateSettingField('website.contact_phone', e.target.value)}
              />
            </div>
            <div>
              <label>Contact Email (`website.contact_email`)</label>
              <input
                value={settingsMap['website.contact_email'] ?? 'info@school.edu.bd'}
                onChange={(e) => updateSettingField('website.contact_email', e.target.value)}
              />
            </div>
          </div>

          <h3>Public School Website Content (`/site`)</h3>
          <div className="form-grid">
            <div>
              <label>Hero Banner Title (`website.hero_title`)</label>
              <input
                value={settingsMap['website.hero_title'] ?? ''}
                onChange={(e) => updateSettingField('website.hero_title', e.target.value)}
              />
            </div>
            <div>
              <label>Principal Full Name (`website.principal_name`)</label>
              <input
                value={settingsMap['website.principal_name'] ?? ''}
                onChange={(e) => updateSettingField('website.principal_name', e.target.value)}
              />
            </div>
            <div>
              <label>Campus Address (`website.contact_address`)</label>
              <input
                value={settingsMap['website.contact_address'] ?? ''}
                onChange={(e) => updateSettingField('website.contact_address', e.target.value)}
              />
            </div>
          </div>

          <div>
            <label>Hero Subtitle (`website.hero_subtitle`)</label>
            <textarea
              rows={2}
              value={settingsMap['website.hero_subtitle'] ?? ''}
              onChange={(e) => updateSettingField('website.hero_subtitle', e.target.value)}
            />
          </div>

          <div>
            <label>About the Institution (`website.about_text`)</label>
            <textarea
              rows={3}
              value={settingsMap['website.about_text'] ?? ''}
              onChange={(e) => updateSettingField('website.about_text', e.target.value)}
            />
          </div>

          <div>
            <label>Principal&apos;s Message (`website.principal_message`)</label>
            <textarea
              rows={3}
              value={settingsMap['website.principal_message'] ?? ''}
              onChange={(e) => updateSettingField('website.principal_message', e.target.value)}
            />
          </div>

          <div className="action-row">
            <button type="submit">{t('common.save')}</button>
            <Link to="/site" className="btn secondary">
              Preview Public School Website &rarr;
            </Link>
          </div>
        </form>
      )}

      {activeTab === 'policies' && (
        <div className="stack">
          <div className="card page-header">
            <div>
              <h3>JSON Settings Backup & Restore (`All ERP Settings`)</h3>
              <p className="muted" style={{ margin: 0 }}>
                Export all ERP configuration keys as a `.json` file or import a `.json` file to restore/clone settings across tenants.
              </p>
            </div>
            <div className="action-row">
              <button type="button" onClick={handleExportSettingsJson}>
                Export All Settings (JSON)
              </button>
              <button type="button" className="secondary" onClick={() => fileInputRef.current?.click()}>
                Import Settings from JSON
              </button>
            </div>
          </div>

          <form className="card form-grid" onSubmit={handleAddCustomSetting}>
            <div>
              <label>Setting Key (e.g. `academic.credit_hours_enabled` or `fees.currency`)</label>
              <input
                value={customKey}
                onChange={(e) => setCustomKey(e.target.value)}
                placeholder="module.setting_key"
                required
              />
            </div>
            <div>
              <label>Setting Value</label>
              <input
                value={customVal}
                onChange={(e) => setCustomVal(e.target.value)}
                placeholder="Value"
                required
              />
            </div>
            <button type="submit">Add / Update Setting</button>
          </form>

          <div className="card">
            <h3>All Active School Settings (`app_settings`)</h3>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Setting Key</th>
                  <th>Current Value</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(settingsMap)
                  .sort(([a], [b]) => a.localeCompare(b))
                  .map(([key, val]) => (
                    <tr key={key}>
                      <td>
                        <code>{key}</code>
                      </td>
                      <td>
                        {key === 'academic.credit_hours_enabled' ? (
                          <select
                            value={val}
                            onChange={(e) => updateSettingField(key, e.target.value)}
                          >
                            <option value="false">false (School Mode — Hide Credit Hours)</option>
                            <option value="true">true (University Mode — Show Credit Hours)</option>
                          </select>
                        ) : (
                          <input
                            value={val}
                            onChange={(e) => updateSettingField(key, e.target.value)}
                          />
                        )}
                      </td>
                      <td className="action-row">
                        <button
                          type="button"
                          onClick={() => void saveSettingKey(key, settingsMap[key] ?? '')}
                        >
                          {t('common.save')}
                        </button>
                        <button
                          type="button"
                          className="danger"
                          onClick={() =>
                            setDeleteTarget({
                              title: `Delete Setting Key: ${key}`,
                              onConfirm: async () => {
                                await deleteSettingKey(key);
                              },
                            })
                          }
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'users' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleCreateUser}>
            <div>
              <label>Email Address</label>
              <input
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder="user@school.edu"
                required
              />
            </div>
            <div>
              <label>Full Name</label>
              <input
                value={newFullName}
                onChange={(e) => setNewFullName(e.target.value)}
                placeholder="Full Name"
                required
              />
            </div>
            <div>
              <label>Initial Password</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
            </div>
            <div>
              <label>Initial Role</label>
              <select value={newRoleCode} onChange={(e) => setNewRoleCode(e.target.value)}>
                {roles.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.code} ({r.name})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Scope Type</label>
              <select
                value={newScopeType}
                onChange={(e) =>
                  setNewScopeType(
                    e.target.value as 'ALL' | 'DEPARTMENT' | 'CLASS' | 'SECTION' | 'OWN'
                  )
                }
              >
                <option value="ALL">ALL (Global School Scope)</option>
                <option value="DEPARTMENT">DEPARTMENT</option>
                <option value="CLASS">CLASS</option>
                <option value="SECTION">SECTION</option>
                <option value="OWN">OWN</option>
              </select>
            </div>
            <div>
              <label>Scope ID (0 for ALL)</label>
              <input
                type="number"
                value={newScopeId}
                onChange={(e) => setNewScopeId(Number(e.target.value))}
                min={0}
              />
            </div>
            <button type="submit">Create User Account</button>
          </form>

          <div className="card">
            <h3>School Users & SuperAdmin Privilege Grants</h3>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Full Name</th>
                  <th>Email</th>
                  <th>Status</th>
                  <th>Assigned Roles & Scopes</th>
                  <th>Quick Privilege Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.fullName}</strong>
                    </td>
                    <td>{u.email}</td>
                    <td>
                      <span className="badge active">{u.status}</span>
                    </td>
                    <td>
                      <div className="action-row">
                        {u.roles.map((rg) => (
                          <span key={rg.userRoleId} className="badge">
                            {rg.roleCode} ({rg.scopeType}
                            {rg.scopeId > 0 ? `#${rg.scopeId}` : ''}){' '}
                            <button
                              type="button"
                              className="danger"
                              style={{ padding: '1px 6px', marginLeft: 6, fontSize: '0.7rem' }}
                              onClick={() =>
                                setDeleteTarget({
                                  title: `Revoke Role ${rg.roleCode} from ${u.fullName}`,
                                  onConfirm: async () => {
                                    await handleRevokeRole(u.id, rg.userRoleId);
                                  },
                                })
                              }
                            >
                              &times;
                            </button>
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="action-row">
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => void handleGrantRole(u.id, 'SUPER_ADMIN')}
                      >
                        + Grant SUPER_ADMIN
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => void handleGrantRole(u.id, 'SCHOOL_ADMIN')}
                      >
                        + Grant SCHOOL_ADMIN
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="card">
            <h3>System Roles & Granted Permissions Matrix</h3>
            <div className="grid-cards">
              {roles.map((r) => (
                <div key={r.id} className="card stack" style={{ gap: 8 }}>
                  <div className="page-header">
                    <strong>{r.name}</strong>
                    <span className="badge active">{r.code}</span>
                  </div>
                  <div className="muted" style={{ fontSize: '0.8rem' }}>
                    {r.permissions.length} permission(s) granted:{' '}
                    {r.permissions.slice(0, 12).join(', ')}
                    {r.permissions.length > 12 ? ` +${r.permissions.length - 12} more` : ''}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {activeTab === 'security' && (
        <div className="stack">
          <SecurityAndCacheSettingsPanel />

          <form className="card form-grid" onSubmit={handleChangePassword}>
            <div>
              <label>Current Password</label>
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                required
              />
            </div>
            <div>
              <label>New Password (min 8 chars)</label>
              <input
                type="password"
                value={nextPassword}
                onChange={(e) => setNextPassword(e.target.value)}
                minLength={8}
                required
              />
            </div>
            <button type="submit">Change My Password</button>
          </form>
        </div>
      )}
    </section>
  );
}
