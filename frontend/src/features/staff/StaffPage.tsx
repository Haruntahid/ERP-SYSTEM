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

interface Designation {
  id: number;
  code: string;
  title: string;
  category: string;
}

interface StaffMember {
  id: number;
  campusId: number;
  departmentId: number | null;
  designationId: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string;
  staffType: string;
  employmentStatus: string;
  joinDate: string;
}

interface TeacherAssignment {
  id: number;
  academicSessionId: number;
  staffId: number;
  sectionId: number;
  subjectId: number | null;
  classTeacher: boolean;
  roleType: string;
}

interface AcademicSessionLookup {
  id: number;
  code: string;
  name: string;
  current: boolean;
}

interface AcademicClassLookup {
  id: number;
  code: string;
  name: string;
}

interface SectionLookup {
  id: number;
  classId: number;
  code: string;
  name: string;
  capacity: number;
}

interface SubjectLookup {
  id: number;
  code: string;
  name: string;
}

interface CampusLookup {
  id: number;
  code: string;
  name: string;
}

function slugifyCode(input: string, prefix = 'DESIG'): string {
  const cleaned = input
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  return cleaned ? cleaned.slice(0, 36) : `${prefix}_${Date.now().toString().slice(-4)}`;
}

export function StaffPage() {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'directory' | 'assignments' | 'designations'>('directory');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  // Add / Edit staff form
  const [editingStaffId, setEditingStaffId] = useState<number | null>(null);
  const [campusId, setCampusId] = useState<number>(0);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [designationId, setDesignationId] = useState<number>(0);
  const [staffType, setStaffType] = useState('TEACHING');
  const [joinDate, setJoinDate] = useState('2026-01-01');

  // Assignment form (user-friendly dropdown selections)
  const [sessionId, setSessionId] = useState<number>(0);
  const [staffId, setStaffId] = useState<number>(0);
  const [sectionId, setSectionId] = useState<number>(0);
  const [subjectId, setSubjectId] = useState<string>('');
  const [classTeacher, setClassTeacher] = useState(false);

  // Designation form
  const [editingDesigCode, setEditingDesigCode] = useState<string | null>(null);
  const [desigTitle, setDesigTitle] = useState('');
  const [desigCat, setDesigCat] = useState('TEACHING');

  const staffQuery = useQuery({
    queryKey: ['staff', 'list'],
    queryFn: async () => unwrapList((await api.get<PageResponse<StaffMember>>('/api/v1/staff?size=100')).data),
  });

  const designationsQuery = useQuery({
    queryKey: ['staff', 'designations'],
    queryFn: async () => unwrapList((await api.get<PageResponse<Designation>>('/api/v1/staff/designations?size=100')).data),
  });

  const assignmentsQuery = useQuery({
    queryKey: ['staff', 'assignments'],
    queryFn: async () => unwrapList((await api.get<TeacherAssignment[]>('/api/v1/staff/assignments')).data),
  });

  const sessionsQuery = useQuery({
    queryKey: ['academic', 'sessions'],
    queryFn: async () => unwrapList((await api.get<PageResponse<AcademicSessionLookup>>('/api/v1/academic/sessions?size=100')).data),
  });

  const classesQuery = useQuery({
    queryKey: ['academic', 'classes'],
    queryFn: async () => unwrapList((await api.get<PageResponse<AcademicClassLookup>>('/api/v1/academic/classes?size=100')).data),
  });

  const sectionsQuery = useQuery({
    queryKey: ['academic', 'sections'],
    queryFn: async () => unwrapList((await api.get<SectionLookup[]>('/api/v1/academic/sections')).data),
  });

  const subjectsQuery = useQuery({
    queryKey: ['academic', 'subjects'],
    queryFn: async () => unwrapList((await api.get<PageResponse<SubjectLookup>>('/api/v1/academic/subjects?size=100')).data),
  });

  const campusesQuery = useQuery({
    queryKey: ['academic', 'campuses'],
    queryFn: async () => unwrapList((await api.get<PageResponse<CampusLookup>>('/api/v1/academic/campuses?size=100')).data),
  });

  const staffList = staffQuery.data ?? [];
  const designations = designationsQuery.data ?? [];
  const assignments = assignmentsQuery.data ?? [];
  const sessions = sessionsQuery.data ?? [];
  const classes = classesQuery.data ?? [];
  const sections = sectionsQuery.data ?? [];
  const subjects = subjectsQuery.data ?? [];
  const campuses = campusesQuery.data ?? [];

  const teachingStaff = staffList.filter((s) => s.staffType === 'TEACHING' && s.employmentStatus === 'ACTIVE');

  useEffect(() => {
    if (campusId === 0 && campuses[0]) setCampusId(campuses[0].id);
    if (designationId === 0 && designations[0]) setDesignationId(designations[0].id);
    if (sessionId === 0 && sessions[0]) {
      const current = sessions.find((s) => s.current) ?? sessions[0];
      setSessionId(current.id);
    }
    if (staffId === 0 && teachingStaff[0]) setStaffId(teachingStaff[0].id);
    if (sectionId === 0 && sections[0]) setSectionId(sections[0].id);
  }, [campuses, designations, sessions, teachingStaff, sections, campusId, designationId, sessionId, staffId, sectionId]);

  const classMap = new Map(classes.map((c) => [c.id, c]));
  const sessionMap = new Map(sessions.map((s) => [s.id, s]));
  const staffMap = new Map(staffList.map((s) => [s.id, s]));
  const sectionMap = new Map(sections.map((sec) => [sec.id, sec]));
  const subjectMap = new Map(subjects.map((sub) => [sub.id, sub]));
  const designationMap = new Map(designations.map((d) => [d.id, d]));

  const formatSectionLabel = (sec: SectionLookup) => {
    const cls = classMap.get(sec.classId);
    return cls ? `${cls.name} — ${sec.name} (Cap: ${sec.capacity})` : `${sec.name} (Cap: ${sec.capacity})`;
  };

  const saveStaffMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      const targetCampusId = campusId || campuses[0]?.id || 1;
      const targetDesigId = designationId || designations[0]?.id || 1;
      const payload = {
        campusId: Number(targetCampusId),
        departmentId: null,
        designationId: Number(targetDesigId),
        firstName,
        lastName,
        email: email || undefined,
        phone,
        staffType,
        joinDate,
      };
      if (editingStaffId) {
        return api.put(`/api/v1/staff/${editingStaffId}`, payload);
      }
      return api.post('/api/v1/staff', payload);
    },
    onSuccess: () => {
      setEditingStaffId(null);
      setFirstName('');
      setLastName('');
      setEmail('');
      setPhone('');
      setError(null);
      setMessage(editingStaffId ? 'Staff member updated.' : 'Staff member added with system-generated Employee ID.');
      void qc.invalidateQueries({ queryKey: ['staff', 'list'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const changeStatusMut = useMutation({
    mutationFn: async ({ id, status }: { id: number; status: string }) =>
      api.patch(`/api/v1/staff/${id}/status`, { status }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['staff', 'list'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteStaffMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/staff/${id}`),
    onSuccess: () => {
      setMessage('Staff record deleted.');
      void qc.invalidateQueries({ queryKey: ['staff', 'list'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const assignTeacherMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      if (!sessionId || !staffId || !sectionId) {
        throw new Error('Please select an Academic Session, Teacher, and Class Section.');
      }
      return api.post('/api/v1/staff/assignments', {
        academicSessionId: Number(sessionId),
        staffId: Number(staffId),
        sectionId: Number(sectionId),
        subjectId: subjectId ? Number(subjectId) : null,
        classTeacher,
        roleType: 'PRIMARY',
      });
    },
    onSuccess: () => {
      setError(null);
      setMessage('Teacher assigned to section.');
      void qc.invalidateQueries({ queryKey: ['staff', 'assignments'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteAssignmentMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/staff/assignments/${id}`),
    onSuccess: () => {
      setMessage('Teacher assignment removed.');
      void qc.invalidateQueries({ queryKey: ['staff', 'assignments'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const saveDesigMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      const generatedCode = editingDesigCode ?? slugifyCode(desigTitle, 'DESIG');
      return api.post('/api/v1/staff/designations', {
        code: generatedCode,
        title: desigTitle,
        category: desigCat,
      });
    },
    onSuccess: () => {
      setEditingDesigCode(null);
      setDesigTitle('');
      setError(null);
      setMessage('Designation saved.');
      void qc.invalidateQueries({ queryKey: ['staff', 'designations'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteDesigMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/staff/designations/${id}`),
    onSuccess: () => {
      setMessage('Designation deleted.');
      void qc.invalidateQueries({ queryKey: ['staff', 'designations'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const startEditStaff = (s: StaffMember) => {
    setEditingStaffId(s.id);
    setCampusId(s.campusId);
    setDesignationId(s.designationId);
    setFirstName(s.firstName);
    setLastName(s.lastName);
    setEmail(s.email ?? '');
    setPhone(s.phone);
    setStaffType(s.staffType);
    setJoinDate(s.joinDate);
  };

  return (
    <div className="stack">
      <ConfirmDeleteModal target={deleteTarget} onClose={() => setDeleteTarget(null)} />

      <div>
        <h2>{t('staff.nav.title')}</h2>
        <p className="muted">Manage faculty directory, designations, and section/subject teaching assignments.</p>
      </div>

      {error && <div className="error-text">{error}</div>}
      {message && <div className="success">{message}</div>}

      <div className="tabs">
        <button type="button" className={`tab-btn ${tab === 'directory' ? 'active' : ''}`} onClick={() => setTab('directory')}>
          {t('staff.directory.title')} ({staffList.length})
        </button>
        <button type="button" className={`tab-btn ${tab === 'assignments' ? 'active' : ''}`} onClick={() => setTab('assignments')}>
          {t('staff.assignments.title')} ({assignments.length})
        </button>
        <button type="button" className={`tab-btn ${tab === 'designations' ? 'active' : ''}`} onClick={() => setTab('designations')}>
          {t('staff.field.designation')} ({designations.length})
        </button>
      </div>

      {tab === 'directory' && (
        <div className="stack">
          <form className="card stack" onSubmit={(e) => saveStaffMut.mutate(e)}>
            <div className="page-header">
              <h3>{editingStaffId ? 'Edit Staff Member' : 'Add New Staff / Teacher'}</h3>
              <span className="badge active">Employee ID: Auto-Generated by System</span>
            </div>
            <div className="form-grid">
              <label>
                First Name
                <input required value={firstName} onChange={(e) => setFirstName(e.target.value)} placeholder="Rahim" />
              </label>
              <label>
                Last Name
                <input required value={lastName} onChange={(e) => setLastName(e.target.value)} placeholder="Uddin" />
              </label>
              <label>
                Phone Number
                <input required value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="01711000000" />
              </label>
              <label>
                Email (Optional)
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="rahim@school.edu" />
              </label>
              <label>
                Campus
                <select value={campusId} onChange={(e) => setCampusId(Number(e.target.value))}>
                  {campuses.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </label>
              <label>
                {t('staff.field.designation')}
                <select value={designationId} onChange={(e) => setDesignationId(Number(e.target.value))}>
                  {designations.map((d) => (
                    <option key={d.id} value={d.id}>{d.title} ({d.category})</option>
                  ))}
                </select>
              </label>
              <label>
                Staff Category
                <select value={staffType} onChange={(e) => setStaffType(e.target.value)}>
                  <option value="TEACHING">Teaching Faculty</option>
                  <option value="NON_TEACHING">Administrative / Non-Teaching</option>
                </select>
              </label>
              <label>
                Join Date
                <input type="date" required value={joinDate} onChange={(e) => setJoinDate(e.target.value)} />
              </label>
            </div>
            <div className="action-row">
              <button type="submit" disabled={saveStaffMut.isPending}>
                {editingStaffId ? 'Update Staff Member' : t('staff.action.add')}
              </button>
              {editingStaffId && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingStaffId(null);
                    setFirstName('');
                    setLastName('');
                    setEmail('');
                    setPhone('');
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
                  <th>{t('staff.field.employeeCode')}</th>
                  <th>Full Name</th>
                  <th>Designation</th>
                  <th>Category</th>
                  <th>Phone / Email</th>
                  <th>Join Date</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {staffList.map((s) => (
                  <tr key={s.id}>
                    <td><code>{s.employeeCode}</code></td>
                    <td><strong>{s.fullName}</strong></td>
                    <td>{designationMap.get(s.designationId)?.title ?? 'Staff'}</td>
                    <td><span className="badge">{s.staffType}</span></td>
                    <td>{s.phone}{s.email ? ` • ${s.email}` : ''}</td>
                    <td>{s.joinDate}</td>
                    <td>
                      <span className={`badge ${s.employmentStatus === 'ACTIVE' ? 'success' : 'warning'}`}>
                        {t(`staff.status.${s.employmentStatus}`)}
                      </span>
                    </td>
                    <td className="action-row">
                      <button type="button" className="secondary" onClick={() => startEditStaff(s)}>
                        Edit
                      </button>
                      {s.employmentStatus !== 'ON_LEAVE' && (
                        <button type="button" className="secondary" onClick={() => changeStatusMut.mutate({ id: s.id, status: 'ON_LEAVE' })}>
                          On Leave
                        </button>
                      )}
                      {s.employmentStatus !== 'ACTIVE' && (
                        <button type="button" onClick={() => changeStatusMut.mutate({ id: s.id, status: 'ACTIVE' })}>
                          Activate
                        </button>
                      )}
                      <button
                        type="button"
                        className="danger"
                        onClick={() =>
                          setDeleteTarget({
                            title: `Delete Staff: ${s.fullName} (${s.employeeCode})`,
                            onConfirm: async () => {
                              await deleteStaffMut.mutateAsync(s.id);
                            },
                          })
                        }
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
                {staffList.length === 0 && (
                  <tr>
                    <td colSpan={8} className="muted">{t('common.noData')}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'assignments' && (
        <div className="stack">
          <form className="card stack" onSubmit={(e) => assignTeacherMut.mutate(e)}>
            <h3>Assign Teacher to Class Section & Subject</h3>
            <div className="form-grid">
              <label>
                Academic Session
                <select required value={sessionId} onChange={(e) => setSessionId(Number(e.target.value))}>
                  <option value={0}>-- Select Academic Session --</option>
                  {sessions.map((sess) => (
                    <option key={sess.id} value={sess.id}>
                      {sess.name} ({sess.code}){sess.current ? ' — CURRENT' : ''}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Teacher (Teaching Faculty)
                <select required value={staffId} onChange={(e) => setStaffId(Number(e.target.value))}>
                  <option value={0}>-- Select Teacher --</option>
                  {teachingStaff.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.fullName} — {designationMap.get(s.designationId)?.title ?? 'Teacher'} ({s.employeeCode})
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Class & Section
                <select required value={sectionId} onChange={(e) => setSectionId(Number(e.target.value))}>
                  <option value={0}>-- Select Class & Section --</option>
                  {sections.map((sec) => (
                    <option key={sec.id} value={sec.id}>
                      {formatSectionLabel(sec)}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Subject (Optional)
                <select value={subjectId} onChange={(e) => setSubjectId(e.target.value)}>
                  <option value="">-- Homeroom / All Subjects (Optional) --</option>
                  {subjects.map((sub) => (
                    <option key={sub.id} value={String(sub.id)}>
                      {sub.name} ({sub.code})
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="action-row">
              <label className="row" style={{ flexDirection: 'row', alignItems: 'center' }}>
                <input type="checkbox" checked={classTeacher} onChange={(e) => setClassTeacher(e.target.checked)} />
                {t('staff.assignments.classTeacher')}
              </label>
              <button type="submit" disabled={assignTeacherMut.isPending}>
                Assign Teacher
              </button>
            </div>
          </form>

          <div className="card">
            <table>
              <thead>
                <tr>
                  <th>Academic Session</th>
                  <th>Teacher</th>
                  <th>Class & Section</th>
                  <th>Subject</th>
                  <th>Role</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {assignments.map((a) => {
                  const sess = sessionMap.get(a.academicSessionId);
                  const teacher = staffMap.get(a.staffId);
                  const sec = sectionMap.get(a.sectionId);
                  const sub = a.subjectId ? subjectMap.get(a.subjectId) : null;
                  return (
                    <tr key={a.id}>
                      <td>{sess ? `${sess.name} (${sess.code})` : `Session #${a.academicSessionId}`}</td>
                      <td>
                        <strong>{teacher ? teacher.fullName : `Staff #${a.staffId}`}</strong>
                        {teacher ? <span className="muted"> ({teacher.employeeCode})</span> : null}
                      </td>
                      <td>{sec ? formatSectionLabel(sec) : `Section #${a.sectionId}`}</td>
                      <td>{sub ? `${sub.name} (${sub.code})` : 'Homeroom / General'}</td>
                      <td>
                        <span className={`badge ${a.classTeacher ? 'success' : ''}`}>
                          {a.classTeacher ? 'Class Teacher' : a.roleType}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="danger"
                          onClick={() =>
                            setDeleteTarget({
                              title: `Remove Assignment for ${teacher?.fullName ?? 'Teacher'}`,
                              onConfirm: async () => {
                                await deleteAssignmentMut.mutateAsync(a.id);
                              },
                            })
                          }
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  );
                })}
                {assignments.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted">{t('common.noData')}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'designations' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => saveDesigMut.mutate(e)}>
            <label>
              Designation Title
              <input
                required
                value={desigTitle}
                onChange={(e) => setDesigTitle(e.target.value)}
                placeholder="e.g. Senior Lecturer or Lab Assistant"
              />
            </label>
            <label>
              Category
              <select value={desigCat} onChange={(e) => setDesigCat(e.target.value)}>
                <option value="TEACHING">Teaching Faculty</option>
                <option value="NON_TEACHING">Administrative / Non-Teaching</option>
              </select>
            </label>
            <label>
              System Code (Auto-Generated)
              <input
                readOnly
                value={editingDesigCode ?? (desigTitle ? slugifyCode(desigTitle, 'DESIG') : 'AUTO')}
              />
            </label>
            <div className="action-row">
              <button type="submit" disabled={saveDesigMut.isPending}>
                {editingDesigCode ? 'Update Designation' : t('common.save')}
              </button>
              {editingDesigCode && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingDesigCode(null);
                    setDesigTitle('');
                  }}
                >
                  Cancel
                </button>
              )}
            </div>
          </form>

          <div className="card">
            <table>
              <thead>
                <tr>
                  <th>Title</th>
                  <th>System Code</th>
                  <th>Category</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {designations.map((d) => (
                  <tr key={d.id}>
                    <td><strong>{d.title}</strong></td>
                    <td><code>{d.code}</code></td>
                    <td><span className="badge">{d.category}</span></td>
                    <td className="action-row">
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => {
                          setEditingDesigCode(d.code);
                          setDesigTitle(d.title);
                          setDesigCat(d.category);
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="danger"
                        onClick={() =>
                          setDeleteTarget({
                            title: `Delete Designation: ${d.title}`,
                            onConfirm: async () => {
                              await deleteDesigMut.mutateAsync(d.id);
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
