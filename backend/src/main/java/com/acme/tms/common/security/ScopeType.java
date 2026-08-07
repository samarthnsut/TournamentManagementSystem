package com.acme.tms.common.security;

public enum ScopeType {
    GLOBAL,
    ORGANIZATION,
    TOURNAMENT,
    COMPETITION,

    /**
     * A target only, never a grant: match endpoints are addressed by match id, and the authority to
     * act on one is inherited from its competition. The role tables deliberately do not accept it,
     * so resolving through {@code MatchScopeResolver} is the only way it can be satisfied.
     */
    MATCH
}
