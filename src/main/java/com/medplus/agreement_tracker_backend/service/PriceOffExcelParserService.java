package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.response.PriceOffExcelParseResult;
import org.apache.poi.ss.usermodel.Workbook;

public interface PriceOffExcelParserService {

    PriceOffExcelParseResult parseWorkbook(Workbook workbook);
}
