package com.acme.tms.fixture.strategy;

/**
 * Closed set, frozen by ARCHITECTURE_BRIEF section 7. Adding a format means adding a constant here
 * and one {@link FixtureGenerator} implementation — never a branch in domain code.
 */
public enum FixtureGeneratorKey {
    ROUND_ROBIN,
    SINGLE_ELIMINATION,
    DOUBLE_ELIMINATION,
    SWISS,
    NONE
}
