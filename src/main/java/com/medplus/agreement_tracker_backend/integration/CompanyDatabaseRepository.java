package com.medplus.agreement_tracker_backend.integration;

import com.medplus.agreement_tracker_backend.integration.dto.StoreDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class CompanyDatabaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompanyDatabaseRepository(@Qualifier("secondJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Use fully qualified table name as requested: schema_name.table_name
    private static final String STORE_TABLE = "company_db.stores";

    private static final RowMapper<StoreDetails> STORE_ROW_MAPPER = (rs, rowNum) -> StoreDetails.builder()
            .storeId(rs.getString("store_id"))
            .storeName(rs.getString("store_name"))
            .city(rs.getString("city"))
            .status(rs.getString("status"))
            .build();

    public Optional<StoreDetails> findStoreById(String storeId) {
        String sql = "SELECT store_id, store_name, city, status FROM " + STORE_TABLE + " WHERE store_id = ?";
        try {
            List<StoreDetails> results = jdbcTemplate.query(sql, STORE_ROW_MAPPER, storeId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error fetching store details from company database for storeId: {}", storeId, e);
            return Optional.empty();
        }
    }

    public List<StoreDetails> findAllStores() {
        String sql = "SELECT store_id, store_name, city, status FROM " + STORE_TABLE + " LIMIT 100";
        return jdbcTemplate.query(sql, STORE_ROW_MAPPER);
    }
}
