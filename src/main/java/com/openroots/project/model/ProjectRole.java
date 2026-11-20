package com.openroots.project.model;

import java.util.Locale;

/**
 * Role of a user inside one project. Stored as an upper-case string in
 * {@code project_member.role}; legacy lower-case values are read case-insensitively.
 */
public enum ProjectRole {
    VIEWER,
    MEMBER,
    ADMIN,
    OWNER;

    public static ProjectRole parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    /** VIEWER is read-only; every other role may use project devices. */
    public boolean canUseDevices() {
        return this != VIEWER;
    }

    /** OWNER and ADMIN manage devices, credentials and membership. */
    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }
}
