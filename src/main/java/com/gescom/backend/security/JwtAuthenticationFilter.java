package com.gescom.backend.security;

import com.gescom.backend.entity.User;
import com.gescom.backend.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre exécuté une fois par requête (OncePerRequestFilter) pour l'authentification JWT
 * sans session : il lit le token dans l'en-tête Authorization, le vérifie et met
 * l'utilisateur dans le SecurityContext pour la durée de la requête.
 * Il est placé avant le filtre d'authentification standard de Spring (voir SecurityConfig).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Si le token est valide, on recrée l'authentification et on la met dans le
            // contexte de sécurité ; sinon la requête continue en anonyme.
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUsernameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Spring Security ne vérifie isEnabled() qu'à la connexion, dans le
                // DaoAuthenticationProvider. Comme ici on recrée l'authentification nous-mêmes
                // à partir du token, il faut refaire la vérification. Sinon un token créé
                // avant la suspension d'une entreprise marcherait jusqu'à son expiration, et
                // la suspension ne s'appliquerait que des heures plus tard.
                //
                // Si le compte est refusé on ne coupe pas la chaîne : la requête continue en
                // anonyme et tombera sur le point d'entrée d'authentification, qui renvoie 401.
                if (userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    applyTenantScope(userDetails);
                } else {
                    log.debug("Jeton refusé : le compte {} est désactivé ou son entreprise est suspendue", username);
                }
            }
        } catch (Exception e) {
            log.warn("Impossible d'authentifier l'utilisateur : {}", e.getMessage(), e);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // À ne surtout pas oublier : les threads du serveur sont réutilisés, et un contexte
            // pas nettoyé passerait à la requête suivante, peut-être celle d'un utilisateur
            // d'une autre entreprise.
            TenantContext.clear();
        }
    }

    /**
     * Définit l'entreprise de la requête à partir de l'utilisateur chargé en base.
     *
     * On la lit exprès dans l'entité et pas dans un claim du token : un id d'entreprise dans
     * le JWT serait une donnée venant du client, et un token fabriqué ou trop vieux pourrait
     * donner accès aux données d'une autre entreprise. Ici le client ne fournit que son
     * identité, déjà vérifiée par la signature.
     *
     * Le SUPER_ADMIN n'a pas d'entreprise : son contexte reste vide, ce qui désactive le
     * filtre par entreprise et lui donne accès à toutes les entreprises.
     */
    private void applyTenantScope(UserDetails userDetails) {
        if (userDetails instanceof User user && user.getOwnerCompany() != null) {
            TenantContext.setCompanyId(user.getOwnerCompany().getId());
        } else {
            TenantContext.clear();
        }
    }

    /** Récupère le token dans l'en-tête « Authorization: Bearer <token> », ou null s'il n'y en a pas. */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // retire le préfixe "Bearer "
        }

        return null;
    }
}
