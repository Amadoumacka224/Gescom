package com.gescom.backend.repository;

import com.gescom.backend.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Tickets de support. Pas filtres par entreprise, reserves a l'espace super admin.
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>,
        JpaSpecificationExecutor<SupportTicket> {

    /**
     * L'entreprise est chargee dans la meme requete : la liste l'affiche sur chaque ligne,
     * sinon on aurait un N+1 par page.
     */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<SupportTicket> findAll(Specification<SupportTicket> spec, Pageable pageable);

    /** Les messages sont charges avec le ticket : l'ecran de detail affiche les deux. */
    @EntityGraph(attributePaths = {"company", "messages", "messages.author"})
    Optional<SupportTicket> findWithMessagesById(Long id);

    boolean existsByReference(String reference);

    long countByStatusIn(Collection<SupportTicket.TicketStatus> statuses);

    @Query("SELECT t.status, COUNT(t) FROM SupportTicket t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    /** Tickets ouverts d'une entreprise, affiches sur sa fiche. */
    List<SupportTicket> findByCompanyIdAndStatusInOrderByCreatedAtDesc(
            Long companyId, Collection<SupportTicket.TicketStatus> statuses);
}
