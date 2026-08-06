package com.acme.tms.common.domain;

/**
 * Who competes. Lives in {@code common} because three modules need it — {@code tournament} stores
 * it on a SportConfiguration, {@code registration} types its Participants by it, and the
 * {@code fixture} strategies declare which ones they support.
 */
public enum ParticipantType {
    INDIVIDUAL,
    TEAM,
    ORGANIZATION
}
