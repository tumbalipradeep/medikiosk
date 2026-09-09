package in.devmedi.kiosk.module.ai.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient.Builder} for the AI provider adapters.
 *
 * Spring Boot 4 no longer auto-configures a {@code RestClient.Builder} bean, so we
 * expose one built on the JDK HTTP client. Each provider clones this builder and
 * overrides the request factory with its own short connect/read timeouts.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(jdkRequestFactory(Duration.ofSeconds(10)));
    }

    public static JdkClientHttpRequestFactory jdkRequestFactory(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }
}