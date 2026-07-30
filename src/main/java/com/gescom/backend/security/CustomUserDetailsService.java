package com.gescom.backend.security;

import com.gescom.backend.entity.User;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pont entre Spring Security et la base de données : charge un utilisateur par son
 * nom d'utilisateur. L'entité User implémente UserDetails, elle est donc retournée telle quelle.
 * Utilisé par le provider d'authentification (login) et par le filtre JWT (chaque requête).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        return user;
    }
}
