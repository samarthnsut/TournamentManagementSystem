package com.acme.tms.access.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "permission")
public class Permission extends SoftDeletableEntity {

    @Column(nullable = false, length = 80)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
