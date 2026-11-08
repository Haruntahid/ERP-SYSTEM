package com.schoolerp.fees.application;

import com.schoolerp.fees.domain.FeeHead;
import com.schoolerp.fees.domain.FeeLedgerEntry;
import com.schoolerp.fees.domain.FeeStructure;
import com.schoolerp.fees.domain.FeeStructureLine;
import com.schoolerp.fees.domain.Invoice;
import com.schoolerp.fees.domain.InvoiceLine;
import com.schoolerp.fees.domain.Payment;
import com.schoolerp.fees.domain.PaymentAllocation;
import com.schoolerp.fees.domain.StudentDiscountWaiver;
import com.schoolerp.fees.domain.StudentWallet;
import com.schoolerp.fees.infrastructure.FeeHeadRepository;
import com.schoolerp.fees.infrastructure.FeeLedgerEntryRepository;
import com.schoolerp.fees.infrastructure.FeeStructureLineRepository;
import com.schoolerp.fees.infrastructure.FeeStructureRepository;
import com.schoolerp.fees.infrastructure.InvoiceLineRepository;
import com.schoolerp.fees.infrastructure.InvoiceRepository;
import com.schoolerp.fees.infrastructure.PaymentAllocationRepository;
import com.schoolerp.fees.infrastructure.PaymentRepository;
import com.schoolerp.fees.infrastructure.StudentDiscountWaiverRepository;
import com.schoolerp.fees.infrastructure.StudentWalletRepository;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.messaging.EventPriority;
import com.schoolerp.shared.messaging.OutboxEventWriter;
import com.schoolerp.students.application.StudentService;
import com.schoolerp.students.domain.Enrollment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeService {

    private final FeeHeadRepository feeHeadRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final FeeStructureLineRepository feeStructureLineRepository;
    private final StudentDiscountWaiverRepository waiverRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final StudentWalletRepository walletRepository;
    private final FeeLedgerEntryRepository ledgerRepository;
    private final StudentService studentService;
    private final AuditService auditService;
    private final OutboxEventWriter outboxEventWriter;

    public FeeService(
            FeeHeadRepository feeHeadRepository,
            FeeStructureRepository feeStructureRepository,
            FeeStructureLineRepository feeStructureLineRepository,
            StudentDiscountWaiverRepository waiverRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository allocationRepository,
            StudentWalletRepository walletRepository,
            FeeLedgerEntryRepository ledgerRepository,
            StudentService studentService,
            AuditService auditService,
            OutboxEventWriter outboxEventWriter) {
        this.feeHeadRepository = feeHeadRepository;
        this.feeStructureRepository = feeStructureRepository;
        this.feeStructureLineRepository = feeStructureLineRepository;
        this.waiverRepository = waiverRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.studentService = studentService;
        this.auditService = auditService;
        this.outboxEventWriter = outboxEventWriter;
    }

    public record FeeStructureLineInput(
            Long feeHeadId,
            String frequency,
            BigDecimal amount,
            int dueDayOfMonth) {}

    public record FeeStructureDto(
            FeeStructure structure,
            List<FeeStructureLine> lines) {}

    public record InvoiceLineInput(
            Long feeHeadId,
            String description,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal fineAmount) {}

    public record InvoiceDetailDto(
            Invoice invoice,
            List<InvoiceLine> lines) {}

    public record PaymentReceiptDto(
            Payment payment,
            List<PaymentAllocation> allocations,
            BigDecimal walletCreditedAmount) {}

    public record StudentFeeLedgerStatementDto(
            Long studentId,
            BigDecimal walletBalance,
            BigDecimal currentLedgerBalance,
            List<Invoice> invoices,
            List<Payment> payments,
            List<FeeLedgerEntry> ledgerEntries) {}

    @Transactional(readOnly = true)
    public List<FeeHead> listFeeHeads() {
        return feeHeadRepository.findAllByDeletedAtIsNullOrderByAllocationPriorityAscIdAsc();
    }

    @Transactional
    public FeeHead saveFeeHead(String code, String name, String category, int allocationPriority, boolean refundable) {
        Optional<FeeHead> existing = feeHeadRepository.findByCodeAndDeletedAtIsNull(code.trim().toUpperCase());
        if (existing.isPresent()) {
            FeeHead head = existing.get();
            head.setName(name.trim());
            return feeHeadRepository.save(head);
        }
        return feeHeadRepository.save(new FeeHead(code, name, category, allocationPriority, refundable));
    }

    @Transactional(readOnly = true)
    public List<FeeStructureDto> listFeeStructures() {
        List<FeeStructure> structures = feeStructureRepository.findAllByDeletedAtIsNullOrderByIdDesc();
        return structures.stream()
                .map(s -> new FeeStructureDto(s, feeStructureLineRepository.findAllByFeeStructureId(s.getId())))
                .toList();
    }

    @Transactional
    public FeeStructureDto createFeeStructure(
            Long academicSessionId,
            Long campusId,
            Long classId,
            String name,
            String currency,
            List<FeeStructureLineInput> lineInputs) {
        FeeStructure structure = feeStructureRepository.save(
                new FeeStructure(academicSessionId, campusId, classId, name, currency));
        List<FeeStructureLine> savedLines = new ArrayList<>();
        if (lineInputs != null) {
            for (FeeStructureLineInput li : lineInputs) {
                savedLines.add(feeStructureLineRepository.save(
                        new FeeStructureLine(structure.getId(), li.feeHeadId(), li.frequency(), li.amount(), li.dueDayOfMonth())));
            }
        }
        auditService.record("FEE_STRUCTURE_CREATED", "FeeStructure", String.valueOf(structure.getId()), null,
                Map.of("name", structure.getName(), "classId", classId));
        return new FeeStructureDto(structure, savedLines);
    }

    @Transactional(readOnly = true)
    public List<StudentDiscountWaiver> listWaivers() {
        return waiverRepository.findAllByDeletedAtIsNullOrderByIdDesc();
    }

    @Transactional
    public StudentDiscountWaiver createWaiver(
            Long enrollmentId,
            Long studentId,
            Long feeHeadId,
            String title,
            String discountType,
            BigDecimal discountValue,
            String reason,
            Long actorUserId,
            boolean autoApprove) {
        StudentDiscountWaiver waiver = waiverRepository.save(new StudentDiscountWaiver(
                enrollmentId, studentId, feeHeadId, title, discountType, discountValue, reason, actorUserId, autoApprove));
        auditService.record("WAIVER_CREATED", "StudentDiscountWaiver", String.valueOf(waiver.getId()), null,
                Map.of("studentId", studentId, "title", title, "status", waiver.getStatus()));
        return waiver;
    }

    @Transactional
    public StudentDiscountWaiver approveWaiver(Long waiverId, Long approverUserId) {
        StudentDiscountWaiver waiver = waiverRepository.findById(waiverId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Waiver not found"));
        waiver.approve(approverUserId);
        waiver = waiverRepository.save(waiver);
        auditService.record("WAIVER_APPROVED", "StudentDiscountWaiver", String.valueOf(waiverId), null,
                Map.of("studentId", waiver.getStudentId(), "approvedBy", approverUserId != null ? approverUserId : 1L));
        return waiver;
    }

    @Transactional
    public InvoiceDetailDto createSingleInvoice(
            Long enrollmentId,
            Long studentId,
            Long academicSessionId,
            String billingMonth,
            LocalDate dueDate,
            List<InvoiceLineInput> lineInputs,
            Long actorUserId) {
        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one invoice line is required");
        }
        long seq = invoiceRepository.count() + 1;
        String invoiceNo = "INV-%d-%06d".formatted(Year.now().getValue(), seq);

        Invoice invoice = new Invoice(
                invoiceNo, enrollmentId, studentId, academicSessionId, billingMonth,
                LocalDate.now(), dueDate != null ? dueDate : LocalDate.now().plusDays(15), "BDT", actorUserId);
        invoice = invoiceRepository.save(invoice);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalFine = BigDecimal.ZERO;
        List<InvoiceLine> savedLines = new ArrayList<>();

        for (InvoiceLineInput input : lineInputs) {
            BigDecimal gross = input.grossAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal discount = input.discountAmount() != null ? input.discountAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal fine = input.fineAmount() != null ? input.fineAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            InvoiceLine line = invoiceLineRepository.save(
                    new InvoiceLine(invoice.getId(), input.feeHeadId(), input.description(), gross, discount, fine));
            savedLines.add(line);
            totalGross = totalGross.add(gross);
            totalDiscount = totalDiscount.add(discount);
            totalFine = totalFine.add(fine);
        }

        invoice.recalculateFromLines(totalGross, totalDiscount, totalFine, BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        appendLedgerEntry(
                studentId,
                enrollmentId,
                "DEBIT",
                "INVOICE_ISSUED",
                "Invoice",
                invoice.getId(),
                invoice.getCurrency(),
                invoice.getNetPayableAmount(),
                "Invoice " + invoice.getInvoiceNo() + " issued",
                actorUserId);

        auditService.record("INVOICE_ISSUED", "Invoice", String.valueOf(invoice.getId()), null,
                Map.of("invoiceNo", invoice.getInvoiceNo(), "studentId", studentId,
                        "netPayableAmount", invoice.getNetPayableAmount()));

        outboxEventWriter.write("Invoice", String.valueOf(invoice.getId()),
                "InvoiceIssuedEvent", "fees.invoice_issued", EventPriority.NORMAL,
                Map.of("invoiceId", invoice.getId(), "invoiceNo", invoice.getInvoiceNo(),
                        "studentId", studentId, "netPayableAmount", invoice.getNetPayableAmount()));

        return new InvoiceDetailDto(invoice, savedLines);
    }

    @Transactional
    public List<Invoice> generateBatchInvoices(
            Long academicSessionId,
            Long classId,
            Long feeStructureId,
            String billingMonth,
            LocalDate dueDate,
            Long actorUserId) {
        FeeStructure structure = feeStructureRepository.findByIdAndDeletedAtIsNull(feeStructureId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fee structure not found"));
        List<FeeStructureLine> structureLines = feeStructureLineRepository.findAllByFeeStructureId(structure.getId());
        if (structureLines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fee structure has no lines");
        }
        Map<Long, FeeHead> headsById = new HashMap<>();
        for (FeeHead h : feeHeadRepository.findAll()) {
            headsById.put(h.getId(), h);
        }

        List<Enrollment> enrollments = studentService.listSessionEnrollments(academicSessionId, classId, null);
        List<Invoice> created = new ArrayList<>();

        for (Enrollment e : enrollments) {
            List<StudentDiscountWaiver> activeWaivers = waiverRepository
                    .findAllByEnrollmentIdAndStatusAndDeletedAtIsNull(e.getId(), "ACTIVE");
            List<InvoiceLineInput> lineInputs = new ArrayList<>();

            for (FeeStructureLine sl : structureLines) {
                FeeHead head = headsById.get(sl.getFeeHeadId());
                String desc = (head != null ? head.getName() : "Fee") + (billingMonth != null ? " (" + billingMonth + ")" : "");
                BigDecimal discount = BigDecimal.ZERO;
                for (StudentDiscountWaiver w : activeWaivers) {
                    if (w.getFeeHeadId().equals(sl.getFeeHeadId())) {
                        if ("PERCENTAGE".equalsIgnoreCase(w.getDiscountType())) {
                            discount = discount.add(sl.getAmount().multiply(w.getDiscountValue())
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                        } else {
                            discount = discount.add(w.getDiscountValue());
                        }
                    }
                }
                discount = discount.min(sl.getAmount());
                lineInputs.add(new InvoiceLineInput(sl.getFeeHeadId(), desc, sl.getAmount(), discount, BigDecimal.ZERO));
            }

            InvoiceDetailDto dto = createSingleInvoice(
                    e.getId(), e.getStudentId(), academicSessionId, billingMonth, dueDate, lineInputs, actorUserId);
            created.add(dto.invoice());
        }
        return created;
    }

    @Transactional(readOnly = true)
    public Page<Invoice> listInvoices(Pageable pageable) {
        return invoiceRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public InvoiceDetailDto getInvoiceDetail(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return new InvoiceDetailDto(invoice, invoiceLineRepository.findAllByInvoiceIdOrderByIdAsc(invoiceId));
    }

    @Transactional
    public PaymentReceiptDto recordPayment(
            String idempotencyKey,
            Long invoiceId,
            BigDecimal amount,
            String gatewayCode,
            String bankOrChequeRef,
            Long actorUserId) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : UUID.randomUUID().toString();

        // BR-04: Idempotency check
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(key);
        if (existingPayment.isPresent()) {
            Payment p = existingPayment.get();
            return new PaymentReceiptDto(p, allocationRepository.findAllByPaymentId(p.getId()), BigDecimal.ZERO);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero");
        }

        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if ("PAID".equals(invoice.getStatus()) || "CANCELLED".equals(invoice.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Invoice is already " + invoice.getStatus());
        }

        long seq = paymentRepository.count() + 1;
        String receiptNo = "RCP-%d-%06d".formatted(Year.now().getValue(), seq);
        String verificationToken = UUID.randomUUID().toString().replace("-", "");

        Payment payment = paymentRepository.save(new Payment(
                receiptNo,
                key,
                invoice.getStudentId(),
                invoice.getEnrollmentId(),
                gatewayCode,
                bankOrChequeRef,
                invoice.getCurrency(),
                amount,
                actorUserId,
                verificationToken));

        // Sort invoice lines by FeeHead allocationPriority ASC (BR-06: FINE -> ARREARS -> TUITION -> OTHER)
        Map<Long, Integer> priorityByHeadId = new HashMap<>();
        for (FeeHead h : feeHeadRepository.findAll()) {
            priorityByHeadId.put(h.getId(), h.getAllocationPriority());
        }

        List<InvoiceLine> lines = new ArrayList<>(invoiceLineRepository.findAllByInvoiceIdOrderByIdAsc(invoice.getId()));
        lines.sort(Comparator.comparingInt((InvoiceLine l) -> priorityByHeadId.getOrDefault(l.getFeeHeadId(), 100))
                .thenComparingLong(InvoiceLine::getId));

        BigDecimal remainingToAllocate = amount.setScale(2, RoundingMode.HALF_UP);
        List<PaymentAllocation> allocations = new ArrayList<>();

        for (InvoiceLine line : lines) {
            if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal lineDue = line.getBalanceDueAmount();
            if (lineDue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal allocated = remainingToAllocate.min(lineDue);
                line.allocate(allocated);
                invoiceLineRepository.save(line);
                allocations.add(allocationRepository.save(
                        new PaymentAllocation(payment.getId(), invoice.getId(), line.getId(), allocated)));
                remainingToAllocate = remainingToAllocate.subtract(allocated);
            }
        }

        recalculateInvoiceTotals(invoice, lines);

        // Append CREDIT ledger entry for payment received
        appendLedgerEntry(
                invoice.getStudentId(),
                invoice.getEnrollmentId(),
                "CREDIT",
                "PAYMENT_RECEIVED",
                "Payment",
                payment.getId(),
                payment.getCurrency(),
                payment.getAmount(),
                "Payment received (" + payment.getReceiptNo() + ") for " + invoice.getInvoiceNo(),
                actorUserId);

        // BR-07: Overpayment credited to StudentWallet
        BigDecimal walletCredited = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (remainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
            StudentWallet wallet = walletRepository
                    .findByStudentIdAndCurrency(invoice.getStudentId(), invoice.getCurrency())
                    .orElseGet(() -> new StudentWallet(invoice.getStudentId(), invoice.getCurrency()));
            wallet.credit(remainingToAllocate);
            walletRepository.save(wallet);
            walletCredited = remainingToAllocate;
        }

        auditService.record("PAYMENT_COLLECTED", "Payment", String.valueOf(payment.getId()), null,
                Map.of("receiptNo", payment.getReceiptNo(), "invoiceNo", invoice.getInvoiceNo(),
                        "amount", payment.getAmount(), "gatewayCode", payment.getGatewayCode()));

        outboxEventWriter.write("Payment", String.valueOf(payment.getId()),
                "PaymentSucceededEvent", "fees.payment_succeeded", EventPriority.HIGH,
                Map.of("paymentId", payment.getId(), "receiptNo", payment.getReceiptNo(),
                        "studentId", payment.getStudentId(), "amount", payment.getAmount(),
                        "remainingBalance", invoice.getBalanceDueAmount()));

        return new PaymentReceiptDto(payment, allocations, walletCredited);
    }

    @Transactional
    public Payment reversePayment(Long paymentId, String reversalReason, Long actorUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        if ("REVERSED".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is already reversed");
        }
        payment.markReversed(reversalReason, actorUserId);
        payment = paymentRepository.save(payment);

        List<PaymentAllocation> allocations = allocationRepository.findAllByPaymentId(paymentId);
        Long affectedInvoiceId = null;
        for (PaymentAllocation alloc : allocations) {
            if ("ACTIVE".equals(alloc.getStatus())) {
                alloc.markReversed();
                allocationRepository.save(alloc);
                affectedInvoiceId = alloc.getInvoiceId();
                invoiceLineRepository.findById(alloc.getInvoiceLineId()).ifPresent(line -> {
                    line.unallocate(alloc.getAllocatedAmount());
                    invoiceLineRepository.save(line);
                });
            }
        }

        if (affectedInvoiceId != null) {
            invoiceRepository.findByIdAndDeletedAtIsNull(affectedInvoiceId).ifPresent(inv -> {
                List<InvoiceLine> lines = invoiceLineRepository.findAllByInvoiceIdOrderByIdAsc(inv.getId());
                recalculateInvoiceTotals(inv, lines);
            });
        }

        // BR-03: Append compensating DEBIT entry to immutable ledger
        appendLedgerEntry(
                payment.getStudentId(),
                payment.getEnrollmentId(),
                "DEBIT",
                "PAYMENT_REVERSED",
                "Payment",
                payment.getId(),
                payment.getCurrency(),
                payment.getAmount(),
                "Reversal of " + payment.getReceiptNo() + ": " + reversalReason,
                actorUserId);

        auditService.record("PAYMENT_REVERSED", "Payment", String.valueOf(paymentId), null,
                Map.of("receiptNo", payment.getReceiptNo(), "amount", payment.getAmount(), "reason", reversalReason));
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> listPayments() {
        return paymentRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public StudentFeeLedgerStatementDto getStudentStatement(Long studentId) {
        BigDecimal walletBalance = walletRepository.findByStudentIdAndCurrency(studentId, "BDT")
                .map(StudentWallet::getBalanceAmount)
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        List<FeeLedgerEntry> entries = ledgerRepository.findAllByStudentIdOrderByIdDesc(studentId);
        BigDecimal runningBalance = entries.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : entries.get(0).getRunningBalanceAfter();
        return new StudentFeeLedgerStatementDto(
                studentId,
                walletBalance,
                runningBalance,
                invoiceRepository.findAllByStudentIdAndDeletedAtIsNullOrderByIdDesc(studentId),
                paymentRepository.findAllByStudentIdOrderByIdDesc(studentId),
                entries);
    }

    private void recalculateInvoiceTotals(Invoice invoice, List<InvoiceLine> lines) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal fine = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (InvoiceLine l : lines) {
            gross = gross.add(l.getGrossAmount());
            discount = discount.add(l.getDiscountAmount());
            fine = fine.add(l.getFineAmount());
            paid = paid.add(l.getAllocatedPaidAmount());
        }
        invoice.recalculateFromLines(gross, discount, fine, paid);
        invoiceRepository.save(invoice);
    }

    private void appendLedgerEntry(
            Long studentId,
            Long enrollmentId,
            String entryType,
            String eventCategory,
            String referenceType,
            Long referenceId,
            String currency,
            BigDecimal amount,
            String narration,
            Long actorUserId) {
        BigDecimal previousBalance = ledgerRepository.findFirstByStudentIdOrderByIdDesc(studentId)
                .map(FeeLedgerEntry::getRunningBalanceAfter)
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        BigDecimal newBalance = "DEBIT".equalsIgnoreCase(entryType)
                ? previousBalance.add(amount)
                : previousBalance.subtract(amount);
        ledgerRepository.save(new FeeLedgerEntry(
                studentId, enrollmentId, entryType, eventCategory, referenceType,
                referenceId, currency, amount, newBalance, narration, actorUserId));
    }
}

