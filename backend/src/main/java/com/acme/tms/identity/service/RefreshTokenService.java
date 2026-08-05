package com.acme.tms.identity.service;

import com.acme.tms.common.exception.AuthenticationException;
import com.acme.tms.common.security.JwtService;
import com.acme.tms.common.util.RandomTokenGenerator;
import com.acme.tms.common.util.Sha256;
import com.acme.tms.common.util.UuidV7Generator;
import com.acme.tms.identity.domain.RefreshToken;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final TransactionTemplate independentTransaction;

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        JwtService jwtService,
        PlatformTransactionManager transactionManager
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public TokenPair issue(User user) {
        return issue(user, UuidV7Generator.generate());
    }

    @Transactional
    public TokenPair rotate(String presentedRefreshToken, User user) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(Sha256.hash(presentedRefreshToken))
            .orElseThrow(() -> new AuthenticationException("REFRESH_TOKEN_INVALID", "Refresh token is invalid."));

        if (!existing.isActive()) {
            revokeStolenFamily(existing.getFamilyId());
            throw new AuthenticationException("REFRESH_TOKEN_INVALID", "Refresh token is expired or revoked.");
        }

        existing.revoke();
        return issue(user, existing.getFamilyId());
    }

    @Transactional
    public void logout(String presentedRefreshToken) {
        refreshTokenRepository.findByTokenHash(Sha256.hash(presentedRefreshToken))
            .ifPresent(token -> revokeFamily(token.getFamilyId()));
    }

    public RefreshToken requireActive(String presentedRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(Sha256.hash(presentedRefreshToken))
            .orElseThrow(() -> new AuthenticationException("REFRESH_TOKEN_INVALID", "Refresh token is invalid."));

        if (!token.isActive()) {
            revokeStolenFamily(token.getFamilyId());
            throw new AuthenticationException("REFRESH_TOKEN_INVALID", "Refresh token is expired or revoked.");
        }

        return token;
    }

    /**
     * Presenting a spent token means the family leaked, so every sibling dies with it. This must
     * commit on its own: the caller's transaction is about to be rolled back by the 401 we throw
     * next, which would otherwise undo the revocation and leave the stolen token usable.
     */
    private void revokeStolenFamily(UUID familyId) {
        independentTransaction.executeWithoutResult(status -> revokeFamily(familyId));
    }

    private TokenPair issue(User user, UUID familyId) {
        String refreshToken = RandomTokenGenerator.generate();

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(Sha256.hash(refreshToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(jwtService.refreshTokenTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(token);

        return new TokenPair(
            jwtService.issueAccessToken(user.getId(), user.getEmail()),
            refreshToken,
            jwtService.accessTokenTtlSeconds()
        );
    }

    private void revokeFamily(UUID familyId) {
        refreshTokenRepository.findByFamilyId(familyId).forEach(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke();
            }
        });
    }
}

