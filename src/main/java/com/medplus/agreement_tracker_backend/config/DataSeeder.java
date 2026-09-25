package com.medplus.agreement_tracker_backend.config;

import com.medplus.agreement_tracker_backend.entity.*;
import com.medplus.agreement_tracker_backend.enums.DiscountCalculationKind;
import com.medplus.agreement_tracker_backend.enums.RightCode;
import com.medplus.agreement_tracker_backend.enums.RoleName;
import com.medplus.agreement_tracker_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final IncomeTypeRepository incomeTypeRepository;
    private final AgreementTypeRepository agreementTypeRepository;
    private final RightRepository rightRepository;
    private final RoleRightRepository roleRightRepository;
    private final ChannelMasterRepository channelRepository;
    private final DiscountTypeMasterRepository discountTypeRepository;
    private final PriceOffLocationMasterRepository priceOffLocationMasterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private static final String TEST_PASSWORD = "medplus@123";

    @Override
    public void run(String... args) {
        try {
            cleanupDeprecatedRolesAndUsers();
            seedRoles();
            seedTestUsers();
            syncSeedUserPasswords();
            seedLookups();
            seedAgreementTypes();
            seedRights();
            seedRoleRights();
            log.info("Data seeding complete");
        } catch (Exception ex) {
            log.error("Data seeding failed — login and master data may be unavailable", ex);
            throw ex;
        }
    }

    private void seedRoles() {
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                Role role = Role.builder().name(name).description(name.name() + " role").build();
                roleRepository.save(role);
            }
        }
    }

    private void seedTestUsers() {
        seedUserWithRole("admin", "System Administrator", "admin@medplus.com", "EMP001", TEST_PASSWORD, RoleName.ADMIN);
        seedUserWithRole("amgr", "Ravi Kumar", "ravi.kumar@medplus.com", "EMP002", TEST_PASSWORD, RoleName.ACCOUNT_MANAGER);
        seedUserWithRole("approver", "Priya Sharma", "priya.sharma@medplus.com", "EMP003", TEST_PASSWORD, RoleName.APPROVER);
    }

    private void cleanupDeprecatedRolesAndUsers() {
        jdbcTemplate.update("""
                DELETE ur FROM user_roles ur
                INNER JOIN users u ON ur.user_id = u.id
                WHERE u.username IN ('leader', 'finance')
                """);
        jdbcTemplate.update("""
                UPDATE users SET is_active = 0 WHERE username IN ('leader', 'finance')
                """);
        jdbcTemplate.update("""
                DELETE ur FROM user_roles ur
                INNER JOIN roles r ON ur.role_id = r.id
                WHERE r.name IN ('LEADERSHIP', 'FINANCE')
                """);
        jdbcTemplate.update("""
                DELETE rr FROM role_rights rr
                INNER JOIN roles r ON rr.role_id = r.id
                WHERE r.name IN ('LEADERSHIP', 'FINANCE')
                """);
        jdbcTemplate.update("""
                DELETE FROM roles WHERE name IN ('LEADERSHIP', 'FINANCE')
                """);
        log.info("Deprecated LEADERSHIP/FINANCE roles and users removed");
    }

    private void syncSeedUserPasswords() {
        String encodedPassword = passwordEncoder.encode(TEST_PASSWORD);
        List<String> seedUsernames = List.of("admin", "amgr", "approver");
        for (String username : seedUsernames) {
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setPasswordHash(encodedPassword);
                userRepository.save(user);
            });
        }
        log.info("Seed user passwords synchronized");
    }

    private void seedUserWithRole(
            String username,
            String fullName,
            String email,
            String employeeId,
            String rawPassword,
            RoleName roleName) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User user = User.builder()
                .username(username)
                .fullName(fullName)
                .email(email)
                .employeeId(employeeId)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .isActive(true)
                .build();
        user = userRepository.save(user);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());

        log.info("Test user created: {} / {} ({})", username, rawPassword, roleName);
    }

    private void seedLookups() {
        List<String> incomeTypes = List.of(
                "Data Fee",
                "Commercial Contracts",
                "Asset Rentals",
                "Ad-Hoc Activities"
        );
        for (String name : incomeTypes) {
            if (!incomeTypeRepository.existsByNameIgnoreCase(name)) {
                incomeTypeRepository.save(IncomeType.builder().name(name).build());
            }
        }

    }

    private void seedAgreementTypes() {
        List<String> allowedTypes = List.of("Agreement", "MOU", "Email Confirmation");
        for (String name : allowedTypes) {
            agreementTypeRepository.findAll().stream()
                    .filter(type -> type.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> {
                                if (!existing.isActive()) {
                                    existing.setActive(true);
                                    agreementTypeRepository.save(existing);
                                }
                            },
                            () -> agreementTypeRepository.save(AgreementType.builder().name(name).build())
                    );
        }
        agreementTypeRepository.findAll().forEach(type -> {
            boolean allowed = allowedTypes.stream()
                    .anyMatch(name -> name.equalsIgnoreCase(type.getName()));
            if (!allowed && type.isActive()) {
                type.setActive(false);
                agreementTypeRepository.save(type);
            }
        });
    }

    private void seedPriceOffLocationMaster() {
        List<String> virtualZones = List.of(
                "AP", "KA", "NAG", "OR", "PNQ", "PY", "TG", "TN", "WB", "MP", "CG", "KL", "DL", "UP", "HY");
        for (String code : virtualZones) {
            priceOffLocationMasterRepository.findByCodeIgnoreCase(code)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setName(code);
                                if (!existing.isActive()) {
                                    existing.setActive(true);
                                }
                                priceOffLocationMasterRepository.save(existing);
                            },
                            () -> priceOffLocationMasterRepository.save(PriceOffLocationMaster.builder()
                                    .name(code)
                                    .code(code)
                                    .build()));
        }
        priceOffLocationMasterRepository.findAll().forEach(location -> {
            boolean allowed = virtualZones.stream()
                    .anyMatch(code -> code.equalsIgnoreCase(location.getCode()));
            if (!allowed && location.isActive()) {
                location.setActive(false);
                priceOffLocationMasterRepository.save(location);
            }
        });
    }

    private void seedChannelMaster() {
        List<String[]> channels = List.of(
                new String[]{"RETAIL", "Retail Pharmacy"},
                new String[]{"ECOM", "E-Commerce"},
                new String[]{"B2B", "B2B Institutional"},
                new String[]{"HOSP", "Hospital Channel"}
        );
        for (String[] seed : channels) {
            channelRepository.findByChannelNameIgnoreCaseAndIsActiveTrue(seed[1])
                    .ifPresentOrElse(
                            existing -> {
                                existing.setChannelCode(seed[0]);
                                channelRepository.save(existing);
                            },
                            () -> channelRepository.save(ChannelMaster.builder()
                                    .channelCode(seed[0])
                                    .channelName(seed[1])
                                    .build()));
        }
    }

    private void seedDiscountTypeMaster() {
        List<Object[]> types = List.of(
                new Object[]{"PERCENTAGE", "Percentage Off", DiscountCalculationKind.PERCENTAGE},
                new Object[]{"FIXED_AMOUNT", "Fixed Amount Off", DiscountCalculationKind.FIXED_AMOUNT}
        );
        for (Object[] seed : types) {
            String code = (String) seed[0];
            String name = (String) seed[1];
            DiscountCalculationKind kind = (DiscountCalculationKind) seed[2];
            discountTypeRepository.findByDiscountCodeIgnoreCaseAndIsActiveTrue(code)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setDiscountName(name);
                                existing.setCalculationKind(kind);
                                discountTypeRepository.save(existing);
                            },
                            () -> discountTypeRepository.save(DiscountTypeMaster.builder()
                                    .discountCode(code)
                                    .discountName(name)
                                    .calculationKind(kind)
                                    .build()));
        }
    }

    private void seedRights() {
        List<Right> defaults = List.of(
                right(RightCode.DASHBOARD_VIEW, "View Dashboard", "DASHBOARD"),
                right(RightCode.AGREEMENT_VIEW, "View Agreements", "AGREEMENTS"),
                right(RightCode.AGREEMENT_VIEW_ALL, "View All Agreements", "AGREEMENTS"),
                right(RightCode.AGREEMENT_CREATE, "Create Agreements", "AGREEMENTS"),
                right(RightCode.AGREEMENT_EDIT, "Edit Agreements", "AGREEMENTS"),
                right(RightCode.AGREEMENT_APPROVE, "Approve Agreements", "AGREEMENTS"),
                right(RightCode.MASTER_VIEW, "View Master Data", "MASTER"),
                right(RightCode.MASTER_MANAGE, "Manage Master Data", "MASTER"),
                right(RightCode.ADMIN_USERS, "Manage Users", "ADMIN"),
                right(RightCode.PRICE_OFF_VIEW, "View Price Off Campaigns", "PRICE_OFFS"),
                right(RightCode.PRICE_OFF_MANAGE, "Manage Price Off Campaigns", "PRICE_OFFS"),
                right(RightCode.PRICE_OFF_APPROVE, "Approve Price Off Campaigns", "PRICE_OFFS"),
                right(RightCode.COMMERCIAL_PAYOUT_CALCULATE, "Calculate Commercial Payouts", "AGREEMENTS")
        );
        for (Right right : defaults) {
            if (rightRepository.findByCode(right.getCode()).isEmpty()) {
                rightRepository.save(right);
            }
        }
    }

    private Right right(RightCode code, String name, String module) {
        return Right.builder().code(code.name()).name(name).module(module).build();
    }

    private void seedRoleRights() {
        roleRightRepository.deleteAllInBatch();

        Map<RoleName, List<String>> mappings = Map.of(
                RoleName.ADMIN, List.of(
                        RightCode.DASHBOARD_VIEW.name(),
                        RightCode.MASTER_MANAGE.name(),
                        RightCode.MASTER_VIEW.name(),
                        RightCode.ADMIN_USERS.name()
                ),
                RoleName.ACCOUNT_MANAGER, List.of(
                        RightCode.AGREEMENT_VIEW.name(),
                        RightCode.AGREEMENT_CREATE.name(),
                        RightCode.AGREEMENT_EDIT.name(),
                        RightCode.MASTER_VIEW.name(),
                        RightCode.DASHBOARD_VIEW.name(),
                        RightCode.PRICE_OFF_VIEW.name(),
                        RightCode.PRICE_OFF_MANAGE.name()
                ),
                RoleName.APPROVER, List.of(
                        RightCode.AGREEMENT_VIEW_ALL.name(),
                        RightCode.AGREEMENT_APPROVE.name(),
                        RightCode.MASTER_VIEW.name(),
                        RightCode.DASHBOARD_VIEW.name(),
                        RightCode.PRICE_OFF_APPROVE.name(),
                        RightCode.COMMERCIAL_PAYOUT_CALCULATE.name()
                )
        );

        mappings.forEach((roleName, rightCodes) -> {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            for (String code : rightCodes) {
                Right right = rightRepository.findByCode(code).orElseThrow();
                roleRightRepository.save(RoleRight.builder().role(role).right(right).build());
            }
        });

        log.info("Role-rights seeded: ADMIN(4), ACCOUNT_MANAGER(7), APPROVER(6)");
    }
}
