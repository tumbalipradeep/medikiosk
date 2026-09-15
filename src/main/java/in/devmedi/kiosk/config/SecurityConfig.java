package in.devmedi.kiosk.config;

import in.devmedi.kiosk.module.auth.security.LoginFailureHandler;
import in.devmedi.kiosk.module.auth.security.RoleBasedSuccessHandler;
import in.devmedi.kiosk.module.auth.security.SecurityPolicyProperties;
import in.devmedi.kiosk.module.ocr.OcrProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityPolicyProperties.class, OcrProperties.class})
public class SecurityConfig {

    private final RoleBasedSuccessHandler roleBasedSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;

    public SecurityConfig(RoleBasedSuccessHandler roleBasedSuccessHandler,
                          LoginFailureHandler loginFailureHandler) {
        this.roleBasedSuccessHandler = roleBasedSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionAuthenticationStrategy sessionAuthenticationStrategy) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/error", "/css/**", "/js/**", "/img/**").permitAll()
                        .requestMatchers("/api/capabilities/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/account/**").authenticated()
                        .requestMatchers("/patient/**").hasRole("PATIENT")
                        .requestMatchers("/physician/**").hasRole("PHYSICIAN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        // Never send case/page references to third-party CDNs or other origins.
                        .referrerPolicy(c -> c.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(roleBasedSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(sm -> sm.sessionAuthenticationStrategy(sessionAuthenticationStrategy));

        return http.build();
    }
}