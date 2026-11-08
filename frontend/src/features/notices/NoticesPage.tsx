import { useState, useEffect, FormEvent } from 'react';
import { useI18n } from '../../shared/i18n';
import { api, getErrorMessage } from '../../shared/api/client';

interface PageResponse<T> {
  content: T[];
}

function unwrapList<T>(data: PageResponse<T> | T[] | null | undefined): T[] {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.content)) return data.content;
  return [];
}

interface Notice {
  id: number;
  title: string;
  bodyMarkdown: string;
  smsBody: string | null;
  category: 'GENERAL' | 'ACADEMIC' | 'EXAM' | 'HOLIDAY' | 'FEE' | 'EVENT' | 'EMERGENCY';
  priority: 'NORMAL' | 'HIGH' | 'URGENT';
  pinned: boolean;
  sendInApp: boolean;
  sendEmail: boolean;
  sendSms: boolean;
  status: 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'ARCHIVED';
  publishedAt: string | null;
}

type TabKey = 'feed' | 'manage';

export function NoticesPage() {
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState<TabKey>('feed');
  const [feed, setFeed] = useState<Notice[]>([]);
  const [allNotices, setAllNotices] = useState<Notice[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  // Compose Notice Form
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [category, setCategory] = useState<Notice['category']>('GENERAL');
  const [priority, setPriority] = useState<Notice['priority']>('NORMAL');
  const [isPinned, setIsPinned] = useState(false);
  const [sendEmail, setSendEmail] = useState(false);
  const [sendSms, setSendSms] = useState(false);
  const [audienceType, setAudienceType] = useState<'WHOLE_SCHOOL' | 'CLASS' | 'SECTION' | 'ROLE'>('WHOLE_SCHOOL');

  const loadData = async () => {
    try {
      setError(null);
      const [feedRes, allRes] = await Promise.all([
        api.get<PageResponse<Notice> | Notice[]>('/api/v1/notices/feed?size=100'),
        api.get<PageResponse<Notice> | Notice[]>('/api/v1/notices?size=100')
      ]);
      setFeed(unwrapList(feedRes.data));
      setAllNotices(unwrapList(allRes.data));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  const handleCreateNotice = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setError(null);
      setMessage(null);
      await api.post('/api/v1/notices', {
        title,
        bodyMarkdown: body,
        smsBody: body.slice(0, 160),
        category,
        priority,
        pinned: isPinned,
        sendInApp: true,
        sendEmail,
        sendSms,
        audiences: [
          {
            targetType: audienceType,
            targetRoleCode: audienceType === 'ROLE' ? 'TEACHER' : null,
            targetEntityId: null
          }
        ]
      });
      setTitle('');
      setBody('');
      setMessage('Notice draft saved.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handlePublish = async (id: number) => {
    try {
      await api.post(`/api/v1/notices/${id}/publish`);
      setMessage('Notice published and dispatched.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleArchive = async (id: number) => {
    try {
      await api.post(`/api/v1/notices/${id}/archive`);
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleMarkRead = async (id: number) => {
    try {
      await api.post(`/api/v1/notices/${id}/read`);
      setMessage(`Marked notice #${id} as read.`);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <section className="stack">
      <div>
        <h2>{t('notices.title')}</h2>
        <p className="muted">{t('notices.subtitle')}</p>
      </div>

      {error && <p className="error-text">{error}</p>}
      {message && <p className="badge active">{message}</p>}

      <div className="tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'feed' ? 'active' : ''}`}
          onClick={() => setActiveTab('feed')}
        >
          {t('notices.feed')} ({feed.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'manage' ? 'active' : ''}`}
          onClick={() => setActiveTab('manage')}
        >
          {t('notices.compose')} ({allNotices.length})
        </button>
      </div>

      {activeTab === 'feed' && (
        <div className="stack">
          {feed.map((n) => (
            <article key={n.id} className="card stack">
              <div className="page-header">
                <div>
                  <h3>
                    {n.pinned ? '[PINNED] ' : ''}
                    {n.title}
                  </h3>
                  <span className="muted">
                    {n.category} &bull; Priority: {n.priority}
                  </span>
                </div>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => handleMarkRead(n.id)}
                >
                  Acknowledge / Read
                </button>
              </div>
              <p>{n.bodyMarkdown}</p>
            </article>
          ))}
          {feed.length === 0 && (
            <div className="card muted">{t('common.noData')}</div>
          )}
        </div>
      )}

      {activeTab === 'manage' && (
        <div className="stack">
          <form className="card stack" onSubmit={handleCreateNotice}>
            <div className="form-grid">
              <div>
                <label>Title</label>
                <input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>
              <div>
                <label>Category</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value as Notice['category'])}
                >
                  <option value="GENERAL">GENERAL</option>
                  <option value="ACADEMIC">ACADEMIC</option>
                  <option value="EXAM">EXAM</option>
                  <option value="HOLIDAY">HOLIDAY</option>
                  <option value="FEE">FEE</option>
                  <option value="EVENT">EVENT</option>
                  <option value="EMERGENCY">EMERGENCY</option>
                </select>
              </div>
              <div>
                <label>Priority</label>
                <select
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as Notice['priority'])}
                >
                  <option value="NORMAL">NORMAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="URGENT">URGENT</option>
                </select>
              </div>
              <div>
                <label>Audience</label>
                <select
                  value={audienceType}
                  onChange={(e) =>
                    setAudienceType(
                      e.target.value as 'WHOLE_SCHOOL' | 'CLASS' | 'SECTION' | 'ROLE'
                    )
                  }
                >
                  <option value="WHOLE_SCHOOL">WHOLE_SCHOOL</option>
                  <option value="CLASS">CLASS</option>
                  <option value="SECTION">SECTION</option>
                  <option value="ROLE">ROLE (Teachers)</option>
                </select>
              </div>
            </div>
            <div>
              <label>Message Body</label>
              <textarea
                rows={3}
                value={body}
                onChange={(e) => setBody(e.target.value)}
                required
              />
            </div>
            <div className="action-row">
              <label>
                <input
                  type="checkbox"
                  checked={isPinned}
                  onChange={(e) => setIsPinned(e.target.checked)}
                />{' '}
                Pin to Top
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={sendEmail}
                  onChange={(e) => setSendEmail(e.target.checked)}
                />{' '}
                Dispatch Email
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={sendSms}
                  onChange={(e) => setSendSms(e.target.checked)}
                />{' '}
                Dispatch SMS
              </label>
              <button type="submit">Save Draft Notice</button>
            </div>
          </form>

          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Category</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {allNotices.map((n) => (
                  <tr key={n.id}>
                    <td>{n.title}</td>
                    <td>{n.category}</td>
                    <td>{n.priority}</td>
                    <td>
                      <span className="badge active">{n.status}</span>
                    </td>
                    <td className="action-row">
                      {n.status !== 'PUBLISHED' && (
                        <button type="button" onClick={() => handlePublish(n.id)}>
                          {t('notices.publish')}
                        </button>
                      )}
                      {n.status !== 'ARCHIVED' && (
                        <button
                          type="button"
                          className="danger"
                          onClick={() => handleArchive(n.id)}
                        >
                          Archive
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {allNotices.length === 0 && (
                  <tr>
                    <td colSpan={5} className="muted">
                      {t('common.noData')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
