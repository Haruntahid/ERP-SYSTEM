import { FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getErrorMessage } from '../../shared/api/client';
import { useI18n } from '../../shared/i18n';
import { ConfirmDeleteModal, DeleteTarget } from '../../shared/components/ConfirmDeleteModal';

interface PageResponse<T> {
  content: T[];
}

function unwrapList<T>(data: PageResponse<T> | T[] | null | undefined): T[] {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.content)) return data.content;
  return [];
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

interface SettingItem {
  key: string;
  value: string;
}

function autoCode(name: string, prefix: string): string {
  const cleaned = name
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return cleaned ? `${prefix}-${cleaned}`.slice(0, 36) : `${prefix}-${Date.now().toString().slice(-4)}`;
}

export function AcademicPage({ initialTab = 'sessions' }: { initialTab?: 'sessions' | 'classes' | 'subjects' | 'campuses' }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'sessions' | 'classes' | 'subjects' | 'campuses'>(initialTab);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  // Session form state
  const [editingSessionId, setEditingSessionId] = useState<number | null>(null);
  const [sessCode, setSessCode] = useState('');
  const [sessName, setSessName] = useState('');
  const [sessType, setSessType] = useState('YEARLY');
  const [startDate, setStartDate] = useState('2026-01-01');
  const [endDate, setEndDate] = useState('2026-12-31');

  // Class form state
  const [editingClassId, setEditingClassId] = useState<number | null>(null);
  const [classCode, setClassCode] = useState('');
  const [className, setClassName] = useState('');
  const [numericLevel, setNumericLevel] = useState(6);

  // Section form state
  const [editingSectionId, setEditingSectionId] = useState<number | null>(null);
  const [selectedClassId, setSelectedClassId] = useState<number>(0);
  const [secCode, setSecCode] = useState('');
  const [secName, setSecName] = useState('');
  const [secCapacity, setSecCapacity] = useState(40);

  // Subject form state
  const [editingSubjectId, setEditingSubjectId] = useState<number | null>(null);
  const [subjCode, setSubjCode] = useState('');
  const [subjName, setSubjName] = useState('');
  const [subjType, setSubjType] = useState('THEORY');
  const [creditHours, setCreditHours] = useState('3.00');

  // Campus form state
  const [editingCampusId, setEditingCampusId] = useState<number | null>(null);
  const [campusCode, setCampusCode] = useState('');
  const [campusName, setCampusName] = useState('');
  const [campusAddress, setCampusAddress] = useState('');
  const [campusPhone, setCampusPhone] = useState('');

  const settingsQuery = useQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const res = await api.get<SettingItem[]>('/api/v1/settings');
      const map: Record<string, string> = {};
      for (const item of res.data) {
        map[item.key] = item.value;
      }
      return map;
    },
  });

  const creditHoursEnabled = settingsQuery.data?.['academic.credit_hours_enabled'] === 'true';

  const toggleCreditHoursMut = useMutation({
    mutationFn: async (enabled: boolean) =>
      api.put('/api/v1/settings/academic.credit_hours_enabled', { value: enabled ? 'true' : 'false' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['settings'] });
      setMessage('Credit Hours display setting updated.');
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const sessionsQuery = useQuery({
    queryKey: ['academic', 'sessions'],
    queryFn: async () => unwrapList((await api.get<PageResponse<AcademicSessionItem>>('/api/v1/academic/sessions?size=100')).data),
  });

  const classesQuery = useQuery({
    queryKey: ['academic', 'classes'],
    queryFn: async () => unwrapList((await api.get<PageResponse<AcademicClassItem>>('/api/v1/academic/classes?size=100')).data),
  });

  const sectionsQuery = useQuery({
    queryKey: ['academic', 'sections'],
    queryFn: async () => unwrapList((await api.get<SectionItem[]>('/api/v1/academic/sections')).data),
  });

  const subjectsQuery = useQuery({
    queryKey: ['academic', 'subjects'],
    queryFn: async () => unwrapList((await api.get<PageResponse<SubjectItem>>('/api/v1/academic/subjects?size=100')).data),
  });

  const campusesQuery = useQuery({
    queryKey: ['academic', 'campuses'],
    queryFn: async () => unwrapList((await api.get<PageResponse<CampusItem>>('/api/v1/academic/campuses?size=100')).data),
  });

  const sessions = sessionsQuery.data ?? [];
  const classes = classesQuery.data ?? [];
  const sections = sectionsQuery.data ?? [];
  const subjects = subjectsQuery.data ?? [];
  const campuses = campusesQuery.data ?? [];

  useEffect(() => {
    if (selectedClassId === 0 && classes[0]) {
      setSelectedClassId(classes[0].id);
    }
  }, [classes, selectedClassId]);

  const classMap = new Map(classes.map((c) => [c.id, c]));

  // Session mutations
  const saveSessionMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      const generatedCode = sessCode.trim() || autoCode(sessName, 'AY');
      const payload = {
        code: generatedCode,
        name: sessName,
        sessionType: sessType,
        startDate,
        endDate,
      };
      if (editingSessionId) {
        return api.put(`/api/v1/academic/sessions/${editingSessionId}`, payload);
      }
      return api.post('/api/v1/academic/sessions', payload);
    },
    onSuccess: () => {
      setEditingSessionId(null);
      setSessCode('');
      setSessName('');
      setError(null);
      setMessage(editingSessionId ? 'Academic session updated.' : 'Academic session created.');
      void qc.invalidateQueries({ queryKey: ['academic', 'sessions'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const activateSessionMut = useMutation({
    mutationFn: async (id: number) => api.post(`/api/v1/academic/sessions/${id}/activate`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['academic', 'sessions'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const archiveSessionMut = useMutation({
    mutationFn: async (id: number) => api.post(`/api/v1/academic/sessions/${id}/archive`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['academic', 'sessions'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteSessionMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/academic/sessions/${id}`),
    onSuccess: () => {
      setMessage('Academic session deleted.');
      void qc.invalidateQueries({ queryKey: ['academic', 'sessions'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  // Class mutations
  const saveClassMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      const generatedCode = classCode.trim() || `C${String(numericLevel).padStart(2, '0')}-${autoCode(className, 'CLS').slice(4, 12)}`;
      const payload = {
        departmentId: null,
        code: generatedCode,
        name: className,
        numericLevel: Number(numericLevel),
      };
      if (editingClassId) {
        return api.put(`/api/v1/academic/classes/${editingClassId}`, payload);
      }
      return api.post('/api/v1/academic/classes', payload);
    },
    onSuccess: () => {
      setEditingClassId(null);
      setClassCode('');
      setClassName('');
      setError(null);
      setMessage(editingClassId ? 'Class updated.' : 'Class created.');
      void qc.invalidateQueries({ queryKey: ['academic', 'classes'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteClassMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/academic/classes/${id}`),
    onSuccess: () => {
      setMessage('Class deleted.');
      void qc.invalidateQueries({ queryKey: ['academic', 'classes'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  // Section mutations
  const saveSectionMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      if (!selectedClassId) {
        throw new Error('Please select a Class first.');
      }
      const generatedCode = secCode.trim() || autoCode(secName, 'SEC');
      const payload = {
        classId: Number(selectedClassId),
        code: generatedCode,
        name: secName,
        capacity: Number(secCapacity),
      };
      if (editingSectionId) {
        return api.put(`/api/v1/academic/sections/${editingSectionId}`, payload);
      }
      return api.post('/api/v1/academic/sections', payload);
    },
    onSuccess: () => {
      setEditingSectionId(null);
      setSecCode('');
      setSecName('');
      setError(null);
      setMessage(editingSectionId ? 'Section updated.' : 'Section created.');
      void qc.invalidateQueries({ queryKey: ['academic', 'sections'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteSectionMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/academic/sections/${id}`),
    onSuccess: () => {
      setMessage('Section deleted.');
      void qc.invalidateQueries({ queryKey: ['academic', 'sections'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  // Subject mutations
  const saveSubjectMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      const generatedCode = subjCode.trim() || autoCode(subjName, 'SUB');
      const payload = {
        code: generatedCode,
        name: subjName,
        subjectType: subjType,
        creditHours: creditHoursEnabled ? Number(creditHours) : 0,
      };
      if (editingSubjectId) {
        return api.put(`/api/v1/academic/subjects/${editingSubjectId}`, payload);
      }
      return api.post('/api/v1/academic/subjects', payload);
    },
    onSuccess: () => {
      setEditingSubjectId(null);
      setSubjCode('');
      setSubjName('');
      setError(null);
      setMessage(editingSubjectId ? 'Subject updated.' : 'Subject created.');
      void qc.invalidateQueries({ queryKey: ['academic', 'subjects'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteSubjectMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/academic/subjects/${id}`),
    onSuccess: () => {
      setMessage('Subject deleted.');
      void qc.invalidateQueries({ queryKey: ['academic', 'subjects'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  // Campus mutations
  const saveCampusMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      const generatedCode = campusCode.trim() || autoCode(campusName, 'CAMP');
      const payload = {
        code: generatedCode,
        name: campusName,
        address: campusAddress,
        phone: campusPhone,
      };
      if (editingCampusId) {
        return api.put(`/api/v1/academic/campuses/${editingCampusId}`, payload);
      }
      return api.post('/api/v1/academic/campuses', payload);
    },
    onSuccess: () => {
      setEditingCampusId(null);
      setCampusCode('');
      setCampusName('');
      setCampusAddress('');
      setCampusPhone('');
      setError(null);
      setMessage(editingCampusId ? 'Campus updated.' : 'Campus saved.');
      void qc.invalidateQueries({ queryKey: ['academic', 'campuses'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteCampusMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/academic/campuses/${id}`),
    onSuccess: () => {
      setMessage('Campus deleted.');
      void qc.invalidateQueries({ queryKey: ['academic', 'campuses'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  return (
    <div className="stack">
      <ConfirmDeleteModal target={deleteTarget} onClose={() => setDeleteTarget(null)} />

      <div>
        <h2>{t('academic.nav.title')}</h2>
        <p className="muted">Configure sessions, classes, sections, subjects, and campuses. All system codes are auto-generated.</p>
      </div>

      {error && <div className="error-text">{error}</div>}
      {message && <div className="success">{message}</div>}

      <div className="tabs">
        <button type="button" className={`tab-btn ${tab === 'sessions' ? 'active' : ''}`} onClick={() => setTab('sessions')}>
          {t('academic.tabs.sessions')} ({sessions.length})
        </button>
        <button type="button" className={`tab-btn ${tab === 'classes' ? 'active' : ''}`} onClick={() => setTab('classes')}>
          {t('academic.tabs.classes')} ({classes.length} / {sections.length} Sections)
        </button>
        <button type="button" className={`tab-btn ${tab === 'subjects' ? 'active' : ''}`} onClick={() => setTab('subjects')}>
          {t('academic.tabs.subjects')} ({subjects.length})
        </button>
        <button type="button" className={`tab-btn ${tab === 'campuses' ? 'active' : ''}`} onClick={() => setTab('campuses')}>
          {t('academic.tabs.campuses')} ({campuses.length})
        </button>
      </div>

      {tab === 'sessions' && (
        <div className="stack">
          <form className="card stack" onSubmit={(e) => saveSessionMut.mutate(e)}>
            <div className="page-header">
              <h3>{editingSessionId ? 'Edit Academic Session' : 'Create Academic Session'}</h3>
              <span className="badge active">System Code: {sessCode || (sessName ? autoCode(sessName, 'AY') : 'Auto-Generated')}</span>
            </div>
            <div className="form-grid">
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
            </div>
            <div className="action-row">
              <button type="submit" disabled={saveSessionMut.isPending}>
                {editingSessionId ? 'Update Session' : t('common.save')}
              </button>
              {editingSessionId && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingSessionId(null);
                    setSessCode('');
                    setSessName('');
                  }}
                >
                  Cancel Edit
                </button>
              )}
            </div>
          </form>

          <div className="card">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>System Code</th>
                  <th>Type</th>
                  <th>Period</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((s) => (
                  <tr key={s.id}>
                    <td><strong>{s.name}</strong></td>
                    <td><code>{s.code}</code></td>
                    <td>{s.sessionType}</td>
                    <td>{s.startDate} &rarr; {s.endDate}</td>
                    <td>
                      <span className={`badge ${s.current ? 'success' : ''}`}>
                        {s.status}{s.current ? ' (CURRENT)' : ''}
                      </span>
                    </td>
                    <td className="action-row">
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => {
                          setEditingSessionId(s.id);
                          setSessCode(s.code);
                          setSessName(s.name);
                          setSessType(s.sessionType);
                          setStartDate(s.startDate);
                          setEndDate(s.endDate);
                        }}
                      >
                        Edit
                      </button>
                      {!s.current && (
                        <button type="button" onClick={() => activateSessionMut.mutate(s.id)}>Activate</button>
                      )}
                      {s.status !== 'ARCHIVED' && (
                        <button type="button" className="secondary" onClick={() => archiveSessionMut.mutate(s.id)}>Archive</button>
                      )}
                      <button
                        type="button"
                        className="danger"
                        onClick={() =>
                          setDeleteTarget({
                            title: `Delete Session: ${s.name}`,
                            onConfirm: async () => {
                              await deleteSessionMut.mutateAsync(s.id);
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

      {tab === 'classes' && (
        <div className="stack">
          <div className="grid-cards">
            <form className="card stack" onSubmit={(e) => saveClassMut.mutate(e)}>
              <div className="page-header">
                <h3>{editingClassId ? 'Edit Class' : 'Create Class'}</h3>
                <span className="badge active">Auto Code</span>
              </div>
              <label>
                Class Name
                <input required value={className} onChange={(e) => setClassName(e.target.value)} placeholder="e.g. Class 6 or Grade 10" />
              </label>
              <label>
                Numeric Grade Level
                <input type="number" min={1} required value={numericLevel} onChange={(e) => setNumericLevel(Number(e.target.value))} />
              </label>
              <div className="action-row">
                <button type="submit" disabled={saveClassMut.isPending}>
                  {editingClassId ? 'Update Class' : 'Add Class'}
                </button>
                {editingClassId && (
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => {
                      setEditingClassId(null);
                      setClassCode('');
                      setClassName('');
                    }}
                  >
                    Cancel Edit
                  </button>
                )}
              </div>
            </form>

            <form className="card stack" onSubmit={(e) => saveSectionMut.mutate(e)}>
              <div className="page-header">
                <h3>{editingSectionId ? 'Edit Section' : 'Create Section'}</h3>
                <span className="badge active">Auto Code</span>
              </div>
              <label>
                Class
                <select required value={selectedClassId} onChange={(e) => setSelectedClassId(Number(e.target.value))}>
                  <option value={0}>-- Select Class --</option>
                  {classes.map((c) => (
                    <option key={c.id} value={c.id}>{c.name} ({c.code})</option>
                  ))}
                </select>
              </label>
              <label>
                Section Name
                <input required value={secName} onChange={(e) => setSecName(e.target.value)} placeholder="e.g. Section A or Rose" />
              </label>
              <label>
                Seat Capacity
                <input type="number" min={1} required value={secCapacity} onChange={(e) => setSecCapacity(Number(e.target.value))} />
              </label>
              <div className="action-row">
                <button type="submit" disabled={saveSectionMut.isPending}>
                  {editingSectionId ? 'Update Section' : 'Add Section'}
                </button>
                {editingSectionId && (
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => {
                      setEditingSectionId(null);
                      setSecCode('');
                      setSecName('');
                    }}
                  >
                    Cancel Edit
                  </button>
                )}
              </div>
            </form>
          </div>

          <div className="card stack">
            <h3>Classes Directory (Edit & Delete Classes)</h3>
            <table>
              <thead>
                <tr>
                  <th>Class Name</th>
                  <th>System Code</th>
                  <th>Grade Level</th>
                  <th>Assigned Sections</th>
                  <th>Class Actions</th>
                </tr>
              </thead>
              <tbody>
                {classes.map((c) => {
                  const classSecs = sections.filter((sec) => sec.classId === c.id);
                  return (
                    <tr key={c.id}>
                      <td><strong>{c.name}</strong></td>
                      <td><code>{c.code}</code></td>
                      <td>Level {c.numericLevel}</td>
                      <td>
                        {classSecs.length === 0 && <span className="muted">No sections yet</span>}
                        {classSecs.map((sec) => (
                          <span key={sec.id} className="badge" style={{ marginRight: 6 }}>
                            {sec.name} (Cap: {sec.capacity})
                          </span>
                        ))}
                      </td>
                      <td className="action-row">
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => {
                            setEditingClassId(c.id);
                            setClassCode(c.code);
                            setClassName(c.name);
                            setNumericLevel(c.numericLevel);
                          }}
                        >
                          Edit Class
                        </button>
                        <button
                          type="button"
                          className="danger"
                          onClick={() =>
                            setDeleteTarget({
                              title: `Delete Class: ${c.name} (${c.code})`,
                              onConfirm: async () => {
                                await deleteClassMut.mutateAsync(c.id);
                              },
                            })
                          }
                        >
                          Delete Class
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="card stack">
            <h3>Sections Directory (Edit & Delete Sections)</h3>
            <table>
              <thead>
                <tr>
                  <th>Class</th>
                  <th>Section Name</th>
                  <th>System Code</th>
                  <th>Seat Capacity</th>
                  <th>Section Actions</th>
                </tr>
              </thead>
              <tbody>
                {sections.map((sec) => {
                  const parentClass = classMap.get(sec.classId);
                  return (
                    <tr key={sec.id}>
                      <td><strong>{parentClass ? parentClass.name : `Class #${sec.classId}`}</strong></td>
                      <td>{sec.name}</td>
                      <td><code>{sec.code}</code></td>
                      <td>{sec.capacity} seats</td>
                      <td className="action-row">
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => {
                            setEditingSectionId(sec.id);
                            setSelectedClassId(sec.classId);
                            setSecCode(sec.code);
                            setSecName(sec.name);
                            setSecCapacity(sec.capacity);
                          }}
                        >
                          Edit Section
                        </button>
                        <button
                          type="button"
                          className="danger"
                          onClick={() =>
                            setDeleteTarget({
                              title: `Delete Section: ${parentClass?.name ?? ''} - ${sec.name}`,
                              onConfirm: async () => {
                                await deleteSectionMut.mutateAsync(sec.id);
                              },
                            })
                          }
                        >
                          Delete Section
                        </button>
                      </td>
                    </tr>
                  );
                })}
                {sections.length === 0 && (
                  <tr>
                    <td colSpan={5} className="muted">{t('common.noData')}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'subjects' && (
        <div className="stack">
          <div className="card page-header">
            <div>
              <strong>University Credit Hours Mode (`academic.credit_hours_enabled`)</strong>
              <p className="muted" style={{ margin: 0 }}>
                Enable Credit Hours for Universities & Colleges, or keep disabled for regular K-12 Schools.
              </p>
            </div>
            <div className="action-row">
              <span className={`badge ${creditHoursEnabled ? 'success' : ''}`}>
                {creditHoursEnabled ? 'Enabled (University Mode)' : 'Disabled (School Mode)'}
              </span>
              <button
                type="button"
                className="secondary"
                onClick={() => toggleCreditHoursMut.mutate(!creditHoursEnabled)}
              >
                {creditHoursEnabled ? 'Disable Credit Hours' : 'Enable Credit Hours'}
              </button>
            </div>
          </div>

          <form className="card stack" onSubmit={(e) => saveSubjectMut.mutate(e)}>
            <div className="page-header">
              <h3>{editingSubjectId ? 'Edit Subject' : 'Create Subject'}</h3>
              <span className="badge active">System Code: {subjCode || (subjName ? autoCode(subjName, 'SUB') : 'Auto-Generated')}</span>
            </div>
            <div className="form-grid">
              <label>
                Subject Name
                <input required value={subjName} onChange={(e) => setSubjName(e.target.value)} placeholder="General Mathematics" />
              </label>
              <label>
                Subject Type
                <select value={subjType} onChange={(e) => setSubjType(e.target.value)}>
                  <option value="THEORY">THEORY</option>
                  <option value="LAB">LAB</option>
                  <option value="COMBINED">COMBINED</option>
                </select>
              </label>
              {creditHoursEnabled && (
                <label>
                  Credit Hours (University Mode)
                  <input type="number" step="0.5" min="0" required value={creditHours} onChange={(e) => setCreditHours(e.target.value)} />
                </label>
              )}
            </div>
            <div className="action-row">
              <button type="submit" disabled={saveSubjectMut.isPending}>
                {editingSubjectId ? 'Update Subject' : t('common.save')}
              </button>
              {editingSubjectId && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingSubjectId(null);
                    setSubjCode('');
                    setSubjName('');
                  }}
                >
                  Cancel Edit
                </button>
              )}
            </div>
          </form>

          <div className="card">
            <table>
              <thead>
                <tr>
                  <th>Subject Name</th>
                  <th>System Code</th>
                  <th>Type</th>
                  {creditHoursEnabled && <th>Credit Hours</th>}
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {subjects.map((sub) => (
                  <tr key={sub.id}>
                    <td><strong>{sub.name}</strong></td>
                    <td><code>{sub.code}</code></td>
                    <td><span className="badge">{sub.subjectType}</span></td>
                    {creditHoursEnabled && <td>{sub.creditHours}</td>}
                    <td className="action-row">
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => {
                          setEditingSubjectId(sub.id);
                          setSubjCode(sub.code);
                          setSubjName(sub.name);
                          setSubjType(sub.subjectType);
                          setCreditHours(String(sub.creditHours ?? '3.00'));
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="danger"
                        onClick={() =>
                          setDeleteTarget({
                            title: `Delete Subject: ${sub.name} (${sub.code})`,
                            onConfirm: async () => {
                              await deleteSubjectMut.mutateAsync(sub.id);
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

      {tab === 'campuses' && (
        <div className="stack">
          <form className="card stack" onSubmit={(e) => saveCampusMut.mutate(e)}>
            <div className="page-header">
              <h3>{editingCampusId ? 'Edit Campus' : 'Create Campus'}</h3>
              <span className="badge active">System Code: {campusCode || (campusName ? autoCode(campusName, 'CAMP') : 'Auto-Generated')}</span>
            </div>
            <div className="form-grid">
              <label>
                Campus Name
                <input required value={campusName} onChange={(e) => setCampusName(e.target.value)} placeholder="Main Campus" />
              </label>
              <label>
                Address
                <input value={campusAddress} onChange={(e) => setCampusAddress(e.target.value)} placeholder="Dinajpur, Bangladesh" />
              </label>
              <label>
                Phone
                <input value={campusPhone} onChange={(e) => setCampusPhone(e.target.value)} placeholder="01700000000" />
              </label>
            </div>
            <div className="action-row">
              <button type="submit" disabled={saveCampusMut.isPending}>
                {editingCampusId ? 'Update Campus' : t('common.save')}
              </button>
              {editingCampusId && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingCampusId(null);
                    setCampusCode('');
                    setCampusName('');
                    setCampusAddress('');
                    setCampusPhone('');
                  }}
                >
                  Cancel Edit
                </button>
              )}
            </div>
          </form>

          <div className="card">
            <table>
              <thead>
                <tr>
                  <th>Campus Name</th>
                  <th>System Code</th>
                  <th>Address</th>
                  <th>Phone</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {campuses.map((c) => (
                  <tr key={c.id}>
                    <td><strong>{c.name}</strong></td>
                    <td><code>{c.code}</code></td>
                    <td>{c.address ?? '—'}</td>
                    <td>{c.phone ?? '—'}</td>
                    <td className="action-row">
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => {
                          setEditingCampusId(c.id);
                          setCampusCode(c.code);
                          setCampusName(c.name);
                          setCampusAddress(c.address ?? '');
                          setCampusPhone(c.phone ?? '');
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="danger"
                        onClick={() =>
                          setDeleteTarget({
                            title: `Delete Campus: ${c.name}`,
                            onConfirm: async () => {
                              await deleteCampusMut.mutateAsync(c.id);
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
    </div>
  );
}
