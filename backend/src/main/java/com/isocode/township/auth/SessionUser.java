package com.isocode.township.auth;

public record SessionUser(Long userId, Role role) {

    public boolean isStaff() {
        return role == Role.HELPDESK;
    }
}
