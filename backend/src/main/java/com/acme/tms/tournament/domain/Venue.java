package com.acme.tms.tournament.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A place matches are played. The table has existed since Sprint 3; Sprint 6 is the first thing
 * that needs to read one, so this maps only what match scheduling asks about. Venue management
 * endpoints are not part of this sprint.
 */
@Entity
@Table(name = "venue")
public class Venue extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID organizationUnitId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    private Integer capacity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String facilities;

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getFacilities() {
        return facilities;
    }

    public void setFacilities(String facilities) {
        this.facilities = facilities;
    }
}
