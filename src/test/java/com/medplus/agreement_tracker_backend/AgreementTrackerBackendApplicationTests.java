package com.medplus.agreement_tracker_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AgreementTrackerBackendApplicationTests {

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate secondJdbcTemplate;

    @Test
    void testSecondJdbcTemplateConnection() {
        try {
            System.out.println("TESTING SECOND DATASOURCE CONNECTION...");
            Integer count = secondJdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.tbl_store", Integer.class);
            System.out.println("SUCCESS! Row count in pos.tbl_store: " + count);
        } catch (Exception e) {
            System.err.println("FAILED TO CONNECT TO SECOND DATASOURCE!");
            e.printStackTrace();
            throw e;
        }
    }

}

