import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, getErrorMessage, setAuthTokens } from '../../shared/api/client';
import { useI18n } from '../../shared/i18n';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export function LoginPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@greenwood.school');
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
    <form className="center-card" onSubmit={handleSubmit}>
      <h2>{t('login.title')}</h2>
      {error && <p className="error">{error}</p>}
      <label>
        {t('login.email')}
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="admin@greenwood.school"
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
    </form>
  );
}
