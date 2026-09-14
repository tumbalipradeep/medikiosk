package in.devmedi.kiosk.module.auth.security;

import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Authentication failure handler that implements the account lockout policy
 * (each bad attempt increments the counter; the account locks after the
 * configured cap) and surfaces a clear, honest reason to the login screen.
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AccountLifecycleService accountLifecycleService;

    public LoginFailureHandler(AccountLifecycleService accountLifecycleService) {
        this.accountLifecycleService = accountLifecycleService;
        setUseForward(false);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            if (exception instanceof BadCredentialsException
                    || exception instanceof UsernameNotFoundException) {
                accountLifecycleService.recordFailedLogin(username);
            }
        }

        if (exception instanceof LockedException) {
            setDefaultFailureUrl("/login?locked");
        } else if (exception instanceof DisabledException) {
            setDefaultFailureUrl("/login?disabled");
        } else {
            setDefaultFailureUrl("/login?error=true");
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}