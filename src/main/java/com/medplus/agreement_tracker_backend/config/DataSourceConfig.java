package com.medplus.agreement_tracker_backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String primaryUrl;

    @Value("${spring.datasource.username}")
    private String primaryUsername;

    @Value("${spring.datasource.password}")
    private String primaryPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String primaryDriver;

    @Value("${spring.second-datasource.url}")
    private String secondUrl;

    @Value("${spring.second-datasource.username}")
    private String secondUsername;

    @Value("${spring.second-datasource.password}")
    private String secondPassword;

    @Value("${spring.second-datasource.driver-class-name}")
    private String secondDriver;

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
                .url(primaryUrl)
                .username(primaryUsername)
                .password(primaryPassword)
                .driverClassName(primaryDriver)
                .build();
    }

    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new JdbcTemplate(primaryDataSource);
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.second-datasource.hikari")
    public DataSource secondDataSource() {
        return DataSourceBuilder.create()
                .url(secondUrl)
                .username(secondUsername)
                .password(secondPassword)
                .driverClassName(secondDriver)
                .build();
    }

    @Bean(name = "secondJdbcTemplate")
    public JdbcTemplate secondJdbcTemplate(@Qualifier("secondDataSource") DataSource secondDataSource) {
        return new JdbcTemplate(secondDataSource);
    }

    @Bean(name = "secondNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate secondNamedParameterJdbcTemplate(@Qualifier("secondDataSource") DataSource secondDataSource) {
        return new NamedParameterJdbcTemplate(secondDataSource);
    }
}
