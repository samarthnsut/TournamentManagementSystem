package com.acme.tms.fixture.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.exception.ResourceNotFoundException;

import com.acme.tms.fixture.repository.MatchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Audit snapshot for a Match. Includes the result, so a corrected scoreline shows both the value it
 * had and the value it was changed to — which is the whole point of auditing a result (BR-RES-3).
 */
/*
 * Providers must not throw. A ResourceNotFoundException raised inside a nested @Transactional
 * service method marks the caller's transaction rollback-only *before* the audit aspect can catch
 * it, which would take down the very operation being recorded. Existence is therefore checked
 * against the repository first, and the throwing read is only reached when it cannot throw.
 */
@Component
public class MatchAuditSnapshots implements AuditSnapshotProvider {

    public static final String MATCH = "Match";

    private final MatchService matchService;
    private final MatchRepository matchRepository;

    public MatchAuditSnapshots(MatchService matchService, MatchRepository matchRepository) {
        this.matchService = matchService;
        this.matchRepository = matchRepository;
    }

    @Override
    public String entityType() {
        return MATCH;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Object> snapshot(UUID entityId) {
        if (matchRepository.findById(entityId).isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(matchService.get(entityId));
        } catch (ResourceNotFoundException exception) {
            return Optional.empty();
        }
    }
}
