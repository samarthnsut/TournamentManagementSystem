package com.acme.tms.identity.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class User extends SoftDeletableEntity {

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.INVITED;

    @Column(length = 64)
    private String inviteTokenHash;

    private Instant inviteExpiresAt;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getInviteTokenHash() {
        return inviteTokenHash;
    }

    public void setInviteTokenHash(String inviteTokenHash) {
        this.inviteTokenHash = inviteTokenHash;
    }

    public Instant getInviteExpiresAt() {
        return inviteExpiresAt;
    }

    public void setInviteExpiresAt(Instant inviteExpiresAt) {
        this.inviteExpiresAt = inviteExpiresAt;
    }
}

