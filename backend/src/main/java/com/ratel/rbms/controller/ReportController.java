package com.ratel.rbms.controller;

import com.ratel.rbms.dto.ReportSummaryResponse;
import com.ratel.rbms.dto.StaffCommissionResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.service.ReportExportService;
import com.ratel.rbms.service.ReportService;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final BusinessRepository businessRepository;

    public ReportController(ReportService reportService, ReportExportService reportExportService, BusinessRepository businessRepository) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.businessRepository = businessRepository;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate[] range = effectiveRange(from, to);
        return reportService.summary(range[0], range[1]);
    }

    @GetMapping("/commissions")
    public List<StaffCommissionResponse> commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate[] range = effectiveRange(from, to);
        return reportService.commissions(range[0], range[1]);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate[] range = effectiveRange(from, to);
        Business business = businessRepository.findById(TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));

        byte[] xlsx = reportExportService.exportReport(business, range[0], range[1]);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + range[0] + "-to-" + range[1] + ".xlsx")
                .body(xlsx);
    }

    private LocalDate[] effectiveRange(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        // Default window: month-to-date, so hitting this with no params gives a sensible answer.
        LocalDate effectiveFrom = from != null ? from : effectiveTo.with(TemporalAdjusters.firstDayOfMonth());
        return new LocalDate[]{effectiveFrom, effectiveTo};
    }
}
