package com.medplus.agreement_tracker_backend.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries country / state / city data from the secondary POS database.
 * Uses fully-qualified table names (pos.tbl_country, pos.tbl_state, pos.tbl_city).
 * Accepts list-based params so the frontend can batch-fetch states/cities for multiple
 * selected countries/states.
 */
@Repository
@Slf4j
public class PosLocationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PosLocationRepository(
            @Qualifier("secondNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Countries ─────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getCountries(String searchKey) {
        String trimmedKey = searchKey != null ? searchKey.trim() : "";
        log.info("POS DB: Fetching countries with searchKey: '{}'", trimmedKey);
        String sql = "SELECT CAST(c.CountryCode AS CHAR) as countryCode, " +
                     "       c.CountryName as countryName, c.SubName as countrySubName " +
                     "FROM pos.tbl_country c " +
                     "WHERE (:searchKey = '' " +
                     "   OR LOWER(c.CountryName) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
                     "   OR LOWER(c.SubName) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
                     "   OR CAST(c.CountryCode AS CHAR) LIKE CONCAT('%', :searchKey, '%')) " +
                     "ORDER BY c.CountryName";
        Map<String, Object> params = Map.of("searchKey", trimmedKey);
        try {
            List<Map<String, Object>> list = jdbc.query(sql, params, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("countryCode", rs.getString("countryCode"));
                map.put("countryName", rs.getString("countryName"));
                map.put("countrySubName", rs.getString("countrySubName"));
                return map;
            });
            log.info("POS DB: Found {} countries for searchKey '{}'", list.size(), trimmedKey);
            return list;
        } catch (Exception ex) {
            log.error("POS DB: failed to fetch countries for searchKey '{}'", trimmedKey, ex);
            return List.of();
        }
    }

    // ── States ────────────────────────────────────────────────────────────────

    /**
     * Returns states for one or more country codes and optional search key.
     */
    public List<Map<String, Object>> getStatesByCountryCodes(List<String> countryCodes, String searchKey) {
        String trimmedKey = searchKey != null ? searchKey.trim() : "";
        List<Long> codes = (countryCodes != null) ? countryCodes.stream().filter(s -> s != null && s.matches("\\d+")).map(Long::valueOf).toList() : List.of();
        log.info("POS DB: Fetching states for countries: {} with searchKey: '{}'", codes, trimmedKey);
        if (codes.isEmpty() && trimmedKey.isEmpty()) {
            return List.of();
        }

        String sql = "SELECT CAST(c.CountryCode AS CHAR) as countryCode, " +
                     "       c.CountryName as countryName, " +
                     "       c.SubName as countrySubName, " +
                     "       CAST(s.StateCode AS CHAR) as stateCode, " +
                     "       s.StateName as stateName, s.SubName as stateSubName " +
                     "FROM pos.tbl_state s " +
                     "JOIN pos.tbl_country c ON s.CountryCode = c.CountryCode " +
                     "WHERE (:hasCodes = FALSE OR s.CountryCode IN (:countryCodes)) " +
                     "  AND (:searchKey = '' " +
                     "       OR LOWER(s.StateName) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
                     "       OR LOWER(s.SubName) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
                     "       OR CAST(s.StateCode AS CHAR) LIKE CONCAT('%', :searchKey, '%')) " +
                     "ORDER BY s.StateName";
        Map<String, Object> params = new HashMap<>();
        params.put("hasCodes", !codes.isEmpty());
        params.put("countryCodes", codes.isEmpty() ? List.of(0L) : codes);
        params.put("searchKey", trimmedKey);
        try {
            List<Map<String, Object>> list = jdbc.query(sql, params, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("countryCode", rs.getString("countryCode"));
                map.put("countryName", rs.getString("countryName"));
                map.put("countrySubName", rs.getString("countrySubName"));
                map.put("stateCode", rs.getString("stateCode"));
                map.put("stateName", rs.getString("stateName"));
                map.put("stateSubName", rs.getString("stateSubName"));
                return map;
            });
            log.info("POS DB: Found {} states for countries {} and searchKey '{}'", list.size(), codes, trimmedKey);
            return list;
        } catch (Exception ex) {
            log.error("POS DB: failed to fetch states for countries {} and searchKey '{}'", codes, trimmedKey, ex);
            return List.of();
        }
    }

    // ── Cities ────────────────────────────────────────────────────────────────

    /**
     * Returns cities for one or more state codes and optional search key.
     */
    public List<Map<String, Object>> getCitiesByStateCodes(List<String> stateCodes, String searchKey) {
        String trimmedKey = searchKey != null ? searchKey.trim() : "";
        List<Long> codes = (stateCodes != null) ? stateCodes.stream().filter(s -> s != null && s.matches("\\d+")).map(Long::valueOf).toList() : List.of();
        log.info("POS DB: Fetching cities for states: {} with searchKey: '{}'", codes, trimmedKey);
        if (codes.isEmpty() && trimmedKey.isEmpty()) {
            return List.of();
        }

        String sql = "SELECT CAST(co.CountryCode AS CHAR) as countryCode, " +
                     "       co.CountryName as countryName, " +
                     "       co.SubName as countrySubName, " +
                     "       CAST(s.StateCode AS CHAR) as stateCode, " +
                     "       s.StateName as stateName, s.SubName as stateSubName, " +
                     "       CAST(c.CityCode AS CHAR) as cityCode, " +
                     "       c.CityName as cityName, c.SubName as citySubName " +
                     "FROM pos.tbl_city c " +
                     "JOIN pos.tbl_state s ON s.StateCode = c.StateCode " +
                     "JOIN pos.tbl_country co ON co.CountryCode = s.CountryCode " +
                     "WHERE (:hasCodes = FALSE OR c.StateCode IN (:stateCodes)) " +
                     "  AND (:searchKey = '' " +
                     "       OR LOWER(c.CityName) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
                     "       OR LOWER(c.SubName) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
                     "       OR CAST(c.CityCode AS CHAR) LIKE CONCAT('%', :searchKey, '%')) " +
                     "ORDER BY c.CityName";
        Map<String, Object> params = new HashMap<>();
        params.put("hasCodes", !codes.isEmpty());
        params.put("stateCodes", codes.isEmpty() ? List.of(0L) : codes);
        params.put("searchKey", trimmedKey);
        try {
            List<Map<String, Object>> list = jdbc.query(sql, params, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("countryCode", rs.getString("countryCode"));
                map.put("countryName", rs.getString("countryName"));
                map.put("countrySubName", rs.getString("countrySubName"));
                map.put("stateCode", rs.getString("stateCode"));
                map.put("stateName", rs.getString("stateName"));
                map.put("stateSubName", rs.getString("stateSubName"));
                map.put("cityCode", rs.getString("cityCode"));
                map.put("cityName", rs.getString("cityName"));
                map.put("citySubName", rs.getString("citySubName"));
                return map;
            });
            log.info("POS DB: Found {} cities for states {} and searchKey '{}'", list.size(), codes, trimmedKey);
            return list;
        } catch (Exception ex) {
            log.error("POS DB: failed to fetch cities for states {} and searchKey '{}'", codes, trimmedKey, ex);
            return List.of();
        }
    }
}
