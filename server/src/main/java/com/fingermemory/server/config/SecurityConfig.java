package com.fingermemory.server.config;

import com.fingermemory.server.auth.AuthRateLimitFilter;
import com.fingermemory.server.auth.AuthRateLimiter;
import com.fingermemory.server.auth.ActiveUserFilter;
import com.fingermemory.server.auth.JsonAuthenticationHandlers;
import com.fingermemory.server.common.ApiError;
import com.fingermemory.server.common.ApiErrorWriter;
import com.fingermemory.server.user.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthRateLimiter rateLimiter,
            UserMapper userMapper,
            ApiErrorWriter errorWriter,
            JsonAuthenticationHandlers authenticationHandlers) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/gm/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(authenticationHandlers.success())
                        .failureHandler(authenticationHandlers.failure())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("FM_SESSION")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        new ApiError("AUTH_REQUIRED", "请先登录")))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                                        new ApiError("ACCESS_DENIED", "没有权限执行此操作"))))
                .addFilterAfter(new ActiveUserFilter(userMapper, errorWriter),
                        SecurityContextHolderFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(rateLimiter, errorWriter),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
