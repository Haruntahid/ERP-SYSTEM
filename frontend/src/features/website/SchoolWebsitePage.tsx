import { useState, useEffect, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, getAccessToken, getErrorMessage } from '../../shared/api/client';
import { useI18n } from '../../shared/i18n';

interface PublicTeacher {
  id: number;
  employeeCode: string;
  fullName: string;
  staffType: string;
  designationTitle: string;
  qualification: string | null;
  specialization: string | null;
  email: string | null;
}

interface AcademicSession {
  id: number;
  name: string;
  code: string;
  current: boolean;
}

interface AcademicClass {
  id: number;
  name: string;
  code: string;
  numericLevel: number;
}

interface Section {
  id: number;
  classId: number;
  name: string;
}

interface Subject {
  id: number;
  name: string;
  code: string;
}

interface Notice {
  id: number;
  title: string;
  bodyMarkdown: string;
  category: string;
  priority: string;
  pinned: boolean;
  publishedAt: string | null;
}

interface PublicSchoolSiteData {
  slug: string;
  schoolName: string;
  plan: string;
  primaryColor: string;
  accentColor: string;
  logoUrl: string;
  defaultLocale: string;
  heroTitle: string;
  heroSubtitle: string;
  aboutText: string;
  principalName: string;
  principalMessage: string;
  contactPhone: string;
  contactEmail: string;
  contactAddress: string;
  establishedYear: string;
  eiinNumber: string;
  stats: {
    totalStudents: number;
    totalTeachers: number;
    totalClasses: number;
    totalCampuses: number;
  };
  sessions: AcademicSession[];
  classes: AcademicClass[];
  sections: Section[];
  subjects: Subject[];
  teachers: PublicTeacher[];
  notices: Notice[];
}

