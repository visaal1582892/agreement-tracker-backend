package com.medplus.agreement_tracker_backend;

import com.medplus.agreement_tracker_backend.service.RevenueRecognitionCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class TestRunner {

    @Autowired
    private RevenueRecognitionCalculationService service;

    @Test
    public void testCalc() {
        service.calculateAndStoreRevenue(List.of(202504, 202505, 202506), null, null);
    }
}
