package com.dipdv.modules.report.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ReportFilterRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to
) {
    public OffsetDateTime fromDateTime() {
        return (from != null ? from : LocalDate.now())
                .atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public OffsetDateTime toDateTime() {
        return (to != null ? to : LocalDate.now())
                .atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
    }
}
