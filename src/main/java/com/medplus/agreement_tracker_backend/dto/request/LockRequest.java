package com.medplus.agreement_tracker_backend.dto.request;

import lombok.Data;

@Data
public class LockRequest {
    private Integer calendarYear;
    private Integer calendarMonth;
}
