package com.acme.tms.workflow.dto;

import jakarta.validation.constraints.Size;

/** The comment is required on reject; the service enforces that rather than the annotation. */
public record ApprovalActionRequest(@Size(max = 1000) String comment) {
}
