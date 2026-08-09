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
 * Filtre exécuté une seule fois par requête (OncePerRequestFilter) qui assure
 * l'authentification stateless par JWT : il lit le token de l'en-tête Authorization,
 * le valide et place l'utilisateur dans le SecurityContext pour la durée de la requête.
 * Placé avant le filtre d'authentification standard de Spring (voir SecurityConfig).
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
            // Si un token valide est présent, on reconstruit l'authentification et on la
            // dépose dans le contexte de sécurité ; sinon la requête poursuit en anonyme.
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUsernameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // isEnabled() n'est vérifié par Spring Security qu'à la connexion, via le
                // DaoAuthenticationProvider. L'authentification par jeton étant reconstruite
                // à la main, la vérification doit être refaite ici — sans quoi un jeton émis
                // avant la suspension d'une entreprise resterait valable jusqu'à son
                // expiration, et la suspension ne prendrait effet qu'avec des heures de retard.
                //
                // Le compte refusé n'interrompt pas la chaîne : la requête poursuit en
                // anonyme et se heurtera au point d'entrée d'authentification, qui répond 401.
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
            // Nettoyage impératif : les threads du conteneur sont recyclés, et un contexte
            // oublié serait hérité par la requête suivante — donc potentiellement par un
            // utilisateur d'une autre entreprise.
            TenantContext.clear();
        }
    }

    /**
     * Fixe l'entreprise de la requête à partir de l'utilisateur chargé en base.
     *
     * Le rattachement est délibérément lu sur l'entité et non sur un claim du jeton :
     * un identifiant d'entreprise transporté dans le JWT serait une donnée fournie par le
     * client, et un jeton forgé ou simplement périmé ouvrirait les données d'autrui. Ici,
     * la seule chose que le client fournit est son identité, déjà vérifiée par signature.
     *
     * Le SUPER_ADMIN n'a pas d'entreprise : son contexte reste vide, ce qui désactive le
     * filtre de cloisonnement et lui donne la vue globale du parc.
     */
    private void applyTenantScope(UserDetails userDetails) {
        if (userDetails instanceof User user && user.getOwnerCompany() != null) {
            TenantContext.setCompanyId(user.getOwnerCompany().getId());
        } else {
            TenantContext.clear();
        }
    }

    /** Extrait le token de l'en-tête « Authorization: Bearer <token> », ou null s'il est absent. */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // retire le préfixe "Bearer "
        }

        return null;
    }
}
