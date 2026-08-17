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
 * {@code JpaSpecificationExecutor} porte la recherche paginée du fichier clients : l'écran
 * combine huit critères facultatifs (recherche libre, type, état, ville, pays, société,
 * présence d'un e-mail, période de création), ce qu'aucune méthode dérivée par nom n'exprime.
 * Le cloisonnement par entreprise reste assuré — le filtre Hibernate couvre les requêtes par
 * critères comme les autres.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {
    Optional<Client> findByEmail(String email);
    List<Client> findByActiveTrue();
    List<Client> findByType(Client.ClientType type);
    Boolean existsByEmail(String email);
    // Variante pour la mise à jour : vrai si un AUTRE client (id différent) utilise déjà cet email.
    Boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Compteurs d'en-tête, agrégés en base.
     *
     * L'écran les déduisait du fichier complet chargé dans le navigateur ; celui-ci n'étant
     * plus rapatrié, il faut les calculer là où sont les données. Une seule requête pour les
     * quatre : ils décrivent le même ensemble et doivent rester cohérents entre eux.
     */
    @Query("""
           SELECT COUNT(c) AS total,
                  SUM(CASE WHEN c.active = true THEN 1 ELSE 0 END) AS active,
                  SUM(CASE WHEN c.type = :individual THEN 1 ELSE 0 END) AS individuals,
                  SUM(CASE WHEN c.type = :companyType THEN 1 ELSE 0 END) AS companies
           FROM Client c
           """)
    ClientSummaryView summary(@Param("individual") Client.ClientType individual,
                              @Param("companyType") Client.ClientType companyType);

    /**
     * Valeurs réellement présentes dans le fichier, pour alimenter les listes déroulantes des
     * filtres. Même principe que {@code ActivityLogRepository.findDistinctEntities} : un
     * critère qui ne rend rien n'est pas proposé.
     *
     * Ces listes étaient dérivées du fichier complet côté navigateur. Elles doivent rester
     * exhaustives — une ville présente en page 3 doit être proposée depuis la page 1 —, d'où
     * une requête dédiée plutôt qu'une déduction sur la page affichée.
     */
    @Query("SELECT DISTINCT c.city FROM Client c WHERE c.city IS NOT NULL AND c.city <> '' ORDER BY c.city")
    List<String> findDistinctCities();

    @Query("SELECT DISTINCT c.country FROM Client c WHERE c.country IS NOT NULL AND c.country <> '' ORDER BY c.country")
    List<String> findDistinctCountries();

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour le motif. */
    interface ClientSummaryView {
        long getTotal();
        long getActive();
        long getIndividuals();
        long getCompanies();
    }
}
