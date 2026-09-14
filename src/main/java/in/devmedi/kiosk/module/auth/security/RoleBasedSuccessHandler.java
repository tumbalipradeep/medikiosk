package in.devmedi.kiosk.module.auth.security;

import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Role-aware success handler with account-lifecycle duties:
 *
 * <ol>
 *   <li>Clear the failed-login counter for a successful login.</li>
 *   <li>Route to the correct portal home by role.</li>
 *   <li>Force an admin-provisioned physician (or password-reset user) through
 *       the temporary-password change screen before entering the portal.</li>
 * </ol>
 */
@Component
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountLifecycleService accountLifecycleService;

    public RoleBasedSuccessHandler(AccountLifecycleService accountLifecycleService) {
        this.accountLifecycleService = accountLifecycleService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        accountLifecycleService.clearFailedLogins(authentication.getName());

        String targetUrl = determineTargetUrl(authentication);
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApplicationUserDetails details && details.isMustChangePassword()) {
            targetUrl = "/account/password?forced=true";
        }
        response.sendRedirect(targetUrl);
    }

    private String determineTargetUrl(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            return switch (authority.getAuthority()) {
                case "ROLE_ADMIN" -> "/admin/home";
                case "ROLE_PHYSICIAN" -> "/physician/home";
                case "ROLE_PATIENT" -> "/patient/home";
                default -> "/";
            };
        }
        return "/";
    }
}