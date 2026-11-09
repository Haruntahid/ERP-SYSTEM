import { useState, useEffect, FormEvent } from 'react';
import { useI18n } from '../../shared/i18n';
import { api, getErrorMessage } from '../../shared/api/client';
import { ConfirmDeleteModal, DeleteTarget } from '../../shared/components/ConfirmDeleteModal';

interface PageResponse<T> {
  content: T[];
}

function unwrapList<T>(data: PageResponse<T> | T[] | null | undefined): T[] {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.content)) return data.content;
  return [];
}

interface AcademicSession {
  id: number;
  name: string;
  current?: boolean;
}

interface SchoolClass {
  id: number;
  name: string;
  code: string;
}

interface Section {
  id: number;
  classId: number;
  name: string;
}

interface Student {
  id: number;
  studentCode: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  bloodGroup: string | null;
  phone: string | null;
  email: string | null;
  status: string;
}

interface Guardian {
  id: number;
  fullName: string;
  phone: string;
  email: string | null;
  relationship?: string;
}

interface Enrollment {
  id: number;
  studentId: number;
  academicSessionId: number;
  classId: number;
  sectionId: number;
  rollNumber: number;
  status: string;
}

interface StudentDetailResponse {
  student: Student;
  guardians: Guardian[];
  enrollments: Enrollment[];
}

interface AdmissionApplication {
  id: number;
  applicationNo: string;
  academicSessionId: number;
  targetClassId: number;
  applicantFirstName: string;
  applicantLastName: string;
  dateOfBirth: string;
  gender: string;
  guardianName: string;
  guardianPhone: string;
  guardianRelationship: string;
  status: string;
  enrolledStudentId: number | null;
}

type TabKey = 'students' | 'admissions' | 'guardians' | 'promotions';

