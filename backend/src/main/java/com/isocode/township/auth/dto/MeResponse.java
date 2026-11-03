package com.isocode.township.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.isocode.township.auth.Role;
import com.isocode.township.flat.MemberRelation;
import java.util.List;

public record MeResponse(
        Long id,
        String name,
        Role role,
        @JsonProperty("isDutyManager") boolean isDutyManager,
        List<FlatMembership> flats) {

    public record FlatMembership(Long id, String code, MemberRelation relation) {
    }
}
