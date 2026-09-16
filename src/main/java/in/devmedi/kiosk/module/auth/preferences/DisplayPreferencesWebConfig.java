package in.devmedi.kiosk.module.auth.preferences;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the display-preferences view seeder. The interceptor is attached
 * lazily (autowired setter) so circular dependency with security-filter-chain
 * initialization cannot occur.
 */
@Configuration
public class DisplayPreferencesWebConfig implements WebMvcConfigurer {

    @Autowired
    private DisplayPreferencesService displayPreferencesService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DisplayPreferencesInterceptor(displayPreferencesService))
                .addPathPatterns("/**");
    }
}
