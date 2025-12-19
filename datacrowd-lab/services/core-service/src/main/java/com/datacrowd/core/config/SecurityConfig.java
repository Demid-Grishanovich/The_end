package com.datacrowd.core.config;

import com.datacrowd.core.security.InternalTokenFilter;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, InternalTokenFilter internalTokenFilter) throws Exception {

        http
                // API-only режим
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Отключаем дефолтные формы/бейсик, чтобы не было popup в Swagger
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable());

        // Если хочешь полностью убрать Basic Auth:
        // .httpBasic(basic -> basic.disable())

        http.authorizeHttpRequests(auth -> auth
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Swagger / OpenAPI (разрешаем всегда)
                .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html"
                ).permitAll()

                // Actuator health/info (чтобы docker/gateway могли чекать)
                .requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info"
                ).permitAll()

                // Наши internal endpoints защищает InternalTokenFilter
                .requestMatchers("/internal/**").permitAll()

                // Всё остальное — пока разрешаем (позже прикрутим JWT через gateway)
                .anyRequest().permitAll()
        );

        // Internal token проверяем ДО стандартных фильтров
        http.addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
