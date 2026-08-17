package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Company;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cloisonnement des donnees entre entreprises clientes.
 *
 * <h2>Pourquoi ce test existe</h2>
 *
 * C'est le seul mecanisme du projet dont la defaillance ne se voit pas : une regression n'y
 * produit ni erreur ni page blanche, seulement les donnees d'un client chez un autre. Le reste
 * de la suite est en Mockito pur et ne peut rien en dire — un mock de repository renvoie ce
 * qu'on lui demande de renvoyer, filtre ou non. Il faut une base, une session Hibernate et le
 * contexte Spring complet pour que la question ait un sens.
 *
 * <h2>Ce qui est verifie</h2>
 *
 * Les trois pieces du dispositif, separement, parce qu'aucune ne se suffit a elle-meme :
 *
 * <ol>
 *   <li>le filtre Hibernate, sur les requetes de liste ;</li>
 *   <li>{@code TenantAwareRepositoryImpl}, sur le chargement par identifiant — que le filtre
 *       ne couvre pas, Hibernate servant {@code find} depuis le cache de premier niveau ;</li>
 *   <li>le refus d'ecriture croisee, qui doit lever plutot que masquer.</li>
 * </ol>
 *
 * S'y ajoutent les deux comportements dont depend tout le reste : l'estampillage automatique a
 * la persistance, et le fait qu'un contexte vide vaut « aucune restriction » — la vue du
 * SUPER_ADMIN, sans laquelle le back-office de la plateforme ne verrait rien.
 *
 * <h2>Base</h2>
 *
 * H2 en memoire, schema derive des entites. Voir le commentaire de la dependance H2 dans le
 * pom pour le detail du choix.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenancy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        // Aucun compte proprietaire ne doit etre cree : PlatformAdminBootstrap le ferait si
        // application-local.properties, present sur un poste de developpement, renseignait ces
        // trois valeurs. Le test ne doit pas dependre de ce qui traine hors du depot.
        "platform.admin.username=",
        "platform.admin.email=",
        "platform.admin.password=",
        "jwt.secret=test-secret-suffisamment-long-pour-hmac-sha256-aaaaaaaaaaaaaaaa",
})
@Transactional
class TenantIsolationTest {

    /*
     * @Transactional n'est pas ici une commodite de nettoyage, c'est une condition de validite.
     *
     * TenantFilterActivator active le filtre sur la session courante avant chaque appel au
     * package repository. Hors transaction, Spring cree un EntityManager ephemere PAR
     * operation : le filtre serait alors pose sur une session, et la requete executee dans une
     * autre — il ne s'appliquerait jamais, et le test echouerait pour une raison etrangere au
     * cloisonnement. En production, le @Transactional des services garantit cette session
     * unique ; le test reproduit la meme condition.
     *
     * Effet secondaire utile : la session restant ouverte, les associations paresseuses sont
     * lisibles dans les assertions, et chaque test est annule en fin d'execution.
     *
     * Effet secondaire notable, et voulu : les entites creees dans setUp restent dans le cache
     * de premier niveau. Le test 2 interroge donc findById sur une entite deja chargee —
     * exactement le trou que le filtre Hibernate ne couvre pas et que
     * TenantAwareRepositoryImpl est charge de fermer.
     */

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ClientRepository clientRepository;

    private Company alpha;
    private Company beta;
    private Long clientOfBetaId;

    /**
     * Deux entreprises, un client chacune.
     *
     * Le jeu est monte SANS contexte, c'est-a-dire dans la vue globale : c'est la seule facon
     * de creer des donnees pour deux entreprises a la fois, et cela reproduit exactement ce que
     * fait le back-office de la plateforme.
     */
    @BeforeEach
    void setUp() {
        TenantContext.clear();
        clientRepository.deleteAll();
        companyRepository.deleteAll();

        alpha = companyRepository.save(company("Alpha", "alpha"));
        beta = companyRepository.save(company("Beta", "beta"));

        clientRepository.save(clientOf(alpha, "Anne", "Alpha"));
        clientOfBetaId = clientRepository.save(clientOf(beta, "Bernard", "Beta")).getId();
    }

