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

interface FeeHead {
  id: number;
  name: string;
  code: string;
  category: string;
  allocationPriority: number;
  refundable: boolean;
}

interface Invoice {
  id: number;
  invoiceNo: string;
  enrollmentId: number;
  studentId: number;
  academicSessionId: number;
  billingMonth: string | null;
  issueDate: string;
  dueDate: string;
  grossAmount: number;
  discountAmount: number;
  fineAmount: number;
  netPayableAmount: number;
  allocatedPaidAmount: number;
  balanceDueAmount: number;
  status: string;
}

interface Payment {
  id: number;
  receiptNo: string;
  studentId: number;
  enrollmentId: number;
  amount: number;
  gatewayCode: string;
  status: string;
  paidAt: string;
}

interface PaymentReceiptResponse {
  payment: Payment;
  walletCreditedAmount: number;
}

interface LedgerEntry {
  id: number;
  entryType: string;
  eventCategory: string;
  referenceType: string;
  referenceId: number;
  amount: number;
  runningBalanceAfter: number;
  narration: string | null;
  createdAt: string;
}

interface StudentLedgerResponse {
  studentId: number;
  walletBalance: number;
  currentLedgerBalance: number;
  ledgerEntries: LedgerEntry[];
}

interface StudentLookup {
  id: number;
  studentCode: string;
  firstName: string;
  lastName: string;
}

interface SessionLookup {
  id: number;
  code: string;
  name: string;
}

interface StudentDetailLookup {
  enrollments?: { id: number; academicSessionId: number }[];
}

type TabKey = 'counter' | 'invoices' | 'heads' | 'ledger';

