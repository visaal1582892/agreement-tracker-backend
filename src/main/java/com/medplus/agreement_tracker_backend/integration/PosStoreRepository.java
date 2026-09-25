package com.medplus.agreement_tracker_backend.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class PosStoreRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PosStoreRepository(
            @Qualifier("secondNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> searchStores(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String trimmed = keyword.trim();
        boolean isBulk = trimmed.contains(",") || trimmed.contains("\n") || trimmed.matches("^(\\d+[\\s,]+)+\\d+$");
        if (isBulk) {
            List<String> ids = java.util.Arrays.stream(trimmed.split("[\\s,]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!ids.isEmpty()) {
                String query = "SELECT ID as id, StoreID as storeId, Name as name, Address as address, Status as status, " +
                        "PinCode as pinCode, Region_1 as region1, Region_2 as region2, Region_3 as region3 " +
                        "FROM pos.tbl_store " +
                        "WHERE (StoreID IN (:ids) OR CAST(ID AS CHAR) IN (:ids)) AND Status='A' LIMIT 100";
                Map<String, Object> params = new HashMap<>();
                params.put("ids", ids);
                return jdbc.queryForList(query, params);
            }
        }
        String query = "SELECT ID as id, StoreID as storeId, Name as name, Address as address, Status as status, " +
                "PinCode as pinCode, Region_1 as region1, Region_2 as region2, Region_3 as region3 " +
                "FROM pos.tbl_store " +
                "WHERE (StoreID LIKE :keyword OR Name LIKE :keyword OR CAST(PinCode AS CHAR) LIKE :keyword) AND Status='A' LIMIT 50";
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "%" + trimmed + "%");
        return jdbc.queryForList(query, params);
    }
}
