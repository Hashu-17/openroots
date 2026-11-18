package com.openroots.user.model;

/**
 * Platform-wide role of an account. Separate from project roles: a platform ADMIN may
 * create projects and change other users' platform roles; a USER only sees projects
 * they have been added to.
 */
public enum PlatformRole {
    USER,
    ADMIN
}
