package com.acme.tms.common.config;

import com.acme.tms.common.security.ScopeType;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.domain.UserStatus;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.identity.service.RoleAssignmentService;
import com.acme.tms.organization.domain.OrganizationUnitType;
import com.acme.tms.organization.dto.CreateOrganizationUnitRequest;
import com.acme.tms.organization.service.OrganizationUnitService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * The roadmap's demo tree (SAI → 2 states → 2 districts) with one user per role. Runs through the
 * real services so seeded accounts are guaranteed to behave like registered ones.
 */
@Configuration
@Profile("dev")
public class DevDataSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String PASSWORD = "StrongPass123";

    @Bean
    ApplicationRunner seedDevData(
        UserRepository userRepository,
        OrganizationUnitService organizationUnitService,
        RoleAssignmentService roleAssignmentService,
        PasswordEncoder passwordEncoder
    ) {
        return args -> seed(userRepository, organizationUnitService, roleAssignmentService, passwordEncoder);
    }

    void seed(
        UserRepository userRepository,
        OrganizationUnitService organizationUnitService,
        RoleAssignmentService roleAssignmentService,
        PasswordEncoder passwordEncoder
    ) {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("super.admin@example.com")) {
            return;
        }

        UUID sai = organizationUnitService.create(new CreateOrganizationUnitRequest(
            null, "Sports Authority of India", "sai", OrganizationUnitType.FEDERATION)).id();
        UUID haryana = organizationUnitService.create(new CreateOrganizationUnitRequest(
            sai, "Haryana State Association", "haryana", OrganizationUnitType.STATE_ASSOCIATION)).id();
        UUID punjab = organizationUnitService.create(new CreateOrganizationUnitRequest(
            sai, "Punjab State Association", "punjab", OrganizationUnitType.STATE_ASSOCIATION)).id();
        UUID sonipat = organizationUnitService.create(new CreateOrganizationUnitRequest(
            haryana, "Sonipat District Association", "sonipat-district", OrganizationUnitType.DISTRICT_ASSOCIATION)).id();
        organizationUnitService.create(new CreateOrganizationUnitRequest(
            punjab, "Ludhiana District Association", "ludhiana-district", OrganizationUnitType.DISTRICT_ASSOCIATION));

        grant(userRepository, roleAssignmentService, passwordEncoder,
            "super.admin@example.com", "Platform Super Admin", "SUPER_ADMIN", ScopeType.GLOBAL, null);
        grant(userRepository, roleAssignmentService, passwordEncoder,
            "haryana.admin@example.com", "Haryana Tenant Admin", "TENANT_ADMIN", ScopeType.ORGANIZATION, haryana);
        grant(userRepository, roleAssignmentService, passwordEncoder,
            "punjab.admin@example.com", "Punjab Tenant Admin", "TENANT_ADMIN", ScopeType.ORGANIZATION, punjab);
        grant(userRepository, roleAssignmentService, passwordEncoder,
            "sonipat.official@example.com", "Sonipat Official", "ORG_OFFICIAL", ScopeType.ORGANIZATION, sonipat);
        grant(userRepository, roleAssignmentService, passwordEncoder,
            "participant@example.com", "Sample Participant", "PARTICIPANT_USER", ScopeType.GLOBAL, null);

        LOG.info("Dev seed loaded. Every seeded account uses the password {}", PASSWORD);
    }

    private void grant(
        UserRepository userRepository,
        RoleAssignmentService roleAssignmentService,
        PasswordEncoder passwordEncoder,
        String email,
        String fullName,
        String roleCode,
        ScopeType scopeType,
        UUID scopeId
    ) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);

        roleAssignmentService.grant(userRepository.save(user).getId(), roleCode, scopeType, scopeId);
    }
}
