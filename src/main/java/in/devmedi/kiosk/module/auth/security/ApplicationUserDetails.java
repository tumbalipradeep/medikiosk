package in.devmedi.kiosk.module.auth.security;

import in.devmedi.kiosk.module.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class ApplicationUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final boolean locked;
    private final Collection<? extends GrantedAuthority> authorities;

    public ApplicationUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.displayName = user.getDisplayName();
        this.enabled = user.isEnabled();
        this.mustChangePassword = user.isMustChangePassword();
        this.locked = user.isLocked();
        this.authorities = List.of(
                new SimpleGrantedAuthority(user.getRole().authority())
        );
    }

    public Long getId() {
        return id;
    }

    public long userId() {
        return id;
    }

    public String username() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * @return the role name (e.g. {@code PHYSICIAN}) for UI navigation links
     */
    public String getRoleLabel() {
        for (GrantedAuthority authority : authorities) {
            String a = authority.getAuthority();
            if (a.startsWith("ROLE_")) {
                return a.substring(5);
            }
        }
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
        return !locked;
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
