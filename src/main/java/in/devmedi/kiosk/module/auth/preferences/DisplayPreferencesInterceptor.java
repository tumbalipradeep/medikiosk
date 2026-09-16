package in.devmedi.kiosk.module.auth.preferences;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Exposes the signed-in user's server-side display preferences to the view
 * layer as request attributes for every page that uses the shared head
 * fragment. Anonymous users get nothing — their preferences remain
 * browser-local only (no cookies, no fingerprinting, no session writes).
 */
public class DisplayPreferencesInterceptor implements HandlerInterceptor {

    /** Request attribute receiving a {@link DisplayPreferencesService.DisplayPreferences} or null. */
    public static final String REQUEST_ATTRIBUTE = "mkDisplayPreferences";

    private final DisplayPreferencesService service;

    public DisplayPreferencesInterceptor(DisplayPreferencesService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ApplicationUserDetails details) {
            // Defensive role check: the principal is only trusted for a
            // preferences read, never for anything clinical.
            String role = details.getRoleLabel();
            if (Role.PATIENT.name().equals(role) || Role.PHYSICIAN.name().equals(role)
                    || Role.ADMIN.name().equals(role)) {
                // Seed only an existing stored row: users who never saved
                // server preferences keep their browser-local state.
                service.findStored(details.userId())
                        .ifPresent(prefs -> request.setAttribute(REQUEST_ATTRIBUTE, prefs));
            }
        }
        return true;
    }
}
