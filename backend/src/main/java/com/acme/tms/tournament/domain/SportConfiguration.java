package com.acme.tms.tournament.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * The strategy-pattern configuration record. {@code config} is held as raw JSON rather than mapped
 * fields: {@code rules} is deliberately open-ended per sport, and the shape is enforced by
 * {@code SportConfigurationValidator} on write plus check constraints in the database.
 */
@Entity
@Table(name = "sport_configuration")
public class SportConfiguration extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID organizationUnitId;

    @Column(nullable = false)
    private UUID sportId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String config;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private boolean isActive = true;

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
        this.sportId = sportId;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }
}
