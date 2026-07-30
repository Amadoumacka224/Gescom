package com.gescom.backend.repository;

import com.gescom.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    // Variante pour la mise à jour : vrai si un AUTRE utilisateur (id différent) utilise déjà cet email.
    // (le username n'est pas modifiable après création, d'où l'absence de variante équivalente)
    Boolean existsByEmailAndIdNot(String email, Long id);
    List<User> findByRole(User.Role role);
    List<User> findByActive(Boolean active);
    List<User> findByRoleAndActive(User.Role role, Boolean active);
}