    /** Un contexte laisse en place contaminerait le test suivant — les threads sont reutilises. */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. En liste, une entreprise ne voit aucune ligne d'une autre")
    void listeCloisonneeParEntreprise() {
        TenantContext.setCompanyId(alpha.getId());

        // Assertions portees sur les seuls noms, jamais sur les entites : le message d'echec
        // d'AssertJ appellerait toString() sur un Client, donc sur son entreprise chargee en
        // paresseux — et l'on obtiendrait une LazyInitializationException a la place du
        // diagnostic recherche.
        List<String> visibles = clientRepository.findAll().stream().map(Client::getLastName).toList();

        assertThat(visibles)
                .as("Alpha ne doit voir que son propre client")
                .containsExactly("Alpha");
    }

    @Test
    @DisplayName("2. findById sur l'identifiant d'une autre entreprise rend vide, pas la ligne")
    void findByIdHorsPerimetreRendVide() {
        TenantContext.setCompanyId(alpha.getId());

        Optional<Client> trouve = clientRepository.findById(clientOfBetaId);

        // Vide et non refuse : un identifiant etranger doit etre indiscernable d'un identifiant
        // inexistant, sans quoi la difference entre 403 et 404 renseignerait sur l'existence de
        // la donnee et permettrait d'en denombrer le parc en balayant les identifiants.
        assertThat(trouve)
                .as("le client de Beta ne doit pas etre atteignable par son identifiant")
                .isEmpty();
    }

    @Test
    @DisplayName("2 bis. Le meme identifiant reste atteignable depuis son entreprise")
    void findByIdDansSonPerimetreFonctionne() {
        TenantContext.setCompanyId(beta.getId());

        // Contre-epreuve indispensable : sans elle, un findById qui renverrait TOUJOURS vide
        // ferait passer le test precedent tout en cassant l'application.
        assertThat(clientRepository.findById(clientOfBetaId)).isPresent();
    }

    @Test
    @DisplayName("3. Une ecriture visant une autre entreprise est refusee, pas silencieuse")
    void ecritureCroiseeRefusee() {
        TenantContext.setCompanyId(beta.getId());
        Client duBeta = clientRepository.findById(clientOfBetaId).orElseThrow();

        TenantContext.setCompanyId(alpha.getId());
        duBeta.setCity("Bruxelles");

        // En lecture on fait disparaitre la donnee ; en ecriture on leve. Une ecriture hors
        // perimetre traduit une anomalie franche — code fautif ou tentative deliberee — qui
        // doit etre rejetee et tracee, jamais masquee.
        assertThatThrownBy(() -> clientRepository.save(duBeta))
                .isInstanceOf(TenantViolationException.class);
    }

    @Test
    @DisplayName("4. Une creation est estampillee de l'entreprise du contexte, sans rien preciser")
    void creationEstampilleeAutomatiquement() {
        TenantContext.setCompanyId(alpha.getId());

        Client nouveau = new Client();
        nouveau.setFirstName("Camille");
        nouveau.setLastName("Nouvelle");
        nouveau.setPhone("0470000000");
        // Aucun appel a setOwnerCompany : c'est precisement ce que TenantEntityListener evite
        // d'imposer a chaque service et a chaque mapper.
        Client enregistre = clientRepository.save(nouveau);

        assertThat(enregistre.getOwnerCompany()).isNotNull();
        assertThat(enregistre.getOwnerCompany().getId()).isEqualTo(alpha.getId());
    }

    @Test
    @DisplayName("5. Sans contexte, la vue est globale — c'est celle du SUPER_ADMIN")
    void contexteVideDonneLaVueGlobale() {
        TenantContext.clear();

        List<String> tous = clientRepository.findAll().stream().map(Client::getLastName).toList();

        // Un contexte nul signifie « aucune restriction » et non « aucune donnee ». C'est ce
        // qui permet au back-office de la plateforme de voir le parc entier — et c'est aussi
        // pourquoi les services de ce back-office doivent poser ownerCompany eux-memes.
        assertThat(tous).containsExactlyInAnyOrder("Alpha", "Beta");
    }

    private Company company(String name, String slug) {
        Company c = new Company();
        c.setName(name);
        c.setSlug(slug);
        c.setEmail(slug + "@example.test");
        c.setCountry("Belgique");
        return c;
    }

    private Client clientOf(Company owner, String firstName, String lastName) {
        Client c = new Client();
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setPhone("0470000000");
        c.setOwnerCompany(owner);
        return c;
    }
}
