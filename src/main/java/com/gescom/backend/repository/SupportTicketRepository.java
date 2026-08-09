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
 * Billetterie du support. Table non cloisonnee, reservee a l'espace proprietaire.
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>,
        JpaSpecificationExecutor<SupportTicket> {

    /**
     * L'entreprise est jointe dans la meme requete : la liste l'affiche a chaque ligne, ce
     * qui declencherait sinon un N+1 par page.
     */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<SupportTicket> findAll(Specification<SupportTicket> spec, Pageable pageable);

    /** Le fil est charge avec le ticket : l'ecran de detail les affiche ensemble. */
    @EntityGraph(attributePaths = {"company", "messages", "messages.author"})
    Optional<SupportTicket> findWithMessagesById(Long id);

    boolean existsByReference(String reference);

    long countByStatusIn(Collection<SupportTicket.TicketStatus> statuses);

    @Query("SELECT t.status, COUNT(t) FROM SupportTicket t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    /** Tickets ouverts d'une entreprise — affiche sur sa fiche. */
    List<SupportTicket> findByCompanyIdAndStatusInOrderByCreatedAtDesc(
            Long companyId, Collection<SupportTicket.TicketStatus> statuses);
}
