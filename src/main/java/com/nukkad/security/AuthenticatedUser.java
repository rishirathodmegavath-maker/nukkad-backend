package com.nukkad.security;

import java.util.Set;

public record AuthenticatedUser(String id, String email, Set<String> roles) {
}
