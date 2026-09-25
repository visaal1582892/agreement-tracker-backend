package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.dto.response.MonthlyCalculationDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class CommercialCalculationDao {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CommercialCalculationDao(@Qualifier("secondNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MonthlyCalculationDto> fetchAggregatedCalculations(
            List<Long> supplierIds,
            List<String> productIds,
            List<String> storeIds,
            List<String> stateCodes,
            LocalDate startDate,
            LocalDate endDate,
            boolean useChallanDate) {

        String dateColumn = useChallanDate ? "DateChalan" : "DateVerified";
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT a.FromAccountID AS supplierId, b.ProductID AS productId, ");
        sql.append("DATE_FORMAT(a.").append(dateColumn).append(", '%Y%m') AS periodMonth, ");
        sql.append("SUM((b.Quantity + b.FreeQuantity) - IFNULL(q.PackSize * j.PurchaseRejectQuantity, 0)) AS totalNetQty, ");
        sql.append("SUM(ROUND((b.Quantity + b.FreeQuantity) * b.CostPrice_E_Tax, 4) - ");
        sql.append("ROUND(IFNULL(q.PackSize * j.PurchaseRejectQuantity, 0) * b.CostPrice_E_Tax, 4)) AS totalNetValue ");
        
        sql.append("FROM pos.tbl_b2b_invoice a ");
        sql.append("INNER JOIN pos.tbl_b2b_invoice_detail b ON a.AccountInvoiceId = b.AccountInvoiceId ");
        sql.append("LEFT JOIN grn.tbl_proforma_rejection i ON a.StoreInvoiceID = i.ProformaStoreReceiptId ");
        sql.append("LEFT JOIN grn.tbl_proforma_rejection_detail j ON i.ProformaRejectionId = j.ProformaRejectionId AND b.Sno = j.Sno ");
        sql.append("LEFT JOIN grn.tbl_proforma_store_receipt_detail q ON i.ProformaStoreReceiptId = q.ProformaStoreReceiptId AND j.Sno = q.Sno ");
        sql.append("INNER JOIN pos.tbl_store f ON a.ToStoreId = f.StoreID ");
        
        sql.append("WHERE a.BusinessType = 'T' AND a.TransactionType IN ('I','D') ");
        sql.append("AND (b.Quantity + b.FreeQuantity) > 0 ");
        sql.append("AND (a.DeliveryChalanNo != 'INTERNAL' OR a.DeliveryChalanNo IS NULL) ");
        
        // 1. Mandatory Date Filters
        sql.append("AND a.").append(dateColumn).append(" >= :startDate AND a.").append(dateColumn).append(" <= :endDate ");
        params.addValue("startDate", startDate);
        params.addValue("endDate", endDate);

        // 2. Dynamic List Filters (Prevents Full Table Scans)
        if (supplierIds != null && !supplierIds.isEmpty()) {
            sql.append("AND a.FromAccountID IN (:supplierIds) ");
            params.addValue("supplierIds", supplierIds);
        }
        if (productIds != null && !productIds.isEmpty()) {
            sql.append("AND b.ProductID IN (:productIds) ");
            params.addValue("productIds", productIds);
        }
        if (storeIds != null && !storeIds.isEmpty()) {
            sql.append("AND a.ToStoreId IN (:storeIds) ");
            params.addValue("storeIds", storeIds);
        }
        if (stateCodes != null && !stateCodes.isEmpty()) {
            sql.append("AND f.Region_2 IN (:stateCodes) ");
            params.addValue("stateCodes", stateCodes);
        }

        sql.append("GROUP BY a.FromAccountID, b.ProductID, DATE_FORMAT(a.").append(dateColumn).append(", '%Y%m')");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> 
            new MonthlyCalculationDto(
                rs.getLong("supplierId"),
                rs.getString("productId"),
                rs.getInt("periodMonth"),
                rs.getLong("totalNetQty"),
                rs.getBigDecimal("totalNetValue")
            )
        );
    }

    public PurchaseTotals sumTotals(
            List<Integer> periodKeys,
            List<Long> supplierIds,
            List<String> productIds,
            List<String> storeIds,
            List<String> stateCodes,
            LocalDate startDate,
            LocalDate endDate,
            boolean useChallanDate) {

        String dateColumn = useChallanDate ? "DateChalan" : "DateVerified";
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT COALESCE(SUM((b.Quantity + b.FreeQuantity) - IFNULL(q.PackSize * j.PurchaseRejectQuantity, 0)), 0) AS totalNetQty, ");
        sql.append("COALESCE(SUM(ROUND((b.Quantity + b.FreeQuantity) * b.CostPrice_E_Tax, 4) - ");
        sql.append("ROUND(IFNULL(q.PackSize * j.PurchaseRejectQuantity, 0) * b.CostPrice_E_Tax, 4)), 0) AS totalNetValue ");
        
        sql.append("FROM pos.tbl_b2b_invoice a ");
        sql.append("INNER JOIN pos.tbl_b2b_invoice_detail b ON a.AccountInvoiceId = b.AccountInvoiceId ");
        sql.append("LEFT JOIN grn.tbl_proforma_rejection i ON a.StoreInvoiceID = i.ProformaStoreReceiptId ");
        sql.append("LEFT JOIN grn.tbl_proforma_rejection_detail j ON i.ProformaRejectionId = j.ProformaRejectionId AND b.Sno = j.Sno ");
        sql.append("LEFT JOIN grn.tbl_proforma_store_receipt_detail q ON i.ProformaStoreReceiptId = q.ProformaStoreReceiptId AND j.Sno = q.Sno ");
        sql.append("INNER JOIN pos.tbl_store f ON a.ToStoreId = f.StoreID ");
        
        sql.append("WHERE a.BusinessType = 'T' AND a.TransactionType IN ('I','D') ");
        sql.append("AND (b.Quantity + b.FreeQuantity) > 0 ");
        sql.append("AND (a.DeliveryChalanNo != 'INTERNAL' OR a.DeliveryChalanNo IS NULL) ");
        
        sql.append("AND a.").append(dateColumn).append(" >= :startDate AND a.").append(dateColumn).append(" <= :endDate ");
        params.addValue("startDate", startDate);
        params.addValue("endDate", endDate);

        if (periodKeys != null && !periodKeys.isEmpty()) {
            sql.append("AND DATE_FORMAT(a.").append(dateColumn).append(", '%Y%m') IN (:periodKeys) ");
            params.addValue("periodKeys", periodKeys);
        }

        if (supplierIds != null && !supplierIds.isEmpty()) {
            sql.append("AND a.FromAccountID IN (:supplierIds) ");
            params.addValue("supplierIds", supplierIds);
        }
        if (productIds != null && !productIds.isEmpty()) {
            sql.append("AND b.ProductID IN (:productIds) ");
            params.addValue("productIds", productIds);
        }
        if (storeIds != null && !storeIds.isEmpty()) {
            sql.append("AND a.ToStoreId IN (:storeIds) ");
            params.addValue("storeIds", storeIds);
        }
        if (stateCodes != null && !stateCodes.isEmpty()) {
            sql.append("AND f.Region_2 IN (:stateCodes) ");
            params.addValue("stateCodes", stateCodes);
        }

        return jdbcTemplate.queryForObject(sql.toString(), params, (rs, rowNum) ->
                new PurchaseTotals(rs.getLong("totalNetQty"), rs.getBigDecimal("totalNetValue")));
    }

    public record PurchaseTotals(long netQty, java.math.BigDecimal netValue) {}
}
