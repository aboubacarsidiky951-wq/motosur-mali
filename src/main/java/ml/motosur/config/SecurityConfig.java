package ml.motosur.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; "
            + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccountRepository accounts,
                                    SecurityContextRepository contextRepository) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(c -> c
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/auth/login"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterBefore(new AccountGuardFilter(accounts), AuthorizationFilter.class)
            .securityContext(sc -> sc.securityContextRepository(contextRepository))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/auth/csrf", "/api/health", "/api/public/**").permitAll()
                .requestMatchers("/api/maintenance/**").hasRole("MAINTENANCE")
                .requestMatchers("/api/police/**").hasAnyRole("COMMISSARIAT", "AGENT", "CENTRAL")
                .requestMatchers("/api/central/**").hasRole("CENTRAL")
                .requestMatchers("/api/admin/**").hasAnyRole("CENTRAL", "COMMISSARIAT")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "{\"error\":\"UNAUTHENTICATED\"}"))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "{\"error\":\"FORBIDDEN\"}")))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .cors(Customizer.withDefaults());
        return http.build();
    }

    static void json(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(body);
    }

    /** Force l'écriture du cookie XSRF-TOKEN pour que le site puisse envoyer l'en-tête X-XSRF-TOKEN. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(req, res);
        }
    }

    /**
     * À chaque appel /api/** d'un utilisateur connecté : le compte doit exister, être actif et garder
     * le même rôle (une suspension agit tout de suite). Un compte à mot de passe provisoire ne peut
     * que changer son mot de passe.
     */
    static final class AccountGuardFilter extends OncePerRequestFilter {
        private final AccountRepository accounts;

        AccountGuardFilter(AccountRepository accounts) {
            this.accounts = accounts;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest req) {
            return !req.getRequestURI().startsWith("/api/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                Account a = accounts.findByLoginIgnoreCase(auth.getName()).orElse(null);
                boolean roleOk = a != null && auth.getAuthorities().stream()
                        .anyMatch(g -> g.getAuthority().equals("ROLE_" + a.role.name()));
                if (a == null || !a.active || !roleOk) {
                    SecurityContextHolder.clearContext();
                    if (req.getSession(false) != null) {
                        req.getSession(false).invalidate();
                    }
                    json(res, 401, "{\"error\":\"UNAUTHENTICATED\"}");
                    return;
                }
                String path = req.getRequestURI();
                boolean allowed = path.equals("/api/auth/password") || path.equals("/api/auth/me")
                        || path.equals("/api/auth/logout");
                if (a.mustChangePassword && !allowed) {
                    json(res, 403, "{\"error\":\"PASSWORD_CHANGE_REQUIRED\"}");
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }
}
