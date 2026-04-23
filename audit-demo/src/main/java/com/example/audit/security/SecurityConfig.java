package com.example.audit.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security setup.
 *
 * Static users (username / password / roles):
 *   admin    / admin123    ADMIN, USER
 *   operator / operator123 OPERATOR, USER
 *   viewer   / viewer123   USER
 *
 * Authorisation rules:
 *   /h2-console/**    any authenticated user (console has its own login)
 *   /audit/**         ADMIN only (reading the audit trail is sensitive)
 *   POST /users/process, /users/fail, /users   ADMIN or OPERATOR
 *   GET  /users/**    any authenticated user
 *   everything else   authenticated
 *
 * Auth mechanisms:
 *   - formLogin on /login (browser users hitting the HTML page)
 *   - httpBasic (for curl / API)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "USER")
                .build();

        UserDetails operator = User.builder()
                .username("operator")
                .password(encoder.encode("operator123"))
                .roles("OPERATOR", "USER")
                .build();

        UserDetails viewer = User.builder()
                .username("viewer")
                .password(encoder.encode("viewer123"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, operator, viewer);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // H2 console ships its own frames + uses its own session — relax CSRF and frame options for that path.
            .csrf(csrf -> csrf
                    .ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**")))
            .headers(h -> h
                    .frameOptions(f -> f.sameOrigin()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico").permitAll()
                    .requestMatchers("/h2-console/**").authenticated()
                    .requestMatchers("/audit/**").hasRole("ADMIN")
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/users/**")
                        .hasAnyRole("ADMIN", "OPERATOR")
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/users/**").authenticated()
                    .requestMatchers("/", "/dashboard").authenticated()
                    .anyRequest().authenticated())
            .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/dashboard", true)
                    .permitAll())
            .logout(logout -> logout
                    .logoutSuccessUrl("/login?logout")
                    .permitAll())
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
