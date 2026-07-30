package com.gescom.backend.security;

import com.gescom.backend.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Composant de sécurité référencé dans les expressions @PreAuthorize
 * (ex : @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")).
 * Permet à un utilisateur d'agir sur ses propres données sans avoir le rôle ADMIN.
 */
@Component("userSecurity")
public class UserSecurity {

    /** Renvoie true si l'utilisateur authentifié est bien celui dont l'id est passé en argument. */
    public boolean isCurrentUser(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            User currentUser = (User) principal;
            return currentUser.getId().equals(userId);
        }

        return false;
    }
}
