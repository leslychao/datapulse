package io.datapulse.security.config;

import static org.springframework.http.HttpMethod.OPTIONS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(OPTIONS, "/**").permitAll()
            .requestMatchers(SecurityPaths.HEALTH).permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
            .authenticationEntryPoint((request, response, authException) -> response.sendError(401))
            .accessDeniedHandler(
                (request, response, accessDeniedException) -> response.sendError(403))
        )
        .build();
  }

  private static final class SecurityPaths {

    private static final String HEALTH = "/actuator/health/**";

    private SecurityPaths() {
    }
  }
}
