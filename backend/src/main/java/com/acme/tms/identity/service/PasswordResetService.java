package com.acme.tms.identity.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.util.RandomTokenGenerator;
import com.acme.tms.common.util.Sha256;
import com.acme.tms.identity.domain.RefreshToken;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.domain.UserStatus;
import com.acme.tms.identity.dto.ForgotPasswordRequest;
import com.acme.tms.identity.dto.ResetPasswordRequest;
import com.acme.tms.identity.repository.RefreshTokenRepository;
import com.acme.tms.identity.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Forgotten-password recovery.
 *
 * <p>The governing rule is that an anonymous caller learns nothing about who has an account.
 * {@link #forgotPassword} therefore behaves identically whether the address is registered or not —
 * no distinct error, no different status, and the caller is told the same thing either way.
 * Anything else turns this endpoint into a way to enumerate a tenant's users.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Short by design: a reset link is a live credential sitting in an inbox. */
    private static final long TOKEN_TTL_MINUTES = 60;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public PasswordResetService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        ApplicationEventPublisher events
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    /**
     * Issues a reset link if — and only if — the address belongs to an account that can sign in.
     * Returns nothing in every case.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> found = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);

        if (found.isEmpty()) {
            // Logged, not returned. The caller gets the same answer as a successful request.
            log.info("Password reset requested for an address with no account");
            return;
        }

        User user = found.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // An invited-but-not-accepted user resets nothing — they finish their invite instead.
            // A suspended one must not be able to let themselves back in.
            log.info("Password reset requested for a {} account; ignoring", user.getStatus());
            return;
        }

        String token = RandomTokenGenerator.generate();
        user.setPasswordResetTokenHash(Sha256.hash(token));
        user.setPasswordResetExpiresAt(Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));

        events.publishEvent(new PasswordResetRequestedEvent(
            user.getId(), user.getEmail(), user.getFullName(), token, TOKEN_TTL_MINUTES));
    }

    /**
     * Consumes the token, sets the password, and ends every existing session.
     *
     * <p>Someone resetting a password has usually lost control of the old one. Leaving live refresh
     * tokens behind would let whoever prompted the reset carry on regardless — the same reasoning
     * as a deliberate password change.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository
            .findByPasswordResetTokenHashAndDeletedAtIsNull(Sha256.hash(request.token()))
            .orElseThrow(() -> new ResourceNotFoundException(
                "RESET_TOKEN_NOT_FOUND", "This reset link is not valid."));

        if (user.getPasswordResetExpiresAt() == null
            || user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("RESET_TOKEN_EXPIRED", "This reset link has expired.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Cleared on use, so the link works exactly once.
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);

        for (RefreshToken token : refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())) {
            token.revoke();
        }
    }
}
