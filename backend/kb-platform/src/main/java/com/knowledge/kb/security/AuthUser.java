package com.knowledge.kb.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class AuthUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean enabled;
    private final Set<String> permissions;
    private final Set<String> roleCodes;

    public AuthUser(Long id, String username, String password, String displayName,
                    boolean enabled, Set<String> permissions, Set<String> roleCodes) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.enabled = enabled;
        this.permissions = permissions;
        this.roleCodes = roleCodes;
    }

    public boolean hasPermission(String code) {
        return permissions != null && permissions.contains(code);
    }

    public boolean isTenantAdmin() {
        return roleCodes != null && roleCodes.contains("TENANT_ADMIN");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
