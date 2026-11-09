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

interface AcademicSession {
  id: number;
  name: string;
}

interface SchoolClass {
  id: number;
  name: string;
}

interface Section {
  id: number;
  classId: number;
  name: string;
}

interface StudentLookup {
  id: number;
  studentCode: string;
  firstName: string;
  lastName: string;
}

interface StaffLookup {
  id: number;
  employeeCode: string;
  fullName: string;
}

type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED' | 'HALF_DAY';

interface StudentRollCallRow {
  enrollmentId: number;
  studentId: number;
  studentCode: string;
  studentName: string;
  rollNumber: number;
  status: AttendanceStatus;
  remarks: string | null;
  leaveApplicationId: number | null;
}

interface StudentAttendanceSheetResponse {
  attendanceSessionId: number | null;
  academicSessionId: number;
  classId: number;
  sectionId: number;
  attendanceDate: string;
  sessionStatus: string;
  editable: boolean;
  rows: StudentRollCallRow[];
}

interface StaffAttendanceSheetRow {
  staffId: number;
  employeeCode: string;
  fullName: string;
  staffType: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'ON_LEAVE' | 'HALF_DAY';
  checkInTime: string | null;
  checkOutTime: string | null;
  remarks: string | null;
}

interface LeaveApplication {
  id: number;
  applicantType: 'STUDENT' | 'STAFF';
  studentEnrollmentId: number | null;
  staffId: number | null;
  leaveType: 'SICK' | 'CASUAL' | 'EMERGENCY' | 'OTHER';
  startDate: string;
  endDate: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
}

type TabKey = 'students' | 'staff' | 'leaves';

