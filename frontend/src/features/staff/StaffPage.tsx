import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getErrorMessage } from '../../shared/api/client';
import { useI18n } from '../../shared/i18n';

interface PageResponse<T> {
  content: T[];
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

export function StaffPage() {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'directory' | 'assignments' | 'designations'>('directory');
  const [error, setError] = useState<string | null>(null);

  // Add staff form
  const [employeeCode, setEmployeeCode] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [designationId, setDesignationId] = useState(1);
  const [staffType, setStaffType] = useState('TEACHING');
  const [joinDate, setJoinDate] = useState('2026-01-01');

  // Assignment form
  const [sessionId, setSessionId] = useState(1);
  const [staffId, setStaffId] = useState(1);
  const [sectionId, setSectionId] = useState(1);
  const [subjectId, setSubjectId] = useState<string>('');
  const [classTeacher, setClassTeacher] = useState(false);

  // Designation form
  const [desigCode, setDesigCode] = useState('');
  const [desigTitle, setDesigTitle] = useState('');
  const [desigCat, setDesigCat] = useState('TEACHING');

  const staffQuery = useQuery({
    queryKey: ['staff', 'list'],
    queryFn: async () => (await api.get<PageResponse<StaffMember>>('/api/v1/staff')).data.content,
  });

  const designationsQuery = useQuery({
    queryKey: ['staff', 'designations'],
    queryFn: async () => (await api.get<PageResponse<Designation>>('/api/v1/staff/designations')).data.content,
  });

  const assignmentsQuery = useQuery({
    queryKey: ['staff', 'assignments'],
    queryFn: async () => (await api.get<TeacherAssignment[]>('/api/v1/staff/assignments')).data,
  });

  const createStaffMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/staff', {
        campusId: 1,
        departmentId: 1,
        designationId: Number(designationId),
        employeeCode: employeeCode || undefined,
        firstName,
        lastName,
        email: email || undefined,
        phone,
        staffType,
        joinDate,
      });
    },
    onSuccess: () => {
      setEmployeeCode('');
      setFirstName('');
      setLastName('');
      setEmail('');
      setPhone('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['staff', 'list'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const changeStatusMut = useMutation({
    mutationFn: async ({ id, status }: { id: number; status: string }) =>
      api.patch(`/api/v1/staff/${id}/status`, { status }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['staff', 'list'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const assignTeacherMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
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
      qc.invalidateQueries({ queryKey: ['staff', 'assignments'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  const deleteAssignmentMut = useMutation({
    mutationFn: async (id: number) => api.delete(`/api/v1/staff/assignments/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['staff', 'assignments'] }),
    onError: (err) => setError(getErrorMessage(err)),
  });

  const saveDesigMut = useMutation({
    mutationFn: async (e: FormEvent) => {
      e.preventDefault();
      return api.post('/api/v1/staff/designations', {
        code: desigCode,
        title: desigTitle,
        category: desigCat,
      });
    },
    onSuccess: () => {
      setDesigCode('');
      setDesigTitle('');
      setError(null);
      qc.invalidateQueries({ queryKey: ['staff', 'designations'] });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  return (
    <div className="stack">
      <h1>{t('staff.nav.title')}</h1>
      {error && <p className="error">{error}</p>}

      <div className="tabs">
        <button type="button" className={`tab-btn ${tab === 'directory' ? 'active' : ''}`} onClick={() => setTab('directory')}>
          {t('staff.directory.title')}
        </button>
        <button type="button" className={`tab-btn ${tab === 'assignments' ? 'active' : ''}`} onClick={() => setTab('assignments')}>
          {t('staff.assignments.title')}
        </button>
        <button type="button" className={`tab-btn ${tab === 'designations' ? 'active' : ''}`} onClick={() => setTab('designations')}>
          {t('staff.field.designation')}
        </button>
      </div>

      {tab === 'directory' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => createStaffMut.mutate(e)}>
            <label>
              {t('staff.field.employeeCode')}
              <input value={employeeCode} onChange={(e) => setEmployeeCode(e.target.value)} placeholder="EMP-2026-0001" />
            </label>
            <label>
              First Name
              <input required value={firstName} onChange={(e) => setFirstName(e.target.value)} placeholder="Rahim" />
            </label>
            <label>
              Last Name
              <input required value={lastName} onChange={(e) => setLastName(e.target.value)} placeholder="Uddin" />
            </label>
            <label>
              Phone
              <input required value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="01711000000" />
            </label>
            <label>
              Email
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="rahim@school.edu" />
            </label>
            <label>
              {t('staff.field.designation')}
              <select value={designationId} onChange={(e) => setDesignationId(Number(e.target.value))}>
                {(designationsQuery.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>{d.title}</option>
                ))}
              </select>
            </label>
            <label>
              Staff Type
              <select value={staffType} onChange={(e) => setStaffType(e.target.value)}>
                <option value="TEACHING">TEACHING</option>
                <option value="NON_TEACHING">NON_TEACHING</option>
              </select>
            </label>
            <label>
              Join Date
              <input type="date" required value={joinDate} onChange={(e) => setJoinDate(e.target.value)} />
            </label>
            <button type="submit" disabled={createStaffMut.isPending}>{t('staff.action.add')}</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>{t('staff.field.employeeCode')}</th>
                <th>Full Name</th>
                <th>Type</th>
                <th>Phone / Email</th>
                <th>Join Date</th>
                <th>Status</th>
                <th>Change Status</th>
              </tr>
            </thead>
            <tbody>
              {(staffQuery.data ?? []).map((s) => (
                <tr key={s.id}>
                  <td>{s.id}</td>
                  <td><strong>{s.employeeCode}</strong></td>
                  <td>{s.fullName}</td>
                  <td><span className="badge">{s.staffType}</span></td>
                  <td>{s.phone}{s.email ? ` • ${s.email}` : ''}</td>
                  <td>{s.joinDate}</td>
                  <td>
                    <span className={`badge ${s.employmentStatus === 'ACTIVE' ? 'success' : 'warning'}`}>
                      {t(`staff.status.${s.employmentStatus}`)}
                    </span>
                  </td>
                  <td className="row">
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
                    {s.employmentStatus !== 'RESIGNED' && (
                      <button type="button" className="danger" onClick={() => changeStatusMut.mutate({ id: s.id, status: 'RESIGNED' })}>
                        Resign
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'assignments' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => assignTeacherMut.mutate(e)}>
            <label>
              Academic Session ID
              <input type="number" min={1} required value={sessionId} onChange={(e) => setSessionId(Number(e.target.value))} />
            </label>
            <label>
              Teacher (Staff ID)
              <select value={staffId} onChange={(e) => setStaffId(Number(e.target.value))}>
                {(staffQuery.data ?? []).filter((s) => s.staffType === 'TEACHING').map((s) => (
                  <option key={s.id} value={s.id}>#{s.id} {s.fullName} ({s.employeeCode})</option>
                ))}
              </select>
            </label>
            <label>
              Section ID
              <input type="number" min={1} required value={sectionId} onChange={(e) => setSectionId(Number(e.target.value))} />
            </label>
            <label>
              Subject ID (Optional)
              <input type="number" min={1} value={subjectId} onChange={(e) => setSubjectId(e.target.value)} placeholder="1" />
            </label>
            <label className="row" style={{ flexDirection: 'row', alignItems: 'center' }}>
              <input type="checkbox" checked={classTeacher} onChange={(e) => setClassTeacher(e.target.checked)} />
              {t('staff.assignments.classTeacher')}
            </label>
            <button type="submit" disabled={assignTeacherMut.isPending}>Assign Teacher</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Session ID</th>
                <th>Staff ID</th>
                <th>Section ID</th>
                <th>Subject ID</th>
                <th>Role</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {(assignmentsQuery.data ?? []).map((a) => (
                <tr key={a.id}>
                  <td>{a.id}</td>
                  <td>{a.academicSessionId}</td>
                  <td>#{a.staffId}</td>
                  <td>Section #{a.sectionId}</td>
                  <td>{a.subjectId ? `Subject #${a.subjectId}` : 'Homeroom'}</td>
                  <td>
                    <span className={`badge ${a.classTeacher ? 'success' : ''}`}>
                      {a.classTeacher ? 'Class Teacher' : a.roleType}
                    </span>
                  </td>
                  <td>
                    <button type="button" className="danger" onClick={() => deleteAssignmentMut.mutate(a.id)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'designations' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={(e) => saveDesigMut.mutate(e)}>
            <label>
              Code
              <input required value={desigCode} onChange={(e) => setDesigCode(e.target.value)} placeholder="LAB_ASST" />
            </label>
            <label>
              Title
              <input required value={desigTitle} onChange={(e) => setDesigTitle(e.target.value)} placeholder="Lab Assistant" />
            </label>
            <label>
              Category
              <select value={desigCat} onChange={(e) => setDesigCat(e.target.value)}>
                <option value="TEACHING">TEACHING</option>
                <option value="NON_TEACHING">NON_TEACHING</option>
              </select>
            </label>
            <button type="submit" disabled={saveDesigMut.isPending}>{t('common.save')}</button>
          </form>

          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Code</th>
                <th>Title</th>
                <th>Category</th>
              </tr>
            </thead>
            <tbody>
              {(designationsQuery.data ?? []).map((d) => (
                <tr key={d.id}>
                  <td>{d.id}</td>
                  <td><strong>{d.code}</strong></td>
                  <td>{d.title}</td>
                  <td><span className="badge">{d.category}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