export function FeesPage() {
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState<TabKey>('counter');
  const [heads, setHeads] = useState<FeeHead[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [students, setStudents] = useState<StudentLookup[]>([]);
  const [sessions, setSessions] = useState<SessionLookup[]>([]);
  const [ledger, setLedger] = useState<StudentLedgerResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  // Offline POS Payment Form
  const [payInvoiceId, setPayInvoiceId] = useState<number>(0);
  const [payAmount, setPayAmount] = useState<number>(1500);
  const [payMethod, setPayMethod] = useState<'OFFLINE_CASH' | 'OFFLINE_BANK' | 'OFFLINE_CHEQUE'>('OFFLINE_CASH');

  // Single Invoice Form
  const [invEnrollmentId] = useState<number>(1);
  const [invStudentId, setInvStudentId] = useState<number>(1);
  const [invSessionId, setInvSessionId] = useState<number>(1);
  const [invBillingMonth, setInvBillingMonth] = useState('2026-01');
  const [invDueDate, setInvDueDate] = useState(new Date().toISOString().slice(0, 10));
  const [invFeeHeadId, setInvFeeHeadId] = useState<number>(0);
  const [invAmount, setInvAmount] = useState<number>(2000);

  // Fee Head Form
  const [headName, setHeadName] = useState('');
  const [headCode, setHeadCode] = useState('');
  const [headCategory, setHeadCategory] = useState('TUITION');
  const [headPriority, setHeadPriority] = useState<number>(30);

  // Ledger lookup
  const [ledgerStudentId, setLedgerStudentId] = useState<number>(1);

  const loadData = async () => {
    try {
      setError(null);
      const [headsRes, invRes, payRes, stuRes, sessRes] = await Promise.all([
        api.get<PageResponse<FeeHead> | FeeHead[]>('/api/v1/fees/heads'),
        api.get<PageResponse<Invoice> | Invoice[]>('/api/v1/fees/invoices?size=100'),
        api.get<PageResponse<Payment> | Payment[]>('/api/v1/fees/payments'),
        api.get<PageResponse<StudentLookup> | StudentLookup[]>('/api/v1/students?size=100'),
        api.get<PageResponse<SessionLookup> | SessionLookup[]>('/api/v1/academic/sessions?size=100')
      ]);
      const headList = unwrapList(headsRes.data);
      const invList = unwrapList(invRes.data);
      const payList = unwrapList(payRes.data);
      const stuList = unwrapList(stuRes.data);
      const sessList = unwrapList(sessRes.data);

      setHeads(headList);
      setInvoices(invList);
      setPayments(payList);
      setStudents(stuList);
      setSessions(sessList);

      if (headList[0] && invFeeHeadId === 0) setInvFeeHeadId(headList[0].id);
      if (stuList[0] && invStudentId === 1) setInvStudentId(stuList[0].id);
      if (sessList[0] && invSessionId === 1) setInvSessionId(sessList[0].id);
      if (invList[0] && payInvoiceId === 0) {
        setPayInvoiceId(invList[0].id);
        setPayAmount(Number(invList[0].balanceDueAmount));
      }
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  const handleRecordPayment = async (e: FormEvent) => {
    e.preventDefault();
    if (!payInvoiceId) {
      setError('Please select an invoice to pay.');
      return;
    }
    try {
      setError(null);
      setMessage(null);
      const res = await api.post<PaymentReceiptResponse>('/api/v1/fees/payments/offline', {
        invoiceId: payInvoiceId,
        amount: payAmount,
        gatewayCode: payMethod,
        bankOrChequeRef: null,
        idempotencyKey: `pos-${payInvoiceId}-${Date.now()}`
      });
      setMessage(`Receipt ${res.data.payment.receiptNo} issued.`);
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleReversePayment = async (paymentId: number) => {
    try {
      await api.post(`/api/v1/fees/payments/${paymentId}/reverse`, {
        reversalReason: 'Counter entry reversal'
      });
      setMessage('Payment reversed and ledger updated.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleCreateInvoice = async (e: FormEvent) => {
    e.preventDefault();
    if (!invFeeHeadId) {
      setError('Please create a fee head first.');
      return;
    }
    try {
      setError(null);
      let resolvedEnrollmentId = invEnrollmentId;
      let resolvedSessionId = invSessionId;
      try {
        const detail = await api.get<StudentDetailLookup>(`/api/v1/students/${invStudentId}`);
        const firstEnr = detail.data.enrollments?.[0];
        if (firstEnr) {
          resolvedEnrollmentId = firstEnr.id;
          resolvedSessionId = firstEnr.academicSessionId;
        }
      } catch {
        // Fallback to selected values
      }
      await api.post('/api/v1/fees/invoices', {
        enrollmentId: resolvedEnrollmentId,
        studentId: invStudentId,
        academicSessionId: resolvedSessionId,
        billingMonth: invBillingMonth,
        dueDate: invDueDate,
        lines: [
          {
            feeHeadId: invFeeHeadId,
            description: `Billing ${invBillingMonth}`,
            grossAmount: invAmount,
            discountAmount: 0,
            fineAmount: 0
          }
        ]
      });
      setMessage('Invoice issued and ledger debited.');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleCreateHead = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setError(null);
      await api.post('/api/v1/fees/heads', {
        name: headName,
        code: headCode,
        category: headCategory,
        allocationPriority: headPriority,
        refundable: false
      });
      setHeadName('');
      setHeadCode('');
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleLoadLedger = async () => {
    try {
      setError(null);
      const res = await api.get<StudentLedgerResponse>(
        `/api/v1/fees/students/${ledgerStudentId}/ledger`
      );
      setLedger(res.data);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <section className="stack">
      <div>
        <h2>{t('fees.title')}</h2>
        <p className="muted">{t('fees.subtitle')}</p>
      </div>

      {error && <p className="error-text">{error}</p>}
      {message && <p className="badge active">{message}</p>}

      <div className="tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'counter' ? 'active' : ''}`}
          onClick={() => setActiveTab('counter')}
        >
          {t('fees.counter')}
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'invoices' ? 'active' : ''}`}
          onClick={() => setActiveTab('invoices')}
        >
          {t('fees.invoices')} ({invoices.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'heads' ? 'active' : ''}`}
          onClick={() => setActiveTab('heads')}
        >
          {t('fees.heads')} ({heads.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'ledger' ? 'active' : ''}`}
          onClick={() => {
            setActiveTab('ledger');
            void handleLoadLedger();
          }}
        >
          {t('fees.ledger')}
        </button>
      </div>

      {activeTab === 'counter' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleRecordPayment}>
            <div>
              <label>Invoice</label>
              <select
                value={payInvoiceId}
                onChange={(e) => {
                  const id = Number(e.target.value);
                  setPayInvoiceId(id);
                  const found = invoices.find((inv) => inv.id === id);
                  if (found) {
                    setPayAmount(Number(found.balanceDueAmount));
                  }
                }}
              >
                <option value={0}>-- Select Invoice --</option>
                {invoices.map((inv) => (
                  <option key={inv.id} value={inv.id}>
                    {inv.invoiceNo} (Student #{inv.studentId} - Balance Due:{' '}
                    {Number(inv.balanceDueAmount).toFixed(2)})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Amount</label>
              <input
                type="number"
                step="0.01"
                value={payAmount}
                onChange={(e) => setPayAmount(Number(e.target.value))}
                required
              />
            </div>
            <div>
              <label>Method</label>
              <select
                value={payMethod}
                onChange={(e) =>
                  setPayMethod(e.target.value as 'OFFLINE_CASH' | 'OFFLINE_BANK' | 'OFFLINE_CHEQUE')
                }
              >
                <option value="OFFLINE_CASH">OFFLINE_CASH</option>
                <option value="OFFLINE_BANK">OFFLINE_BANK</option>
                <option value="OFFLINE_CHEQUE">OFFLINE_CHEQUE</option>
              </select>
            </div>
            <button type="submit">{t('fees.recordPayment')}</button>
          </form>

          <div className="card">
            <h3>Recent Receipts</h3>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Receipt #</th>
                  <th>Student ID</th>
                  <th>Method</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <code>{p.receiptNo}</code>
                    </td>
                    <td>#{p.studentId}</td>
                    <td>{p.gatewayCode}</td>
                    <td>{Number(p.amount).toFixed(2)}</td>
                    <td>
                      <span className="badge active">{p.status}</span>
                    </td>
                    <td>
                      {p.status === 'SUCCEEDED' && (
                        <button
                          type="button"
                          className="danger"
                          onClick={() => handleReversePayment(p.id)}
                        >
                          Reverse
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {payments.length === 0 && (
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

      {activeTab === 'invoices' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleCreateInvoice}>
            <div>
              <label>Select Student</label>
              <select
                value={invStudentId}
                onChange={(e) => setInvStudentId(Number(e.target.value))}
                required
              >
                {students.map((stu) => (
                  <option key={stu.id} value={stu.id}>
                    {stu.firstName} {stu.lastName} ({stu.studentCode})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Academic Session</label>
              <select
                value={invSessionId}
                onChange={(e) => setInvSessionId(Number(e.target.value))}
                required
              >
                {sessions.map((sess) => (
                  <option key={sess.id} value={sess.id}>
                    {sess.name} ({sess.code})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Billing Month (YYYY-MM)</label>
              <input
                value={invBillingMonth}
                onChange={(e) => setInvBillingMonth(e.target.value)}
                maxLength={7}
                required
              />
            </div>
            <div>
              <label>Due Date</label>
              <input
                type="date"
                value={invDueDate}
                onChange={(e) => setInvDueDate(e.target.value)}
                required
              />
            </div>
            <div>
              <label>Fee Head</label>
              <select
                value={invFeeHeadId}
                onChange={(e) => setInvFeeHeadId(Number(e.target.value))}
              >
                <option value={0}>-- Select Head --</option>
                {heads.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.name} ({h.code})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Amount</label>
              <input
                type="number"
                step="0.01"
                value={invAmount}
                onChange={(e) => setInvAmount(Number(e.target.value))}
                required
              />
            </div>
            <button type="submit">Issue Invoice</button>
          </form>

          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Invoice #</th>
                  <th>Student ID</th>
                  <th>Month</th>
                  <th>Due Date</th>
                  <th>Net Payable</th>
                  <th>Paid</th>
                  <th>Balance</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((inv) => (
                  <tr key={inv.id}>
                    <td>
                      <code>{inv.invoiceNo}</code>
                    </td>
                    <td>#{inv.studentId}</td>
                    <td>{inv.billingMonth ?? '-'}</td>
                    <td>{inv.dueDate}</td>
                    <td>{Number(inv.netPayableAmount).toFixed(2)}</td>
                    <td>{Number(inv.allocatedPaidAmount).toFixed(2)}</td>
                    <td>{Number(inv.balanceDueAmount).toFixed(2)}</td>
                    <td>
                      <span className="badge active">{inv.status}</span>
                    </td>
                  </tr>
                ))}
                {invoices.length === 0 && (
                  <tr>
                    <td colSpan={8} className="muted">
                      {t('common.noData')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'heads' && (
        <div className="stack">
          <form className="card form-grid" onSubmit={handleCreateHead}>
            <div>
              <label>Fee Head Name</label>
              <input
                value={headName}
                onChange={(e) => setHeadName(e.target.value)}
                placeholder="Monthly Tuition"
                required
              />
            </div>
            <div>
              <label>Code</label>
              <input
                value={headCode}
                onChange={(e) => setHeadCode(e.target.value)}
                placeholder="TUITION"
                required
              />
            </div>
            <div>
              <label>Category</label>
              <select
                value={headCategory}
                onChange={(e) => setHeadCategory(e.target.value)}
              >
                <option value="TUITION">TUITION</option>
                <option value="ADMISSION">ADMISSION</option>
                <option value="EXAM">EXAM</option>
                <option value="TRANSPORT">TRANSPORT</option>
                <option value="FINE">FINE</option>
                <option value="OTHER">OTHER</option>
              </select>
            </div>
            <div>
              <label>Allocation Priority</label>
              <input
                type="number"
                value={headPriority}
                onChange={(e) => setHeadPriority(Number(e.target.value))}
                min={1}
              />
            </div>
            <button type="submit">{t('common.create')}</button>
          </form>

          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Category</th>
                  <th>Priority</th>
                </tr>
              </thead>
              <tbody>
                {heads.map((h) => (
                  <tr key={h.id}>
                    <td>
                      <code>{h.code}</code>
                    </td>
                    <td>{h.name}</td>
                    <td>{h.category}</td>
                    <td>{h.allocationPriority}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'ledger' && (
        <div className="stack">
          <div className="card action-row">
            <label>Select Student:</label>
            <select
              value={ledgerStudentId}
              onChange={(e) => setLedgerStudentId(Number(e.target.value))}
            >
              {students.map((stu) => (
                <option key={stu.id} value={stu.id}>
                  {stu.firstName} {stu.lastName} ({stu.studentCode})
                </option>
              ))}
            </select>
            <button type="button" onClick={handleLoadLedger}>
              Load Immutable Ledger
            </button>
          </div>

          {ledger && (
            <div className="card stack">
              <div className="grid-cards">
                <div>
                  <span className="muted">Current Receivable Balance</span>
                  <h3>{Number(ledger.currentLedgerBalance).toFixed(2)}</h3>
                </div>
                <div>
                  <span className="muted">Advance Wallet Balance</span>
                  <h3>{Number(ledger.walletBalance).toFixed(2)}</h3>
                </div>
              </div>

              <table className="data-table">
                <thead>
                  <tr>
                    <th>Entry Type</th>
                    <th>Category</th>
                    <th>Amount</th>
                    <th>Running Balance</th>
                    <th>Reference</th>
                    <th>Narration</th>
                  </tr>
                </thead>
                <tbody>
                  {(ledger.ledgerEntries ?? []).map((entry) => (
                    <tr key={entry.id}>
                      <td>
                        <span className="badge">{entry.entryType}</span>
                      </td>
                      <td>{entry.eventCategory}</td>
                      <td>{Number(entry.amount).toFixed(2)}</td>
                      <td>
                        <strong>{Number(entry.runningBalanceAfter).toFixed(2)}</strong>
                      </td>
                      <td>
                        <code>
                          {entry.referenceType} #{entry.referenceId}
                        </code>
                      </td>
                      <td>{entry.narration ?? '-'}</td>
                    </tr>
                  ))}
                  {(ledger.ledgerEntries ?? []).length === 0 && (
                    <tr>
                      <td colSpan={6} className="muted">
                        {t('common.noData')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
