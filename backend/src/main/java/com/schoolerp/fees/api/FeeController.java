package com.schoolerp.fees.api;

import com.schoolerp.fees.application.FeeService;
import com.schoolerp.fees.application.FeeService.FeeStructureDto;
import com.schoolerp.fees.application.FeeService.FeeStructureLineInput;
import com.schoolerp.fees.application.FeeService.InvoiceDetailDto;
import com.schoolerp.fees.application.FeeService.InvoiceLineInput;
import com.schoolerp.fees.application.FeeService.PaymentReceiptDto;
import com.schoolerp.fees.application.FeeService.StudentFeeLedgerStatementDto;
import com.schoolerp.fees.domain.FeeHead;
import com.schoolerp.fees.domain.Invoice;
import com.schoolerp.fees.domain.Payment;
import com.schoolerp.fees.domain.StudentDiscountWaiver;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.RequiresFeature;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/fees")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Fees, Invoices & Payments", description = "Fee structures, waivers, invoices, priority allocations, and immutable fee ledger")
@RequiresFeature("fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    public record CreateFeeHeadRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 30) String category,
            int allocationPriority,
            boolean refundable) {}

    public record CreateFeeStructureRequest(
            @NotNull Long academicSessionId,
            Long campusId,
            @NotNull Long classId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 3) String currency,
            @NotEmpty List<FeeStructureLineInput> lines) {}

    public record CreateStudentWaiverRequest(
            @NotNull Long enrollmentId,
            @NotNull Long studentId,
            @NotNull Long feeHeadId,
            @NotBlank @Size(max = 150) String title,
            @NotBlank String discountType,
            @NotNull @DecimalMin("0.01") BigDecimal discountValue,
            @NotBlank @Size(max = 500) String reason,
            boolean autoApprove) {}

    public record CreateSingleInvoiceRequest(
            @NotNull Long enrollmentId,
            @NotNull Long studentId,
            @NotNull Long academicSessionId,
            @Size(max = 7) String billingMonth,
            LocalDate dueDate,
            @NotEmpty List<InvoiceLineInput> lines) {}

    public record BatchGenerateInvoicesRequest(
            @NotNull Long academicSessionId,
            @NotNull Long classId,
            @NotNull Long feeStructureId,
            @Size(max = 7) String billingMonth,
            LocalDate dueDate) {}

    public record RecordPaymentRequest(
            @NotNull Long invoiceId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 30) String gatewayCode,
            @Size(max = 120) String bankOrChequeRef,
            @Size(max = 100) String idempotencyKey) {}

    public record ReversePaymentRequest(
            @NotBlank @Size(max = 500) String reversalReason) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    @GetMapping("/heads")
    @PreAuthorize("@perm.has(authentication, 'fee.read')")
    @Operation(summary = "List fee heads ordered by allocation priority")
    public List<FeeHead> listFeeHeads() {
        return feeService.listFeeHeads();
    }

    @PostMapping("/heads")
    @PreAuthorize("@perm.has(authentication, 'fee.config.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or update a fee head")
    public FeeHead createFeeHead(@Valid @RequestBody CreateFeeHeadRequest request) {
        return feeService.saveFeeHead(
                request.code(), request.name(), request.category(), request.allocationPriority(), request.refundable());
    }

    @GetMapping("/structures")
    @PreAuthorize("@perm.has(authentication, 'fee.read')")
    @Operation(summary = "List fee structures with lines")
    public List<FeeStructureDto> listFeeStructures() {
        return feeService.listFeeStructures();
    }

    @PostMapping("/structures")
    @PreAuthorize("@perm.has(authentication, 'fee.config.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a class fee structure with lines")
    public FeeStructureDto createFeeStructure(@Valid @RequestBody CreateFeeStructureRequest request) {
        return feeService.createFeeStructure(
                request.academicSessionId(),
                request.campusId(),
                request.classId(),
                request.name(),
                request.currency(),
                request.lines());
    }

    @GetMapping("/waivers")
    @PreAuthorize("@perm.has(authentication, 'fee.read')")
    @Operation(summary = "List student discount waivers")
    public List<StudentDiscountWaiver> listWaivers() {
        return feeService.listWaivers();
    }

    @PostMapping("/waivers")
    @PreAuthorize("@perm.has(authentication, 'fee.waiver.request')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a student discount/scholarship waiver")
    public StudentDiscountWaiver createWaiver(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody CreateStudentWaiverRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return feeService.createWaiver(
                request.enrollmentId(),
                request.studentId(),
                request.feeHeadId(),
                request.title(),
                request.discountType(),
                request.discountValue(),
                request.reason(),
                userId,
                request.autoApprove());
    }

    @PostMapping("/waivers/{id}/approve")
    @PreAuthorize("@perm.has(authentication, 'fee.waiver.approve')")
    @Operation(summary = "Approve a pending student waiver")
    public StudentDiscountWaiver approveWaiver(
            @PathVariable Long id,
            @AuthenticationPrincipal SchoolUserPrincipal principal) {
        Long userId = principal != null ? principal.userId() : 1L;
        return feeService.approveWaiver(id, userId);
    }

    @PostMapping("/invoices")
    @PreAuthorize("@perm.has(authentication, 'fee.invoice.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a single student invoice and post DEBIT to fee ledger")
    public InvoiceDetailDto createInvoice(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody CreateSingleInvoiceRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return feeService.createSingleInvoice(
                request.enrollmentId(),
                request.studentId(),
                request.academicSessionId(),
                request.billingMonth(),
                request.dueDate(),
                request.lines(),
                userId);
    }

    @PostMapping("/invoices/generate-batch")
    @PreAuthorize("@perm.has(authentication, 'fee.invoice.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate batch invoices for all active enrollments in a class using a fee structure")
    public List<Invoice> generateBatchInvoices(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody BatchGenerateInvoicesRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return feeService.generateBatchInvoices(
                request.academicSessionId(),
                request.classId(),
                request.feeStructureId(),
                request.billingMonth(),
                request.dueDate(),
                userId);
    }

    @GetMapping("/invoices")
    @PreAuthorize("@perm.has(authentication, 'fee.invoice.read')")
    @Operation(summary = "List invoices")
    public PageResponse<Invoice> listInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<Invoice> result = feeService.listInvoices(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("@perm.has(authentication, 'fee.invoice.read')")
    @Operation(summary = "Get invoice detail and line breakdown")
    public InvoiceDetailDto getInvoice(@PathVariable Long id) {
        return feeService.getInvoiceDetail(id);
    }

    @PostMapping("/payments/offline")
    @PreAuthorize("@perm.has(authentication, 'fee.payment.collect')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an idempotent payment, allocate across invoice lines by priority, and post CREDIT to ledger")
    public PaymentReceiptDto recordOfflinePayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String headerIdempotencyKey,
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody RecordPaymentRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        String key = headerIdempotencyKey != null && !headerIdempotencyKey.isBlank()
                ? headerIdempotencyKey
                : request.idempotencyKey();
        return feeService.recordPayment(
                key,
                request.invoiceId(),
                request.amount(),
                request.gatewayCode(),
                request.bankOrChequeRef(),
                userId);
    }

    @GetMapping("/payments")
    @PreAuthorize("@perm.has(authentication, 'fee.payment.read')")
    @Operation(summary = "List payments")
    public List<Payment> listPayments() {
        return feeService.listPayments();
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize("@perm.has(authentication, 'fee.payment.reverse')")
    @Operation(summary = "Reverse a posted payment and append compensating DEBIT to fee ledger")
    public Payment reversePayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody ReversePaymentRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return feeService.reversePayment(paymentId, request.reversalReason(), userId);
    }

    @GetMapping("/students/{studentId}/ledger")
    @PreAuthorize("@perm.has(authentication, 'fee.ledger.read')")
    @Operation(summary = "Get student's immutable fee ledger statement, invoices, payments, and wallet balance")
    public StudentFeeLedgerStatementDto getStudentLedger(@PathVariable Long studentId) {
        return feeService.getStudentStatement(studentId);
    }
}

