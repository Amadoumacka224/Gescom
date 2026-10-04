package com.gescom.backend.repository;

import com.gescom.backend.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code JpaSpecificationExecutor} sert pour la recherche paginée des clients : l'écran
 * combine huit filtres facultatifs (recherche, type, état, ville, pays, société, avec e-mail,
 * période de création), impossible à faire avec une méthode dérivée. Le filtrage par
 * entreprise marche toujours, le filtre Hibernate s'applique aussi aux requêtes par critères.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {
    Optional<Client> findByEmail(String email);
    List<Client> findByActiveTrue();
    List<Client> findByType(Client.ClientType type);
    Boolean existsByEmail(String email);
    // Version pour la modification : vrai si un AUTRE client (id différent) a déjà cet email.
    Boolean existsByEmailAndIdNot(String email, Long id);

    /** Clients actifs pour le tableau de bord, comptés en base plutôt que sur une liste. */
    long countByActiveTrue();

    /**
     * Compteurs en haut de page, calculés en base.
     *
     * Avant, l'écran les calculait à partir de tous les clients chargés dans le navigateur.
     * Comme on ne les charge plus, il faut les calculer en base. Une seule requête pour les
     * quatre : ils portent sur les mêmes clients et doivent être cohérents entre eux.
     */
    @Query("""
           SELECT COUNT(c) AS total,
                  COALESCE(SUM(CASE WHEN c.active = true THEN 1 ELSE 0 END), 0) AS active,
                  COALESCE(SUM(CASE WHEN c.type = :individual THEN 1 ELSE 0 END), 0) AS individuals,
                  COALESCE(SUM(CASE WHEN c.type = :companyType THEN 1 ELSE 0 END), 0) AS companies
           FROM Client c
           """)
    ClientSummaryView summary(@Param("individual") Client.ClientType individual,
                              @Param("companyType") Client.ClientType companyType);

    /**
     * Valeurs qui existent vraiment, pour remplir les listes des filtres. Même idée que
     * {@code ActivityLogRepository.findDistinctEntities} : on ne propose pas un filtre qui ne
     * donne rien.
     *
     * Avant, ces listes étaient calculées côté navigateur avec tous les clients. Il faut la
     * liste complète (une ville en page 3 doit être proposée dès la page 1), d'où une requête
     * à part plutôt que de partir de la page affichée.
     */
    @Query("SELECT DISTINCT c.city FROM Client c WHERE c.city IS NOT NULL AND c.city <> '' ORDER BY c.city")
    List<String> findDistinctCities();

    @Query("SELECT DISTINCT c.country FROM Client c WHERE c.country IS NOT NULL AND c.country <> '' ORDER BY c.country")
    List<String> findDistinctCountries();

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour la raison. */
    interface ClientSummaryView {
        long getTotal();
        long getActive();
        long getIndividuals();
        long getCompanies();
    }
}