export function StudentsPage() {
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState<TabKey>('students');
  const [students, setStudents] = useState<Student[]>([]);
  const [applications, setApplications] = useState<AdmissionApplication[]>([]);
  const [guardians, setGuardians] = useState<Guardian[]>([]);
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [sections, setSections] = useState<Section[]>([]);
  const [selectedStudentDetail, setSelectedStudentDetail] = useState<StudentDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  // Create Student Form
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('2013-05-15');
  const [gender, setGender] = useState<'MALE' | 'FEMALE' | 'OTHER'>('MALE');
  const [guardianFullName, setGuardianFullName] = useState('');
  const [guardianPhone, setGuardianPhone] = useState('');
  const [guardianRelation, setGuardianRelation] = useState('FATHER');
  const [enrollSessionId, setEnrollSessionId] = useState<number>(0);
  const [enrollClassId, setEnrollClassId] = useState<number>(0);
  const [enrollSectionId, setEnrollSectionId] = useState<number>(0);
  const [enrollRollNo, setEnrollRollNo] = useState<number>(1);

  // Admission Application Form
  const [appFirstName, setAppFirstName] = useState('');
  const [appLastName, setAppLastName] = useState('');
  const [appDob, setAppDob] = useState('2014-03-10');
  const [appGender, setAppGender] = useState<'MALE' | 'FEMALE' | 'OTHER'>('FEMALE');
  const [appGuardianName, setAppGuardianName] = useState('');
  const [appGuardianPhone, setAppGuardianPhone] = useState('');
  const [appGuardianRelation, setAppGuardianRelation] = useState('MOTHER');
  const [appSessionId, setAppSessionId] = useState<number>(0);
  const [appClassId, setAppClassId] = useState<number>(0);
  const [appEnrollSectionId, setAppEnrollSectionId] = useState<number>(0);
  const [appEnrollRollNo, setAppEnrollRollNo] = useState<number>(1);

  // Session Promotion Form
  const [sourceSessionId, setSourceSessionId] = useState<number>(0);
  const [sourceClassId, setSourceClassId] = useState<number>(0);
  const [targetSessionId, setTargetSessionId] = useState<number>(0);
  const [targetClassId, setTargetClassId] = useState<number>(0);
  const [targetSectionId, setTargetSectionId] = useState<number>(0);

  const loadData = async () => {
    try {
      setError(null);
      const [stuRes, appRes, guardRes, sessRes, clsRes, secRes] = await Promise.all([
        api.get<PageResponse<Student> | Student[]>('/api/v1/students?size=100'),
        api.get<PageResponse<AdmissionApplication> | AdmissionApplication[]>('/api/v1/admissions/applications?size=100'),
        api.get<PageResponse<Guardian> | Guardian[]>('/api/v1/guardians?size=100'),
        api.get<PageResponse<AcademicSession> | AcademicSession[]>('/api/v1/academic/sessions?size=100'),
        api.get<PageResponse<SchoolClass> | SchoolClass[]>('/api/v1/academic/classes?size=100'),
        api.get<PageResponse<Section> | Section[]>('/api/v1/academic/sections?size=100')
      ]);

      const studentList = unwrapList(stuRes.data);
      const appList = unwrapList(appRes.data);
      const guardianList = unwrapList(guardRes.data);
      const sessionList = unwrapList(sessRes.data);
      const classList = unwrapList(clsRes.data);
      const sectionList = unwrapList(secRes.data);

      setStudents(studentList);
      setApplications(appList);
      setGuardians(guardianList);
      setSessions(sessionList);
      setClasses(classList);
      setSections(sectionList);

      const firstSession = sessionList[0]?.id ?? 0;
      const firstClass = classList[0]?.id ?? 0;
      const firstSection = sectionList[0]?.id ?? 0;

      if (enrollSessionId === 0 && firstSession) setEnrollSessionId(firstSession);
      if (enrollClassId === 0 && firstClass) setEnrollClassId(firstClass);
      if (enrollSectionId === 0 && firstSection) setEnrollSectionId(firstSection);
      if (appSessionId === 0 && firstSession) setAppSessionId(firstSession);
      if (appClassId === 0 && firstClass) setAppClassId(firstClass);
      if (appEnrollSectionId === 0 && firstSection) setAppEnrollSectionId(firstSection);
      if (sourceSessionId === 0 && firstSession) setSourceSessionId(firstSession);
      if (sourceClassId === 0 && firstClass) setSourceClassId(firstClass);
      if (targetSessionId === 0 && firstSession) setTargetSessionId(firstSession);
      if (targetClassId === 0 && firstClass) setTargetClassId(firstClass);
      if (targetSectionId === 0 && firstSection) setTargetSectionId(firstSection);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  const handleCreateStudent = async (e: FormEvent) => {
    e.preventDefault();
    if (!enrollSessionId || !enrollClassId || !enrollSectionId) {
      setError('Please create an Academic Session, Class, and Section in Academic Setup first.');
      return;
    }
    try {
      setError(null);
      setMessage(null);
      await api.post('/api/v1/students', {
        studentCode: null,
        firstName,
        lastName,
        dateOfBirth,
        gender,
        bloodGroup: 'O+',
        email: null,
        phone: null,
        guardianName: guardianFullName,
        guardianPhone,
        guardianEmail: null,
        guardianRelationship: guardianRelation,
        academicSessionId: enrollSessionId,
        campusId: null,
        classId: enrollClassId,
        sectionId: enrollSectionId,
        rollNumber: enrollRollNo
      });
      setFirstName('');
      setLastName('');
      setGuardianFullName('');
      setGuardianPhone('');
      setEnrollRollNo((prev) => prev + 1);
      setMessage('Student registered and enrolled.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleViewStudent = async (studentId: number) => {
    try {
      const res = await api.get<StudentDetailResponse>(`/api/v1/students/${studentId}`);
      setSelectedStudentDetail(res.data);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleArchiveStudent = async (studentId: number) => {
    try {
      await api.delete(`/api/v1/students/${studentId}`);
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleSubmitApplication = async (e: FormEvent) => {
    e.preventDefault();
    if (!appSessionId || !appClassId) {
      setError('Please select a Session and Applying Class.');
      return;
    }
    try {
      setError(null);
      await api.post('/api/v1/admissions/applications', {
        academicSessionId: appSessionId,
        targetCampusId: null,
        targetClassId: appClassId,
        targetSectionId: appEnrollSectionId || null,
        applicantFirstName: appFirstName,
        applicantLastName: appLastName,
        dateOfBirth: appDob,
        gender: appGender,
        guardianName: appGuardianName,
        guardianPhone: appGuardianPhone,
        guardianEmail: null,
        guardianRelationship: appGuardianRelation,
        previousSchoolName: null
      });
      setAppFirstName('');
      setAppLastName('');
      setAppGuardianName('');
      setAppGuardianPhone('');
      setMessage('Admission application submitted.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleUpdateApplicationStatus = async (appId: number, status: string) => {
    try {
      await api.post(`/api/v1/admissions/applications/${appId}/status`, {
        status,
        reviewNotes: `Marked ${status}`
      });
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleEnrollApplicant = async (appId: number) => {
    if (!appEnrollSectionId) {
      setError('Please select a target section before enrolling.');
      return;
    }
    try {
      await api.post(`/api/v1/admissions/applications/${appId}/enroll`, {
        sectionId: appEnrollSectionId,
        rollNumber: appEnrollRollNo
      });
      setAppEnrollRollNo((prev) => prev + 1);
      setMessage('Applicant enrolled as active student.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handlePromoteBatch = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setError(null);
      setMessage(null);
      const res = await api.post<Enrollment[]>('/api/v1/enrollments/promotions/commit', {
        sourceSessionId,
        sourceClassId,
        targetSessionId,
        targetClassId,
        targetSectionId
      });
      setMessage(`Successfully promoted ${res.data.length} student(s) to target session.`);
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <section className="stack">
      <ConfirmDeleteModal target={deleteTarget} onClose={() => setDeleteTarget(null)} />
      <div>
        <h2>{t('students.title')}</h2>
        <p className="muted">{t('students.subtitle')}</p>
      </div>

      {error && <p className="error-text">{error}</p>}
      {message && <p className="badge active">{message}</p>}

      <div className="tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'students' ? 'active' : ''}`}
          onClick={() => setActiveTab('students')}
        >
          {t('nav.students')} ({students.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'admissions' ? 'active' : ''}`}
          onClick={() => setActiveTab('admissions')}
        >
          {t('students.admissions')} ({applications.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'guardians' ? 'active' : ''}`}
          onClick={() => setActiveTab('guardians')}
        >
          {t('students.guardians')} ({guardians.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'promotions' ? 'active' : ''}`}
          onClick={() => setActiveTab('promotions')}
        >
          {t('students.promotions')}
        </button>
      </div>

      {activeTab === 'students' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleCreateStudent}>
            <div>
              <label>First Name</label>
              <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
            </div>
            <div>
              <label>Last Name</label>
              <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
            </div>
            <div>
              <label>Date of Birth</label>
              <input type="date" value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} required />
            </div>
            <div>
              <label>Gender</label>
              <select
                value={gender}
                onChange={(e) => setGender(e.target.value as 'MALE' | 'FEMALE' | 'OTHER')}
              >
                <option value="MALE">MALE</option>
                <option value="FEMALE">FEMALE</option>
                <option value="OTHER">OTHER</option>
              </select>
            </div>
            <div>
              <label>Guardian Name</label>
              <input value={guardianFullName} onChange={(e) => setGuardianFullName(e.target.value)} required />
            </div>
            <div>
              <label>Guardian Phone</label>
              <input value={guardianPhone} onChange={(e) => setGuardianPhone(e.target.value)} required />
            </div>
            <div>
              <label>Guardian Relation</label>
              <select value={guardianRelation} onChange={(e) => setGuardianRelation(e.target.value)}>
                <option value="FATHER">FATHER</option>
                <option value="MOTHER">MOTHER</option>
                <option value="GUARDIAN">GUARDIAN</option>
              </select>
            </div>
            <div>
              <label>Academic Session</label>
              <select value={enrollSessionId} onChange={(e) => setEnrollSessionId(Number(e.target.value))} required>
                <option value={0}>-- Select Session --</option>
                {sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Class</label>
              <select value={enrollClassId} onChange={(e) => setEnrollClassId(Number(e.target.value))} required>
                <option value={0}>-- Select Class --</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Section</label>
              <select value={enrollSectionId} onChange={(e) => setEnrollSectionId(Number(e.target.value))} required>
                <option value={0}>-- Select Section --</option>
                {sections.map((sec) => (
                  <option key={sec.id} value={sec.id}>
                    {sec.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Roll No</label>
              <input
                type="number"
                value={enrollRollNo}
                onChange={(e) => setEnrollRollNo(Number(e.target.value))}
                min={1}
                required
              />
            </div>
            <button type="submit">{t('students.addStudent')}</button>
          </form>

          {selectedStudentDetail && (
            <div className="card stack">
              <div className="page-header">
                <h3>
                  {selectedStudentDetail.student.firstName} {selectedStudentDetail.student.lastName} (
                  {selectedStudentDetail.student.studentCode})
                </h3>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => setSelectedStudentDetail(null)}
                >
                  Close
                </button>
              </div>
              <p className="muted">
                Guardians:{' '}
                {(selectedStudentDetail.guardians ?? [])
                  .map((g) => `${g.fullName} (${g.relationship ?? 'GUARDIAN'} - ${g.phone})`)
                  .join(', ') || 'None'}
              </p>
              <p className="muted">
                Enrollments:{' '}
                {(selectedStudentDetail.enrollments ?? [])
                  .map(
                    (en) =>
                      `Enrollment #${en.id} (Session #${en.academicSessionId} / Class #${en.classId} / Roll #${en.rollNumber} - ${en.status})`
                  )
                  .join(' | ') || 'None'}
              </p>
            </div>
          )}

          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>{t('students.studentCode')}</th>
                  <th>Name</th>
                  <th>Gender</th>
                  <th>DOB</th>
                  <th>Status</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {students.map((s) => (
                  <tr key={s.id}>
                    <td>#{s.id}</td>
                    <td>
                      <code>{s.studentCode}</code>
                    </td>
                    <td>
                      {s.firstName} {s.lastName}
                    </td>
                    <td>{s.gender}</td>
                    <td>{s.dateOfBirth}</td>
                    <td>
                      <span className="badge active">{s.status}</span>
                    </td>
                    <td className="action-row">
                      <button type="button" className="secondary" onClick={() => handleViewStudent(s.id)}>
                        Profile
                      </button>
                      {s.status !== 'ARCHIVED' && (
                        <button
                          type="button"
                          className="danger"
                          onClick={() =>
                            setDeleteTarget({
                              title: `Delete / Archive Student: ${s.firstName} ${s.lastName} (${s.studentCode})`,
                              onConfirm: async () => {
                                await handleArchiveStudent(s.id);
                              },
                            })
                          }
                        >
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {students.length === 0 && (
                  <tr>
                    <td colSpan={7} className="muted">
                      {t('common.noData')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'admissions' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleSubmitApplication}>
            <div>
              <label>Session</label>
              <select value={appSessionId} onChange={(e) => setAppSessionId(Number(e.target.value))} required>
                <option value={0}>-- Select Session --</option>
                {sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Applying Class</label>
              <select value={appClassId} onChange={(e) => setAppClassId(Number(e.target.value))} required>
                <option value={0}>-- Select Class --</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Applicant First Name</label>
              <input value={appFirstName} onChange={(e) => setAppFirstName(e.target.value)} required />
            </div>
            <div>
              <label>Applicant Last Name</label>
              <input value={appLastName} onChange={(e) => setAppLastName(e.target.value)} required />
            </div>
            <div>
              <label>Date of Birth</label>
              <input type="date" value={appDob} onChange={(e) => setAppDob(e.target.value)} required />
            </div>
            <div>
              <label>Gender</label>
              <select
                value={appGender}
                onChange={(e) => setAppGender(e.target.value as 'MALE' | 'FEMALE' | 'OTHER')}
              >
                <option value="MALE">MALE</option>
                <option value="FEMALE">FEMALE</option>
                <option value="OTHER">OTHER</option>
              </select>
            </div>
            <div>
              <label>Guardian Name</label>
              <input value={appGuardianName} onChange={(e) => setAppGuardianName(e.target.value)} required />
            </div>
            <div>
              <label>Guardian Phone</label>
              <input value={appGuardianPhone} onChange={(e) => setAppGuardianPhone(e.target.value)} required />
            </div>
            <div>
              <label>Relation</label>
              <select value={appGuardianRelation} onChange={(e) => setAppGuardianRelation(e.target.value)}>
                <option value="FATHER">FATHER</option>
                <option value="MOTHER">MOTHER</option>
                <option value="GUARDIAN">GUARDIAN</option>
              </select>
            </div>
            <button type="submit">Submit Application</button>
          </form>

          <div className="card stack">
            <div className="form-grid">
              <div>
                <label>Target Section for Enrollment</label>
                <select
                  value={appEnrollSectionId}
                  onChange={(e) => setAppEnrollSectionId(Number(e.target.value))}
                >
                  <option value={0}>-- Select Section --</option>
                  {sections.map((sec) => (
                    <option key={sec.id} value={sec.id}>
                      {sec.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label>Roll Number for Enrollment</label>
                <input
                  type="number"
                  min={1}
                  value={appEnrollRollNo}
                  onChange={(e) => setAppEnrollRollNo(Number(e.target.value))}
                />
              </div>
            </div>

            <table className="data-table">
              <thead>
                <tr>
                  <th>App No</th>
                  <th>Applicant</th>
                  <th>Guardian</th>
                  <th>Status</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((app) => (
                  <tr key={app.id}>
                    <td>
                      <code>{app.applicationNo}</code>
                    </td>
                    <td>
                      {app.applicantFirstName} {app.applicantLastName}
                    </td>
                    <td>
                      {app.guardianName} ({app.guardianPhone})
                    </td>
                    <td>
                      <span className="badge active">{app.status}</span>
                    </td>
                    <td className="action-row">
                      {app.status === 'SUBMITTED' && (
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => handleUpdateApplicationStatus(app.id, 'APPROVED')}
                        >
                          Approve
                        </button>
                      )}
                      {app.status === 'APPROVED' && (
                        <button type="button" onClick={() => handleEnrollApplicant(app.id)}>
                          {t('students.enroll')}
                        </button>
                      )}
                      {app.status !== 'ENROLLED' && app.status !== 'REJECTED' && (
                        <button
                          type="button"
                          className="danger"
                          onClick={() => handleUpdateApplicationStatus(app.id, 'REJECTED')}
                        >
                          Reject
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {applications.length === 0 && (
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

      {activeTab === 'guardians' && (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Full Name</th>
                <th>Phone</th>
                <th>Email</th>
              </tr>
            </thead>
            <tbody>
              {guardians.map((g) => (
                <tr key={g.id}>
                  <td>{g.id}</td>
                  <td>{g.fullName}</td>
                  <td>{g.phone}</td>
                  <td>{g.email ?? '-'}</td>
                </tr>
              ))}
              {guardians.length === 0 && (
                <tr>
                  <td colSpan={4} className="muted">
                    {t('common.noData')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {activeTab === 'promotions' && (
        <form className="card form-grid" onSubmit={handlePromoteBatch}>
          <div>
            <label>Source Session</label>
            <select value={sourceSessionId} onChange={(e) => setSourceSessionId(Number(e.target.value))}>
              {sessions.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>Source Class</label>
            <select value={sourceClassId} onChange={(e) => setSourceClassId(Number(e.target.value))}>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>Target Session</label>
            <select value={targetSessionId} onChange={(e) => setTargetSessionId(Number(e.target.value))}>
              {sessions.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>Target Class</label>
            <select value={targetClassId} onChange={(e) => setTargetClassId(Number(e.target.value))}>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>Target Section</label>
            <select value={targetSectionId} onChange={(e) => setTargetSectionId(Number(e.target.value))}>
              {sections.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  {sec.name}
                </option>
              ))}
            </select>
          </div>
          <button type="submit">Commit Batch Promotion</button>
        </form>
      )}
    </section>
  );
}
