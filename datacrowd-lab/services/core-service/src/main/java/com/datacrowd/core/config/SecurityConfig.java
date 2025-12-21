package com.datacrowd.core.config;

import com.datacrowd.core.security.InternalTokenFilter;
import com.datacrowd.core.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalTokenFilter internalTokenFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Swagger/OpenAPI
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                // Health/info
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()

                // public ping
                .requestMatchers("/core/ping").permitAll()

                // internal — доступ разрешаем, но фильтр отрежет без токена
                .requestMatchers("/internal/**").permitAll()

                // RBAC demo (оставляем)
                .requestMatchers("/core/security-demo/admin").hasRole("ADMIN")
                .requestMatchers("/core/security-demo/client").hasRole("CLIENT")
                .requestMatchers("/core/security-demo/worker").hasRole("WORKER")
                .requestMatchers("/core/security-demo/me").authenticated()

                // Projects/Datasets:
                // создание — только CLIENT/ADMIN
                .requestMatchers(HttpMethod.POST, "/core/projects/**").hasAnyRole("CLIENT", "ADMIN")

                // чтение — любой авторизованный
                .requestMatchers(HttpMethod.GET, "/core/projects/**").authenticated()

                // всё остальное в /core/** — требует JWT
                .requestMatchers("/core/**").authenticated()

                .anyRequest().denyAll()
        );

        http.addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
