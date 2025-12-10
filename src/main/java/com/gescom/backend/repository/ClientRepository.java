package com.gescom.backend.repository;

import com.gescom.backend.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmail(String email);
    List<Client> findByActiveTrue();
    List<Client> findByType(Client.ClientType type);
    Boolean existsByEmail(String email);
}
