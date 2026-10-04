package com.gescom.backend.security;

import com.gescom.backend.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Bean de sécurité utilisé dans les @PreAuthorize
 * (ex : @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#id)")).
 * Permet à un utilisateur de modifier ses propres données sans être ADMIN.
 */
@Component("userSecurity")
public class UserSecurity {

    /** Renvoie true si l'utilisateur connecté est celui dont l'id est passé en paramètre. */
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
