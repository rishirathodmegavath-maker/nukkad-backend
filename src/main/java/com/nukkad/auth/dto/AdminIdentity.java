package com.nukkad.auth.dto;

/** Deliberately minimal — the admin portal only needs to know who is signed in, not a member profile. */
public record AdminIdentity(String id, String email, String name) {
}
