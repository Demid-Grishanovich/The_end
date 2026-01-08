package com.datacrowd.core.config;

import com.datacrowd.core.security.InternalTokenFilter;
import com.datacrowd.core.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalTokenFilter internalTokenFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          InternalTokenFilter internalTokenFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalTokenFilter = internalTokenFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // ✅ Swagger / OpenAPI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // ✅ Actuator
                        .requestMatchers("/actuator/**").permitAll()

                        // ✅ Internal endpoints are permitted by Spring Security,
                        // but реально защищены InternalTokenFilter
                        .requestMatchers("/internal/**").permitAll()

                        // Block 7: workers do tasks + review
                        .requestMatchers("/core/tasks/**").hasAnyRole("WORKER", "ADMIN")
                        .requestMatchers("/core/reviews/**").hasAnyRole("WORKER", "ADMIN")

                        // (Reserved for future blocks)
                        .requestMatchers("/core/client/**").hasAnyRole("CLIENT", "ADMIN")

                        // Projects / datasets
                        .requestMatchers(HttpMethod.POST, "/core/projects/**").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/core/projects/**").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/core/projects/**").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/core/projects/**").authenticated()
                        .requestMatchers("/core/**").authenticated()

                        .anyRequest().denyAll()
                )
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
