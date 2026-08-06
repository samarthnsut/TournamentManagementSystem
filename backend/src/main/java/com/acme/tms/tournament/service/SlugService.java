package com.acme.tms.tournament.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.util.SlugUtil;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Service;

/**
 * Tournament slugs are platform-unique because they are the public URL at {@code /t/{slug}}.
 * Uniqueness is also enforced by a partial unique index, so a race that slips past the check here
 * still fails at the database rather than producing two tournaments on one URL.
 */
@Service
public class SlugService {

    private final TournamentRepository tournamentRepository;

    public SlugService(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    /** Uses the requested slug when given, otherwise derives a free one from the name. */
    public String resolve(String requestedSlug, String name) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            return generateFrom(name);
        }

        String slug = requestedSlug.trim();
        if (tournamentRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ConflictException("SLUG_TAKEN", "Tournament slug '" + slug + "' is already in use.");
        }
        return slug;
    }

    private String generateFrom(String name) {
        String base = SlugUtil.from(name, "tournament");
        String candidate = base;
        int suffix = 2;

        while (tournamentRepository.existsBySlugAndDeletedAtIsNull(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }

        return candidate;
    }
}
