package com.acme.tms.registration.domain;

import com.acme.tms.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The answers given for one registration, pinned to the form version that validated them. The pin
 * is the whole point: it never migrates to a newer version (BR-RR-2).
 */
@Entity
@Table(name = "registration_response")
public class RegistrationResponse extends BaseEntity {

    @Column(nullable = false)
    private UUID registrationId;

    @Column(nullable = false)
    private UUID formDefinitionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String answers;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();

    public UUID getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(UUID registrationId) {
        this.registrationId = registrationId;
    }

    public UUID getFormDefinitionId() {
        return formDefinitionId;
    }

    public void setFormDefinitionId(UUID formDefinitionId) {
        this.formDefinitionId = formDefinitionId;
    }

    public String getAnswers() {
        return answers;
    }

    public void setAnswers(String answers) {
        this.answers = answers;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