export function SchoolWebsitePage() {
  const { t, locale, setLocale } = useI18n();
  const [site, setSite] = useState<PublicSchoolSiteData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Online Admission Form State
  const [sessionId, setSessionId] = useState<number>(0);
  const [classId, setClassId] = useState<number>(0);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [dob, setDob] = useState('2014-01-15');
  const [gender, setGender] = useState('MALE');
  const [guardianName, setGuardianName] = useState('');
  const [guardianPhone, setGuardianPhone] = useState('');
  const [guardianEmail, setGuardianEmail] = useState('');
  const [guardianRelation, setGuardianRelation] = useState('FATHER');
  const [prevSchool, setPrevSchool] = useState('');
  const [applySuccess, setApplySuccess] = useState<string | null>(null);
  const [applyError, setApplyError] = useState<string | null>(null);

  const isAuthenticated = Boolean(getAccessToken());

  useEffect(() => {
    const loadSite = async () => {
      try {
        setLoading(true);
        const res = await api.get<PublicSchoolSiteData>('/api/v1/public/school-site');
        setSite(res.data);
        if (res.data.sessions[0]) setSessionId(res.data.sessions[0].id);
        if (res.data.classes[0]) setClassId(res.data.classes[0].id);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };
    void loadSite();
  }, []);

  const handleApply = async (e: FormEvent) => {
    e.preventDefault();
    if (!sessionId || !classId) {
      setApplyError('Admissions are not open yet for an active session/class.');
      return;
    }
    try {
      setApplyError(null);
      setApplySuccess(null);
      const res = await api.post<{ applicationNo: string }>('/api/v1/public/admissions/apply', {
        academicSessionId: sessionId,
        targetClassId: classId,
        targetSectionId: null,
        applicantFirstName: firstName,
        applicantLastName: lastName,
        dateOfBirth: dob,
        gender,
        guardianName,
        guardianPhone,
        guardianEmail: guardianEmail || null,
        guardianRelationship: guardianRelation,
        previousSchoolName: prevSchool || null
      });
      setApplySuccess(
        `Application submitted! Tracking Number: ${res.data.applicationNo}. Our admission office will contact you.`
      );
      setFirstName('');
      setLastName('');
      setGuardianName('');
      setGuardianPhone('');
      setGuardianEmail('');
      setPrevSchool('');
    } catch (err) {
      setApplyError(getErrorMessage(err));
    }
  };

  if (loading) {
    return (
      <div className="auth-wrapper">
        <div className="card">{t('common.loading')}</div>
      </div>
    );
  }

  if (error || !site) {
    return (
      <div className="auth-wrapper">
        <div className="card stack">
          <h3>Unable to load school website</h3>
          <p className="error-text">{error}</p>
          <Link to="/login" className="btn">
            {t('website.nav.erpLogin')}
          </Link>
        </div>
      </div>
    );
  }

  const initials = site.schoolName
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0] ?? '')
    .join('')
    .toUpperCase();

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg)' }}>
      {/* Top Info Bar */}
      <div
        style={{
          background: '#0f172a',
          color: '#cbd5e1',
          fontSize: '0.82rem',
          padding: '8px 24px'
        }}
      >
        <div
          style={{
            maxWidth: 1280,
            margin: '0 auto',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12
          }}
        >
          <div>
            EIIN: <strong>{site.eiinNumber}</strong> &bull; Est. <strong>{site.establishedYear}</strong> &bull;{' '}
            {site.contactAddress}
          </div>
          <div>
            Phone: <strong>{site.contactPhone}</strong> &bull; Email: <strong>{site.contactEmail}</strong>
          </div>
        </div>
      </div>

      {/* Sticky Navigation Header */}
      <header className="site-header">
        <div className="site-nav-inner">
          <div className="action-row">
            <div className="brand-avatar">{initials || 'S'}</div>
            <div>
              <div style={{ fontWeight: 800, fontSize: '1.15rem', color: 'var(--color-text)' }}>
                {site.schoolName}
              </div>
              <div className="muted" style={{ fontSize: '0.78rem' }}>
                Official Institution Portal ({site.slug})
              </div>
            </div>
          </div>

          <nav className="action-row" style={{ gap: 18 }}>
            <a href="#about" style={{ fontWeight: 600, color: 'var(--color-text)' }}>
              {t('website.nav.about')}
            </a>
            <a href="#academics" style={{ fontWeight: 600, color: 'var(--color-text)' }}>
              {t('website.nav.academics')}
            </a>
            <a href="#teachers" style={{ fontWeight: 600, color: 'var(--color-text)' }}>
              {t('website.nav.teachers')}
            </a>
            <a href="#notices" style={{ fontWeight: 600, color: 'var(--color-text)' }}>
              {t('website.nav.notices')}
            </a>
            <a href="#admission" style={{ fontWeight: 600, color: 'var(--color-text)' }}>
              {t('website.nav.admission')}
            </a>
          </nav>

          <div className="action-row">
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

            {isAuthenticated ? (
              <Link to="/" className="btn">
                {t('website.nav.erpDashboard')}
              </Link>
            ) : (
              <Link to="/login" className="btn">
                {t('website.nav.erpLogin')}
              </Link>
            )}
          </div>
        </div>
      </header>

      {/* Hero Banner */}
      <section className="site-hero">
        <div className="site-hero-inner">
          <div className="stack">
            <span
              className="badge"
              style={{
                width: 'fit-content',
                background: 'rgba(255,255,255,0.16)',
                color: '#fff',
                padding: '6px 14px'
              }}
            >
              {site.schoolName} &bull; EIIN {site.eiinNumber}
            </span>
            <h1>{site.heroTitle}</h1>
            <p style={{ fontSize: '1.1rem', color: 'rgba(255,255,255,0.88)', maxWidth: 640 }}>
              {site.heroSubtitle}
            </p>
            <div className="action-row" style={{ marginTop: 8 }}>
              <a
                href="#admission"
                className="btn"
                style={{ background: 'var(--color-accent)', color: '#0f172a', fontWeight: 700 }}
              >
                {t('website.hero.applyBtn')}
              </a>
              <Link
                to={isAuthenticated ? '/' : '/login'}
                className="btn secondary"
                style={{ background: 'rgba(255,255,255,0.12)', color: '#fff', borderColor: 'rgba(255,255,255,0.3)' }}
              >
                {isAuthenticated ? t('website.nav.erpDashboard') : t('website.hero.loginBtn')}
              </Link>
            </div>
          </div>

          <div className="kpi-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <div className="kpi-card">
              <div className="kpi-value">{site.stats.totalStudents}</div>
              <div className="kpi-label">{t('website.stats.students')}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-value">{site.stats.totalTeachers}</div>
              <div className="kpi-label">{t('website.stats.teachers')}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-value">{site.stats.totalClasses}</div>
              <div className="kpi-label">{t('website.stats.classes')}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-value">{site.stats.totalCampuses}</div>
              <div className="kpi-label">{t('website.stats.campuses')}</div>
            </div>
          </div>
        </div>
      </section>

      {/* About & Principal's Message */}
      <section id="about" className="site-section">
        <div className="grid-cards" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))' }}>
          <div className="card stack">
            <span className="badge active" style={{ width: 'fit-content' }}>
              Est. {site.establishedYear}
            </span>
            <h2>{t('website.section.about')}</h2>
            <p style={{ color: '#334155', lineHeight: 1.7 }}>{site.aboutText}</p>
            <div className="muted">
              Campus Address: <strong>{site.contactAddress}</strong>
            </div>
          </div>

          <div className="card stack" style={{ borderLeft: '4px solid var(--color-primary)' }}>
            <span className="badge warning" style={{ width: 'fit-content' }}>
              Leadership
            </span>
            <h2>{t('website.section.principal')}</h2>
            <p style={{ fontStyle: 'italic', color: '#334155', lineHeight: 1.7 }}>
              &ldquo;{site.principalMessage}&rdquo;
            </p>
            <div>
              <strong>{site.principalName}</strong>
              <div className="muted">Principal, {site.schoolName}</div>
            </div>
          </div>
        </div>
      </section>

      {/* Academic Programs & Classes */}
      <section id="academics" className="site-section" style={{ paddingTop: 0 }}>
        <div className="stack">
          <div>
            <h2>{t('website.section.academics')}</h2>
            <p className="muted">
              Active Sessions:{' '}
              {site.sessions.map((s) => `${s.name}${s.current ? ' (Current)' : ''}`).join(', ') ||
                'Configured via ERP'}
            </p>
          </div>

          <div className="grid-cards">
            {site.classes.map((cls) => {
              const clsSections = site.sections.filter((sec) => sec.classId === cls.id);
              return (
                <div key={cls.id} className="module-card">
                  <div className="page-header">
                    <h3>{cls.name}</h3>
                    <span className="badge active">{cls.code}</span>
                  </div>
                  <p className="muted">
                    Sections:{' '}
                    {clsSections.length > 0
                      ? clsSections.map((s) => s.name).join(', ')
                      : 'General Section'}
                  </p>
                </div>
              );
            })}
            {site.classes.length === 0 && (
              <div className="card muted">
                Academic classes will appear here as soon as they are added in the ERP Academic Setup.
              </div>
            )}
          </div>

          {site.subjects.length > 0 && (
            <div className="card">
              <h4>Offered Curriculum Subjects</h4>
              <div className="action-row" style={{ marginTop: 8 }}>
                {site.subjects.map((sub) => (
                  <span key={sub.id} className="badge">
                    {sub.name} ({sub.code})
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* Faculty & Teachers Showcase */}
      <section id="teachers" className="site-section" style={{ paddingTop: 0 }}>
        <div className="stack">
          <div>
            <h2>{t('website.section.teachers')}</h2>
            <p className="muted">
              Meet our dedicated educators and academic mentors at {site.schoolName}.
            </p>
          </div>

          <div className="grid-cards">
            {site.teachers.map((teacher) => {
              const tInitials = teacher.fullName
                .split(' ')
                .slice(0, 2)
                .map((w) => w[0] ?? '')
                .join('')
                .toUpperCase();
              return (
                <div key={teacher.id} className="teacher-card">
                  <div className="teacher-avatar">{tInitials || 'T'}</div>
                  <div className="stack" style={{ gap: 4 }}>
                    <div style={{ fontWeight: 700, fontSize: '1.02rem' }}>{teacher.fullName}</div>
                    <div style={{ color: 'var(--color-primary)', fontWeight: 600, fontSize: '0.86rem' }}>
                      {teacher.designationTitle}
                    </div>
                    {teacher.qualification && (
                      <div className="muted" style={{ fontSize: '0.82rem' }}>
                        {teacher.qualification}
                        {teacher.specialization ? ` • ${teacher.specialization}` : ''}
                      </div>
                    )}
                    {teacher.email && (
                      <div className="muted" style={{ fontSize: '0.8rem' }}>
                        {teacher.email}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
            {site.teachers.length === 0 && (
              <div className="card muted">
                Faculty profiles added in the ERP Staff & Teachers module automatically appear here.
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Public Notice Board */}
      <section id="notices" className="site-section" style={{ paddingTop: 0 }}>
        <div className="stack">
          <h2>{t('website.section.notices')}</h2>
          <div className="grid-cards" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' }}>
            {site.notices.map((n) => (
              <article key={n.id} className="card stack" style={{ gap: 10 }}>
                <div className="page-header">
                  <span className={`badge ${n.priority === 'URGENT' ? 'danger' : 'active'}`}>
                    {n.pinned ? '📌 PINNED • ' : ''}
                    {n.category}
                  </span>
                  {n.publishedAt && (
                    <span className="muted" style={{ fontSize: '0.78rem' }}>
                      {new Date(n.publishedAt).toLocaleDateString()}
                    </span>
                  )}
                </div>
                <h3 style={{ marginBottom: 0 }}>{n.title}</h3>
                <p className="muted" style={{ whiteSpace: 'pre-wrap' }}>
                  {n.bodyMarkdown}
                </p>
              </article>
            ))}
            {site.notices.length === 0 && (
              <div className="card muted">No public notices published yet.</div>
            )}
          </div>
        </div>
      </section>

      {/* Online Admission Form */}
      <section id="admission" className="site-section" style={{ paddingTop: 0 }}>
        <div className="card stack">
          <div>
            <h2>{t('website.section.admission')}</h2>
            <p className="muted">{t('website.admission.subtitle')}</p>
          </div>

          {applySuccess && <div className="success">{applySuccess}</div>}
          {applyError && <div className="error-text">{applyError}</div>}

          <form className="form-grid" onSubmit={handleApply}>
            <div>
              <label>Academic Session</label>
              <select
                value={sessionId}
                onChange={(e) => setSessionId(Number(e.target.value))}
                required
              >
                <option value={0}>-- Select Session --</option>
                {site.sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Applying for Class</label>
              <select
                value={classId}
                onChange={(e) => setClassId(Number(e.target.value))}
                required
              >
                <option value={0}>-- Select Class --</option>
                {site.classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Student First Name</label>
              <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
            </div>
            <div>
              <label>Student Last Name</label>
              <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
            </div>
            <div>
              <label>Date of Birth</label>
              <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} required />
            </div>
            <div>
              <label>Gender</label>
              <select value={gender} onChange={(e) => setGender(e.target.value)}>
                <option value="MALE">MALE</option>
                <option value="FEMALE">FEMALE</option>
                <option value="OTHER">OTHER</option>
              </select>
            </div>
            <div>
              <label>Guardian Full Name</label>
              <input
                value={guardianName}
                onChange={(e) => setGuardianName(e.target.value)}
                required
              />
            </div>
            <div>
              <label>Guardian Phone</label>
              <input
                value={guardianPhone}
                onChange={(e) => setGuardianPhone(e.target.value)}
                placeholder="+8801..."
                required
              />
            </div>
            <div>
              <label>Guardian Email (Optional)</label>
              <input
                type="email"
                value={guardianEmail}
                onChange={(e) => setGuardianEmail(e.target.value)}
              />
            </div>
            <div>
              <label>Relationship</label>
              <select
                value={guardianRelation}
                onChange={(e) => setGuardianRelation(e.target.value)}
              >
                <option value="FATHER">FATHER</option>
                <option value="MOTHER">MOTHER</option>
                <option value="GUARDIAN">GUARDIAN</option>
              </select>
            </div>
            <div>
              <label>Previous School (Optional)</label>
              <input value={prevSchool} onChange={(e) => setPrevSchool(e.target.value)} />
            </div>
            <button type="submit">{t('website.admission.submit')}</button>
          </form>
        </div>
      </section>

      {/* Footer */}
      <footer className="site-footer">
        <div
          style={{
            maxWidth: 1280,
            margin: '0 auto',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 16
          }}
        >
          <div>
            <strong style={{ color: '#fff', fontSize: '1.05rem' }}>{site.schoolName}</strong>
            <div style={{ fontSize: '0.85rem', marginTop: 4 }}>
              {site.contactAddress} &bull; {site.contactPhone} &bull; {site.contactEmail}
            </div>
          </div>
          <div className="action-row">
            <Link to={isAuthenticated ? '/' : '/login'} className="btn">
              {isAuthenticated ? t('website.nav.erpDashboard') : t('website.nav.erpLogin')}
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

