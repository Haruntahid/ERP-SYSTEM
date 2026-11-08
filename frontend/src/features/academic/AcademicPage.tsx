import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getErrorMessage } from '../../shared/api/client';
import { useI18n } from '../../shared/i18n';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AcademicSessionItem {
  id: number;
  code: string;
  name: string;
  sessionType: string;
  startDate: string;
  endDate: string;
  status: string;
  current: boolean;
}

export interface AcademicClassItem {
  id: number;
  departmentId: number;
  code: string;
  name: string;
  numericLevel: number;
}

export interface SectionItem {
  id: number;
  classId: number;
  code: string;
  name: string;
  capacity: number;
}

export interface SubjectItem {
  id: number;
  code: string;
  name: string;
  subjectType: string;
  creditHours: number;
}

export interface CampusItem {
  id: number;
  code: string;
  name: string;
  address?: string;
  phone?: string;
}

export function AcademicPage({ initialTab = 'sessions' }: { initialTab?: 'sessions' | 'classes' | 'subjects' | 'campuses' }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'sessions' | 'classes' | 'subjects' | 'campuses'>(initialTab);
  const [error, setError] = useState<string | null>(null);

  // Session form state
  const [sessCode, setSessCode] = useState('');
  const [sessName, setSessName] = useState('');
  const [sessType, setSessType] = useState('YEARLY');
  const [startDate, setStartDate] = useState('2026-01-01');
  const [endDate, setEndDate] = useState('2026-12-31');

  // Class & Section form state
  const [classCode, setClassCode] = useState('');
  const [className, setClassName] = useState('');
  const [numericLevel, setNumericLevel] = useState(6);
  const [selectedClassId, setSelectedClassId] = useState<number>(1);
  const [secCode, setSecCode] = useState('');
  const [secName, setSecName] = useState('');
  const [secCapacity, setSecCapacity] = useState(40);

  // Subject form state
  const [subjCode, setSubjCode] = useState('');
  const [subjName, setSubjName] = useState('');
  const [subjType, setSubjType] = useState('THEORY');
  const [creditHours, setCreditHours] = useState('1.00');

  // Campus form state
  const [campusCode, setCampusCode] = useState('');
  const [campusName, setCampusName] = useState('');
  const [campusAddress, setCampusAddress] = useState('');
  const [campusPhone, setCampusPhone] = useState('');

  const sessionsQuery = useQuery({
    queryKey: ['academic', 'sessions'],
    queryFn: async () => (await api.get<PageResponse<AcademicSessionItem>>('/api/v1/academic/sessions')).data.content,
  });

  const classesQuery = useQuery({
    queryKey: ['academic', 'classes'],
    queryFn: async () => (await api.get<PageResponse<AcademicClassItem>>('/api/v1/academic/classes')).data.content,
  });

  const sectionsQuery = useQuery({
    queryKey: ['academic', 'sections'],
    queryFn: async () => (await api.get<SectionItem[]>('/api/v1/academic/sections')).data,
  });

  const subjectsQuery = useQuery({
    queryKey: ['academic', 'subjects'],
    queryFn: async () => (await api.get<PageResponse<SubjectItem>>('/api/v1/academic/subjects')).data.content,
  });

  const campusesQuery = useQuery({
    queryKey: ['academic', 'campuses'],
    queryFn: async () => (await api.get<PageResponse<CampusItem>>('/api/v1/academic/campuses')).data.content,
  });

  const createSessionMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/academic/sessions', {
        code: sessCode,
        name: sessName,
        sessionType: sessType,
        startDate,
        endDate,
      });
    },
    onSuccess: () => {
      setSessCode('');
      setSessName('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['academic', 'sessions'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const activateSessionMut = useMutation({
    mutationFn: async (id: number) => api.post(`/api/v1/academic/sessions/${id}/activate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academic', 'sessions'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const archiveSessionMut = useMutation({
    mutationFn: async (id: number) => api.post(`/api/v1/academic/sessions/${id}/archive`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academic', 'sessions'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const createClassMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/academic/classes', {
        departmentId: 1,
        code: classCode,
        name: className,
        numericLevel: Number(numericLevel),
      });
    },
    onSuccess: () => {
      setClassCode('');
      setClassName('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['academic', 'classes'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const createSectionMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/academic/sections', {
        classId: Number(selectedClassId),
        code: secCode,
        name: secName,
        capacity: Number(secCapacity),
      });
    },
    onSuccess: () => {
      setSecCode('');
      setSecName('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['academic', 'sections'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const createSubjectMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/academic/subjects', {
        code: subjCode,
        name: subjName,
        subjectType: subjType,
        creditHours: Number(creditHours),
      });
    },
    onSuccess: () => {
      setSubjCode('');
      setSubjName('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['academic', 'subjects'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const createCampusMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/academic/campuses', {
        code: campusCode,
        name: campusName,
        address: campusAddress,
        phone: campusPhone,
      });
    },
    onSuccess: () => {
      setCampusCode('');
      setCampusName('');
      setCampusAddress('');
      setCampusPhone('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['academic', 'campuses'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  return (
    <div className="stack">
      <h1>{t('academic.nav.title')}</h1>
      {error && <p className="error">{error}</p>}

      <div className="tabs">
        <button type="button" className={`tab-btn ${tab === 'sessions' ? 'active' : ''}`} onClick={() => setTab('sessions')}>
          {t('academic.tabs.sessions')}
        </button>
        <button type="button" className={`tab-btn ${tab === 'classes' ? 'active' : ''}`} onClick={() => setTab('classes')}>
          {t('academic.tabs.classes')}
        </button>
        <button type="button" className={`tab-btn ${tab === 'subjects' ? 'active' : ''}`} onClick={() => setTab('subjects')}>
          {t('academic.tabs.subjects')}
        </button>
        <button type="button" className={`tab-btn ${tab === 'campuses' ? 'active' : ''}`} onClick={() => setTab('campuses')}>
          {t('academic.tabs.campuses')}
        </button>
      </div>

      {tab === 'sessions' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => createSessionMut.mutate(e)}>
            <label>
              Code
              <input required value={sessCode} onChange={(e) => setSessCode(e.target.value)} placeholder="2026" />
            </label>
            <label>
              Session Name
              <input required value={sessName} onChange={(e) => setSessName(e.target.value)} placeholder="Academic Year 2026" />
            </label>
            <label>
              Type
              <select value={sessType} onChange={(e) => setSessType(e.target.value)}>
                <option value="YEARLY">YEARLY</option>
                <option value="SEMESTER">SEMESTER</option>
              </select>
            </label>
            <label>
              Start Date
              <input type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </label>
            <label>
              End Date
              <input type="date" required value={endDate} onChange={(e) => setEndDate(e.target.value)} />
            </label>
            <button type="submit" disabled={createSessionMut.isPending}>{t('common.save')}</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Period</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {(sessionsQuery.data ?? []).map((s) => (
                <tr key={s.id}>
                  <td>{s.id}</td>
                  <td><strong>{s.code}</strong></td>
                  <td>{s.name}</td>
                  <td>{s.sessionType}</td>
                  <td>{s.startDate} → {s.endDate}</td>
                  <td>
                    <span className={`badge ${s.current ? 'success' : ''}`}>
                      {s.status}{s.current ? ' (CURRENT)' : ''}
                    </span>
                  </td>
                  <td className="row">
                    {!s.current && (
                      <button type="button" onClick={() => activateSessionMut.mutate(s.id)}>Activate</button>
                    )}
                    {s.status !== 'ARCHIVED' && (
                      <button type="button" className="secondary" onClick={() => archiveSessionMut.mutate(s.id)}>Archive</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'classes' && (
        <div className="stack">
          <div className="grid-cards">
            <form className="card stack" onSubmit={(e) => createClassMut.mutate(e)}>
              <h3>Create Class</h3>
              <label>
                Class Code
                <input required value={classCode} onChange={(e) => setClassCode(e.target.value)} placeholder="C06" />
              </label>
              <label>
                Class Name
                <input required value={className} onChange={(e) => setClassName(e.target.value)} placeholder="Class 6" />
              </label>
              <label>
                Numeric Level
                <input type="number" min={1} required value={numericLevel} onChange={(e) => setNumericLevel(Number(e.target.value))} />
              </label>
              <button type="submit" disabled={createClassMut.isPending}>Add Class</button>
            </form>

            <form className="card stack" onSubmit={(e) => createSectionMut.mutate(e)}>
              <h3>Create Section</h3>
              <label>
                Class
                <select value={selectedClassId} onChange={(e) => setSelectedClassId(Number(e.target.value))}>
                  {(classesQuery.data ?? []).map((c) => (
                    <option key={c.id} value={c.id}>{c.name} ({c.code})</option>
                  ))}
                </select>
              </label>
              <label>
                Section Code
                <input required value={secCode} onChange={(e) => setSecCode(e.target.value)} placeholder="A" />
              </label>
              <label>
                Section Name
                <input required value={secName} onChange={(e) => setSecName(e.target.value)} placeholder="Section A" />
              </label>
              <label>
                Capacity
                <input type="number" min={1} required value={secCapacity} onChange={(e) => setSecCapacity(Number(e.target.value))} />
              </label>
              <button type="submit" disabled={createSectionMut.isPending}>Add Section</button>
            </form>
          </div>

          <div className="card stack">
            <h3>Classes & Sections Directory</h3>
            <table>
              <thead>
                <tr>
                  <th>Class ID</th>
                  <th>Code</th>
                  <th>Class Name</th>
                  <th>Level</th>
                  <th>Sections</th>
                </tr>
              </thead>
              <tbody>
                {(classesQuery.data ?? []).map((c) => {
                  const classSecs = (sectionsQuery.data ?? []).filter((sec) => sec.classId === c.id);
                  return (
                    <tr key={c.id}>
                      <td>{c.id}</td>
                      <td><strong>{c.code}</strong></td>
                      <td>{c.name}</td>
                      <td>{c.numericLevel}</td>
                      <td>
                        {classSecs.map((sec) => (
                          <span key={sec.id} className="badge" style={{ marginRight: 6 }}>
                            #{sec.id} {sec.name} (Cap: {sec.capacity})
                          </span>
                        ))}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'subjects' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => createSubjectMut.mutate(e)}>
            <label>
              Subject Code
              <input required value={subjCode} onChange={(e) => setSubjCode(e.target.value)} placeholder="MATH-101" />
            </label>
            <label>
              Subject Name
              <input required value={subjName} onChange={(e) => setSubjName(e.target.value)} placeholder="General Mathematics" />
            </label>
            <label>
              Type
              <select value={subjType} onChange={(e) => setSubjType(e.target.value)}>
                <option value="THEORY">THEORY</option>
                <option value="LAB">LAB</option>
                <option value="COMBINED">COMBINED</option>
              </select>
            </label>
            <label>
              Credit Hours
              <input type="number" step="0.5" required value={creditHours} onChange={(e) => setCreditHours(e.target.value)} />
            </label>
            <button type="submit" disabled={createSubjectMut.isPending}>{t('common.save')}</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Code</th>
                <th>Subject Name</th>
                <th>Type</th>
                <th>Credit Hours</th>
              </tr>
            </thead>
            <tbody>
              {(subjectsQuery.data ?? []).map((sub) => (
                <tr key={sub.id}>
                  <td>{sub.id}</td>
                  <td><strong>{sub.code}</strong></td>
                  <td>{sub.name}</td>
                  <td><span className="badge">{sub.subjectType}</span></td>
                  <td>{sub.creditHours}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'campuses' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => createCampusMut.mutate(e)}>
            <label>
              Campus Code
              <input required value={campusCode} onChange={(e) => setCampusCode(e.target.value)} placeholder="NORTH" />
            </label>
            <label>
              Campus Name
              <input required value={campusName} onChange={(e) => setCampusName(e.target.value)} placeholder="North Campus" />
            </label>
            <label>
              Address
              <input value={campusAddress} onChange={(e) => setCampusAddress(e.target.value)} placeholder="Uttara, Dhaka" />
            </label>
            <label>
              Phone
              <input value={campusPhone} onChange={(e) => setCampusPhone(e.target.value)} placeholder="01700000000" />
            </label>
            <button type="submit" disabled={createCampusMut.isPending}>{t('common.save')}</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Code</th>
                <th>Name</th>
                <th>Address</th>
                <th>Phone</th>
              </tr>
            </thead>
            <tbody>
              {(campusesQuery.data ?? []).map((c) => (
                <tr key={c.id}>
                  <td>{c.id}</td>
                  <td><strong>{c.code}</strong></td>
                  <td>{c.name}</td>
                  <td>{c.address ?? '—'}</td>
                  <td>{c.phone ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

