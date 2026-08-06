package com.acme.tms.tournament.dto;

import java.util.UUID;

public record SportResponse(UUID id, String code, String name, String description) {
}
