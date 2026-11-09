import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, getErrorMessage, setAuthTokens } from '../../shared/api/client';
import { useTenantConfig } from '../tenant/hooks';
import { useI18n } from '../../shared/i18n';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export function LoginPage() {
  const { data: tenant } = useTenantConfig();
  const { t, locale, setLocale } = useI18n();
  const navigate = useNavigate();

  const defaultEmail = tenant?.slug ? `admin@${tenant.slug}.school` : 'admin@policeline.school';
  const [email, setEmail] = useState(defaultEmail);
  const [password, setPassword] = useState('Admin@12345');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await api.post<TokenResponse>('/api/v1/auth/login', { email, password });
      setAuthTokens(res.data.accessToken, res.data.refreshToken);
      navigate('/');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-wrapper">
      <form className="center-card" onSubmit={handleSubmit}>
        <div className="page-header">
          <span className="badge active">{tenant?.name ?? 'School ERP'}</span>
          <div className="lang-switch">
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
        </div>

        <div>
          <h2 style={{ marginBottom: 4 }}>{t('login.title')}</h2>
          <p className="muted" style={{ margin: 0 }}>
            {t('login.subtitle')}
          </p>
        </div>

        {error && <div className="error-text">{error}</div>}

        <label>
          {t('login.email')}
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder={defaultEmail}
          />
        </label>

        <label>
          {t('login.password')}
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        <button type="submit" disabled={loading}>
          {loading ? t('common.loading') : t('login.submit')}
        </button>

        <div className="page-header" style={{ paddingTop: 8, borderTop: '1px solid var(--color-border)' }}>
          <Link to="/site" className="muted">
            &larr; {t('login.backToWebsite')}
          </Link>
          <Link to="/platform" className="muted">
            Platform Console
          </Link>
        </div>
      </form>
    </div>
  );
}
