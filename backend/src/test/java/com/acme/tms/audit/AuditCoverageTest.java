package com.acme.tms.audit;

import com.acme.tms.common.audit.Audited;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The R9 mitigation from the roadmap, and the DoD's "verified by an architecture test": every
 * mutating service method either writes an audit row or is on a list that says out loud why not.
 *
 * <p>Manual audit calls are historically the most gap-prone mechanism there is (ADR-014), so the
 * gap is closed mechanically. A new mutating service method fails this test until someone decides
 * what it is called in the trail — which is the point.
 */
class AuditCoverageTest {

    /**
     * Methods that mutate but are deliberately not audited, each with the reason. Anything added
     * here should be uncomfortable to add.
     */
    private static final Set<String> EXEMPT = Set.of(
        // Authentication events, not entity mutations. Auditing every login would bury the business
        // trail in noise; sessions are their own concern and are tracked by refresh-token records.
        "AuthService.bootstrapRegister",
        "AuthService.login",
        "AuthService.refresh",
        "AuthService.logout",
        "AuthService.acceptInvite",
        "RefreshTokenService.issue",
        "RefreshTokenService.rotate",
        "RefreshTokenService.logout",

        // Writes a row *about* another entity's decision; the decision itself is audited on the
        // registration by RegistrationApprovalService.
        "ApprovalInstanceService.open",
        "ApprovalInstanceService.act",
        "ApprovalInstanceService.cancelFor",

        // Recomputed derived data (BR-LE-2), not a user action. The result that triggered it is
        // audited, and auditing the recompute would double every scoreline.
        "LeaderboardService.recompute"
    );

    private List<Class<?>> serviceClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.acme.tms")) {
            String name = definition.getBeanClassName();
            if (name == null || !name.contains(".service.")) {
                continue;
            }
            try {
                Class<?> type = Class.forName(name);
                if (type.isAnnotationPresent(Service.class)) {
                    classes.add(type);
                }
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Scanned a class that will not load: " + name, exception);
            }
        }
        return classes;
    }

    /**
     * A method is treated as mutating when it opens a read-write transaction. That is the same
     * signal the database sees, so it cannot drift from reality the way a naming convention would.
     */
    private boolean isMutating(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        if (transactional == null || transactional.readOnly()) {
            return false;
        }
        return transactional.propagation() != Propagation.SUPPORTS
            && transactional.propagation() != Propagation.NOT_SUPPORTED;
    }

    @Test
    void everyMutatingServiceMethodIsAuditedOrExplicitlyExempt() {
        List<String> unaudited = new ArrayList<>();

        for (Class<?> type : serviceClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || !isMutating(method)) {
                    continue;
                }
                String name = type.getSimpleName() + "." + method.getName();
                if (!method.isAnnotationPresent(Audited.class) && !EXEMPT.contains(name)) {
                    unaudited.add(name);
                }
            }
        }

        assertThat(unaudited)
            .as("mutating service methods with no @Audited and no entry in EXEMPT — add one or the "
                + "other; a silent gap in the trail is the failure mode this test exists to prevent")
            .isEmpty();
    }

    @Test
    void theScanFindsTheServicesItIsSupposedToBeChecking() {
        // A scanner that silently matched nothing would make the test above pass forever.
        assertThat(serviceClasses())
            .as("service classes discovered")
            .hasSizeGreaterThan(10);
    }

    @Test
    void theExemptionListDoesNotRotAroundMethodsThatNoLongerExist() {
        // An exemption for a deleted method is a licence nobody is watching.
        List<String> allMutating = new ArrayList<>();
        for (Class<?> type : serviceClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && isMutating(method)) {
                    allMutating.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(allMutating)
            .as("every EXEMPT entry still names a real mutating method")
            .containsAll(EXEMPT);
    }
}