export function AttendancePage() {
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState<TabKey>('students');
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [sections, setSections] = useState<Section[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number>(0);
  const [selectedClassId, setSelectedClassId] = useState<number>(0);
  const [selectedSectionId, setSelectedSectionId] = useState<number>(0);
  const [attendanceDate, setAttendanceDate] = useState<string>(new Date().toISOString().slice(0, 10));

  const [studentSheet, setStudentSheet] = useState<StudentAttendanceSheetResponse | null>(null);
  const [studentRows, setStudentRows] = useState<StudentRollCallRow[]>([]);
  const [editReason, setEditReason] = useState('');

  const [staffRows, setStaffRows] = useState<StaffAttendanceSheetRow[]>([]);
  const [leaves, setLeaves] = useState<LeaveApplication[]>([]);
  const [studentList, setStudentList] = useState<StudentLookup[]>([]);
  const [staffList, setStaffList] = useState<StaffLookup[]>([]);

  // Leave form
  const [leaveApplicantType, setLeaveApplicantType] = useState<'STUDENT' | 'STAFF'>('STUDENT');
  const [leaveTargetId, setLeaveTargetId] = useState<number>(1);
  const [leaveType, setLeaveType] = useState<'SICK' | 'CASUAL' | 'EMERGENCY' | 'OTHER'>('SICK');
  const [leaveStart, setLeaveStart] = useState<string>(new Date().toISOString().slice(0, 10));
  const [leaveEnd, setLeaveEnd] = useState<string>(new Date().toISOString().slice(0, 10));
  const [leaveReason, setLeaveReason] = useState('');

  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const loadLookups = async () => {
    try {
      const [sessRes, clsRes, secRes, leavesRes, stuRes, stfRes] = await Promise.all([
        api.get<PageResponse<AcademicSession> | AcademicSession[]>('/api/v1/academic/sessions?size=100'),
        api.get<PageResponse<SchoolClass> | SchoolClass[]>('/api/v1/academic/classes?size=100'),
        api.get<PageResponse<Section> | Section[]>('/api/v1/academic/sections?size=100'),
        api.get<PageResponse<LeaveApplication> | LeaveApplication[]>('/api/v1/attendance/leaves?size=100'),
        api.get<PageResponse<StudentLookup> | StudentLookup[]>('/api/v1/students?size=100'),
        api.get<PageResponse<StaffLookup> | StaffLookup[]>('/api/v1/staff?size=100')
      ]);
      const sessionList = unwrapList(sessRes.data);
      const classList = unwrapList(clsRes.data);
      const sectionList = unwrapList(secRes.data);
      const leaveList = unwrapList(leavesRes.data);
      const stuItems = unwrapList(stuRes.data);
      const stfItems = unwrapList(stfRes.data);

      setSessions(sessionList);
      setClasses(classList);
      setSections(sectionList);
      setLeaves(leaveList);
      setStudentList(stuItems);
      setStaffList(stfItems);

      if (sessionList[0] && selectedSessionId === 0) setSelectedSessionId(sessionList[0].id);
      if (classList[0] && selectedClassId === 0) setSelectedClassId(classList[0].id);
      if (sectionList[0] && selectedSectionId === 0) setSelectedSectionId(sectionList[0].id);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  useEffect(() => {
    void loadLookups();
  }, []);

  const loadStudentSheet = async () => {
    if (!selectedSessionId || !selectedSectionId) return;
    try {
      setError(null);
      const res = await api.get<StudentAttendanceSheetResponse>(
        `/api/v1/attendance/students/sheet?sessionId=${selectedSessionId}&classId=${selectedClassId}&sectionId=${selectedSectionId}&date=${attendanceDate}`
      );
      setStudentSheet(res.data);
      setStudentRows(res.data.rows ?? []);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const loadStaffSheet = async () => {
    try {
      setError(null);
      const res = await api.get<StaffAttendanceSheetRow[]>(
        `/api/v1/attendance/staff/sheet?date=${attendanceDate}`
      );
      setStaffRows(unwrapList(res.data));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleMarkAllPresent = () => {
    setStudentRows((prev) => prev.map((r) => ({ ...r, status: 'PRESENT' })));
  };

  const handleStudentStatusChange = (studentId: number, status: AttendanceStatus) => {
    setStudentRows((prev) =>
      prev.map((r) => (r.studentId === studentId ? { ...r, status } : r))
    );
  };

  const handleSubmitStudentAttendance = async () => {
    if (!selectedSessionId || !selectedClassId || !selectedSectionId) return;
    try {
      setError(null);
      setMessage(null);
      const marks = studentRows.map((r) => ({
        enrollmentId: r.enrollmentId,
        studentId: r.studentId,
        status: r.status,
        remarks: r.remarks
      }));

      if (studentSheet?.attendanceSessionId) {
        await api.put(`/api/v1/attendance/students/sessions/${studentSheet.attendanceSessionId}`, {
          editReason: editReason || 'Updated roll-call records',
          marks
        });
        setMessage('Attendance sheet amended.');
      } else {
        await api.post('/api/v1/attendance/students/sessions', {
          academicSessionId: selectedSessionId,
          campusId: null,
          classId: selectedClassId,
          sectionId: selectedSectionId,
          attendanceDate,
          marks,
          editReason: editReason || null
        });
        setMessage('Attendance sheet submitted and absence alerts queued.');
      }
      await loadStudentSheet();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleUnlockSession = async () => {
    if (!studentSheet?.attendanceSessionId) return;
    try {
      await api.post(`/api/v1/attendance/students/sessions/${studentSheet.attendanceSessionId}/unlock`, {
        overrideReason: editReason || 'Admin unlocked attendance session'
      });
      setMessage('Attendance session unlocked for editing.');
      await loadStudentSheet();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleSaveStaffAttendance = async () => {
    try {
      setError(null);
      await api.post('/api/v1/attendance/staff/sheet', {
        attendanceDate,
        rows: staffRows
      });
      setMessage('Staff attendance saved.');
      await loadStaffSheet();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleCreateLeave = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setError(null);
      await api.post('/api/v1/attendance/leaves', {
        applicantType: leaveApplicantType,
        studentEnrollmentId: leaveApplicantType === 'STUDENT' ? leaveTargetId : null,
        staffId: leaveApplicantType === 'STAFF' ? leaveTargetId : null,
        leaveType,
        startDate: leaveStart,
        endDate: leaveEnd,
        reason: leaveReason
      });
      setLeaveReason('');
      setMessage('Leave application submitted.');
      await loadLookups();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleReviewLeave = async (id: number, status: 'APPROVED' | 'REJECTED') => {
    try {
      await api.post(`/api/v1/attendance/leaves/${id}/review`, {
        status,
        reviewRemarks: `Reviewed as ${status}`
      });
      await loadLookups();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <section className="stack">
      <div>
        <h2>{t('attendance.title')}</h2>
        <p className="muted">{t('attendance.subtitle')}</p>
      </div>

      {error && <p className="error-text">{error}</p>}
      {message && <p className="badge active">{message}</p>}

      <div className="tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'students' ? 'active' : ''}`}
          onClick={() => setActiveTab('students')}
        >
          {t('attendance.studentSheet')}
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'staff' ? 'active' : ''}`}
          onClick={() => {
            setActiveTab('staff');
            void loadStaffSheet();
          }}
        >
          {t('attendance.staffSheet')}
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'leaves' ? 'active' : ''}`}
          onClick={() => setActiveTab('leaves')}
        >
          {t('attendance.leaves')} ({leaves.length})
        </button>
      </div>

      {activeTab === 'students' && (
        <div className="stack">
          <div className="card form-grid">
            <div>
              <label>Academic Session</label>
              <select
                value={selectedSessionId}
                onChange={(e) => setSelectedSessionId(Number(e.target.value))}
              >
                {sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Class</label>
              <select
                value={selectedClassId}
                onChange={(e) => setSelectedClassId(Number(e.target.value))}
              >
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Section</label>
              <select
                value={selectedSectionId}
                onChange={(e) => setSelectedSectionId(Number(e.target.value))}
              >
                {sections.map((sec) => (
                  <option key={sec.id} value={sec.id}>
                    {sec.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Date</label>
              <input
                type="date"
                value={attendanceDate}
                onChange={(e) => setAttendanceDate(e.target.value)}
              />
            </div>
            <button type="button" onClick={loadStudentSheet}>
              Load Roll-Call Sheet
            </button>
            <button type="button" className="secondary" onClick={handleMarkAllPresent}>
              {t('attendance.markAllPresent')}
            </button>
          </div>

          <div className="card stack">
            {studentSheet?.attendanceSessionId && (
              <div className="action-row">
                <span className="badge active">
                  Session Status: {studentSheet.sessionStatus}
                </span>
                <input
                  placeholder="Amendment reason (required when updating)"
                  value={editReason}
                  onChange={(e) => setEditReason(e.target.value)}
                />
                {!studentSheet.editable && (
                  <button type="button" className="secondary" onClick={handleUnlockSession}>
                    Unlock Session
                  </button>
                )}
              </div>
            )}

            <table className="data-table">
              <thead>
                <tr>
                  <th>Roll</th>
                  <th>Code</th>
                  <th>Student Name</th>
                  <th>Approved Leave</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {studentRows.map((row) => (
                  <tr key={row.studentId}>
                    <td>{row.rollNumber}</td>
                    <td>
                      <code>{row.studentCode}</code>
                    </td>
                    <td>{row.studentName}</td>
                    <td>{row.leaveApplicationId ? `Yes (#${row.leaveApplicationId})` : 'No'}</td>
                    <td>
                      <select
                        value={row.status}
                        onChange={(e) =>
                          handleStudentStatusChange(row.studentId, e.target.value as AttendanceStatus)
                        }
                      >
                        <option value="PRESENT">PRESENT</option>
                        <option value="ABSENT">ABSENT</option>
                        <option value="LATE">LATE</option>
                        <option value="EXCUSED">EXCUSED</option>
                        <option value="HALF_DAY">HALF_DAY</option>
                      </select>
                    </td>
                  </tr>
                ))}
                {studentRows.length === 0 && (
                  <tr>
                    <td colSpan={5} className="muted">
                      Click &quot;Load Roll-Call Sheet&quot; to load enrolled students for this section.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            {studentRows.length > 0 && (
              <div>
                <button type="button" onClick={handleSubmitStudentAttendance}>
                  {t('attendance.submitSheet')}
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === 'staff' && (
        <div className="card stack">
          <div className="action-row">
            <input
              type="date"
              value={attendanceDate}
              onChange={(e) => setAttendanceDate(e.target.value)}
            />
            <button type="button" className="secondary" onClick={loadStaffSheet}>
              Load Staff Sheet
            </button>
            <button type="button" onClick={handleSaveStaffAttendance}>
              Save Staff Attendance
            </button>
          </div>
          <table className="data-table">
            <thead>
              <tr>
                <th>Employee Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {staffRows.map((row) => (
                <tr key={row.staffId}>
                  <td>
                    <code>{row.employeeCode}</code>
                  </td>
                  <td>{row.fullName}</td>
                  <td>{row.staffType}</td>
                  <td>
                    <select
                      value={row.status}
                      onChange={(e) =>
                        setStaffRows((prev) =>
                          prev.map((r) =>
                            r.staffId === row.staffId
                              ? { ...r, status: e.target.value as StaffAttendanceSheetRow['status'] }
                              : r
                          )
                        )
                      }
                    >
                      <option value="PRESENT">PRESENT</option>
                      <option value="ABSENT">ABSENT</option>
                      <option value="LATE">LATE</option>
                      <option value="ON_LEAVE">ON_LEAVE</option>
                      <option value="HALF_DAY">HALF_DAY</option>
                    </select>
                  </td>
                </tr>
              ))}
              {staffRows.length === 0 && (
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

      {activeTab === 'leaves' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleCreateLeave}>
            <div>
              <label>Applicant Type</label>
              <select
                value={leaveApplicantType}
                onChange={(e) => setLeaveApplicantType(e.target.value as 'STUDENT' | 'STAFF')}
              >
                <option value="STUDENT">STUDENT</option>
                <option value="STAFF">STAFF</option>
              </select>
            </div>
            <div>
              <label>{leaveApplicantType === 'STUDENT' ? 'Select Student' : 'Select Staff Member'}</label>
              <select
                value={leaveTargetId}
                onChange={(e) => setLeaveTargetId(Number(e.target.value))}
                required
              >
                {leaveApplicantType === 'STUDENT'
                  ? studentList.map((stu) => (
                      <option key={stu.id} value={stu.id}>
                        {stu.firstName} {stu.lastName} ({stu.studentCode})
                      </option>
                    ))
                  : staffList.map((stf) => (
                      <option key={stf.id} value={stf.id}>
                        {stf.fullName} ({stf.employeeCode})
                      </option>
                    ))}
              </select>
            </div>
            <div>
              <label>Leave Type</label>
              <select
                value={leaveType}
                onChange={(e) =>
                  setLeaveType(e.target.value as 'SICK' | 'CASUAL' | 'EMERGENCY' | 'OTHER')
                }
              >
                <option value="SICK">SICK</option>
                <option value="CASUAL">CASUAL</option>
                <option value="EMERGENCY">EMERGENCY</option>
                <option value="OTHER">OTHER</option>
              </select>
            </div>
            <div>
              <label>Start Date</label>
              <input
                type="date"
                value={leaveStart}
                onChange={(e) => setLeaveStart(e.target.value)}
                required
              />
            </div>
            <div>
              <label>End Date</label>
              <input
                type="date"
                value={leaveEnd}
                onChange={(e) => setLeaveEnd(e.target.value)}
                required
              />
            </div>
            <div>
              <label>Reason</label>
              <input
                value={leaveReason}
                onChange={(e) => setLeaveReason(e.target.value)}
                required
              />
            </div>
            <button type="submit">Submit Leave Request</button>
          </form>

          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Applicant</th>
                  <th>Leave Type</th>
                  <th>Dates</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {leaves.map((lv) => (
                  <tr key={lv.id}>
                    <td>
                      {lv.applicantType} #{lv.studentEnrollmentId ?? lv.staffId}
                    </td>
                    <td>{lv.leaveType}</td>
                    <td>
                      {lv.startDate} &rarr; {lv.endDate}
                    </td>
                    <td>{lv.reason}</td>
                    <td>
                      <span className="badge active">{lv.status}</span>
                    </td>
                    <td className="action-row">
                      {lv.status === 'PENDING' && (
                        <>
                          <button
                            type="button"
                            onClick={() => handleReviewLeave(lv.id, 'APPROVED')}
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            className="danger"
                            onClick={() => handleReviewLeave(lv.id, 'REJECTED')}
                          >
                            Reject
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
                {leaves.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted">
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
