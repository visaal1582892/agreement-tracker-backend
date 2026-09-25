package com.medplus.agreement_tracker_backend.integration;

import com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@Slf4j
public class StoreLocationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public StoreLocationRepository(@Qualifier("secondJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static final String STORE_TABLE = "pos.tbl_store";

    private static final RowMapper<StoreLocationDetails> ROW_MAPPER = (rs, rowNum) -> StoreLocationDetails.builder()
            .storeId(rs.getString("StoreID"))
            .name(rs.getString("Name"))
            .region1(rs.getString("Region_1"))
            .region2(rs.getString("Region_2"))
            .region3(rs.getString("Region_3"))
            .pinCode(rs.getObject("PinCode") != null ? rs.getInt("PinCode") : null)
            .address(rs.getString("Address"))
            .build();

    public List<StoreLocationDetails> findActiveStoresByIds(Set<String> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = "SELECT StoreID, Name, Region_1, Region_2, Region_3, PinCode, Address " +
                     "FROM " + STORE_TABLE + " " +
                     "WHERE Status = 'A' AND StoreID IN (:storeIds)";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("storeIds", storeIds);

        try {
            return jdbcTemplate.query(sql, parameters, ROW_MAPPER);
        } catch (Exception e) {
            log.error("Error fetching active store location details from secondary DB for storeIds: {}", storeIds, e);
            return Collections.emptyList();
        }
    }

    public List<StoreLocationDetails> searchActiveStoresByIds(Set<String> storeIds, String search, int offset, int limit) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder("SELECT StoreID, Name, Region_1, Region_2, Region_3, PinCode, Address ");
        sql.append("FROM ").append(STORE_TABLE).append(" ");
        sql.append("WHERE Status = 'A' AND StoreID IN (:storeIds) ");

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("storeIds", storeIds);
        
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (StoreID LIKE :search OR Name LIKE :search) ");
            parameters.addValue("search", "%" + search.trim() + "%");
        }

        sql.append("ORDER BY StoreID ASC LIMIT :limit OFFSET :offset");
        parameters.addValue("limit", limit);
        parameters.addValue("offset", offset);

        try {
            return jdbcTemplate.query(sql.toString(), parameters, ROW_MAPPER);
        } catch (Exception e) {
            log.error("Error searching active store location details", e);
            return Collections.emptyList();
        }
    }

    public long countActiveStoresByIds(Set<String> storeIds, String search) {
        if (storeIds == null || storeIds.isEmpty()) {
            return 0L;
        }

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) ");
        sql.append("FROM ").append(STORE_TABLE).append(" ");
        sql.append("WHERE Status = 'A' AND StoreID IN (:storeIds) ");

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("storeIds", storeIds);
        
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (StoreID LIKE :search OR Name LIKE :search) ");
            parameters.addValue("search", "%" + search.trim() + "%");
        }

        try {
            Long count = jdbcTemplate.queryForObject(sql.toString(), parameters, Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Error counting active store location details", e);
            return 0L;
        }
    }
}
