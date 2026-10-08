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
 * Separation des donnees entre les entreprises clientes.
 *
 * <h2>Pourquoi ce test</h2>
 *
 * C'est le seul mecanisme du projet dont on ne voit pas les bugs : une regression ne donne
 * ni erreur ni page blanche, juste les donnees d'un client chez un autre. Les autres tests
 * sont en Mockito et ne peuvent pas le verifier : un mock de repository renvoie ce qu'on lui
 * dit, filtre ou pas. Il faut une vraie base, une session Hibernate et tout le contexte
 * Spring pour que le test ait un sens.
 *
 * <h2>Ce qu'on verifie</h2>
 *
 * Les trois parties du systeme, chacune a part, parce qu'aucune ne suffit seule :
 *
 * <ol>
 *   <li>le filtre Hibernate, sur les requetes de liste ;</li>
 *   <li>{@code TenantAwareRepositoryImpl}, sur le chargement par id, que le filtre ne
 *       couvre pas car Hibernate peut servir {@code find} depuis son cache ;</li>
 *   <li>le refus d'ecrire dans une autre entreprise, qui doit lancer une exception et pas
 *       cacher le probleme.</li>
 * </ol>
 *
 * On verifie aussi deux comportements dont tout le reste depend : l'entreprise mise
 * automatiquement a l'enregistrement, et le fait qu'un contexte vide veut dire « pas de
 * restriction » (la vue du SUPER_ADMIN, sans laquelle le back-office ne verrait rien).
 *
 * <h2>Base</h2>
 *
 * H2 en memoire, schema genere depuis les entites. Voir le commentaire de la dependance H2
 * dans le pom pour la raison de ce choix.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenancy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        // On ne veut pas creer de compte super admin : PlatformAdminBootstrap le ferait si
        // application-local.properties (present sur un poste de dev) avait ces trois valeurs.
        // Le test ne doit pas dependre de fichiers en dehors du depot.
        "platform.admin.username=",
        "platform.admin.email=",
        "platform.admin.password=",
        "jwt.secret=test-secret-suffisamment-long-pour-hmac-sha256-aaaaaaaaaaaaaaaa",
})
@Transactional
class TenantIsolationTest {

    /*
     * Ici @Transactional n'est pas juste pour nettoyer apres, le test en a besoin pour etre valable.
     *
     * TenantFilterActivator active le filtre sur la session en cours avant chaque appel aux
     * repositories. Sans transaction, Spring cree un EntityManager temporaire PAR operation :
     * le filtre serait mis sur une session et la requete lancee sur une autre, donc il ne
     * s'appliquerait jamais et le test echouerait pour une raison qui n'a rien a voir. En
     * production, le @Transactional des services garantit une seule session ; le test fait
     * pareil.
     *
     * En bonus : la session reste ouverte, donc on peut lire les relations lazy dans les
     * assertions, et chaque test est annule a la fin.
     *
     * Autre effet, voulu celui-la : les entites creees dans setUp restent dans le cache de
     * Hibernate. Le test 2 fait donc un findById sur une entite deja chargee, c'est
     * exactement le trou que le filtre Hibernate ne couvre pas et que
     * TenantAwareRepositoryImpl doit boucher.
     */

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ClientRepository clientRepository;

    private Company alpha;
    private Company beta;
    private Long clientOfBetaId;

    /**
     * Deux entreprises, un client chacune.
     *
     * Les donnees sont creees SANS contexte, donc sans filtre : c'est le seul moyen de creer
     * des donnees pour deux entreprises en meme temps, et c'est exactement ce que fait le
     * back-office de la plateforme.
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

    /** Un contexte pas nettoye fausserait le test suivant (les threads sont reutilises). */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. En liste, une entreprise ne voit aucune ligne d'une autre")
    void listeCloisonneeParEntreprise() {
        TenantContext.setCompanyId(alpha.getId());

        // On verifie seulement les noms, jamais les entites : en cas d'echec AssertJ
        // appellerait toString() sur un Client, donc sur son entreprise chargee en lazy, et on
        // aurait une LazyInitializationException au lieu du vrai message d'erreur.
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

        // Vide et pas refuse : un id d'une autre entreprise doit etre impossible a distinguer
        // d'un id qui n'existe pas, sinon la difference entre 403 et 404 dirait si la donnee
        // existe, et on pourrait compter les donnees des autres en essayant tous les ids.
        assertThat(trouve)
                .as("le client de Beta ne doit pas etre atteignable par son identifiant")
                .isEmpty();
    }

    @Test
    @DisplayName("2 bis. Le meme identifiant reste atteignable depuis son entreprise")
    void findByIdDansSonPerimetreFonctionne() {
        TenantContext.setCompanyId(beta.getId());

        // Test inverse indispensable : sans lui, un findById qui renverrait TOUJOURS vide
        // ferait passer le test d'avant tout en cassant l'application.
        assertThat(clientRepository.findById(clientOfBetaId)).isPresent();
    }

    @Test
    @DisplayName("3. Une ecriture visant une autre entreprise est refusee, pas silencieuse")
    void ecritureCroiseeRefusee() {
        TenantContext.setCompanyId(beta.getId());
        Client duBeta = clientRepository.findById(clientOfBetaId).orElseThrow();

        TenantContext.setCompanyId(alpha.getId());
        duBeta.setCity("Bruxelles");

        // En lecture on fait comme si la donnee n'existait pas ; en ecriture on lance une
        // exception. Ecrire dans une autre entreprise veut dire qu'il y a un vrai probleme
        // (bug ou tentative volontaire), qu'on doit refuser et logger, jamais cacher.
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
        // Pas de setOwnerCompany : c'est justement ce que TenantEntityListener evite de devoir
        // faire dans chaque service et chaque mapper.
        Client enregistre = clientRepository.save(nouveau);

        assertThat(enregistre.getOwnerCompany()).isNotNull();
        assertThat(enregistre.getOwnerCompany().getId()).isEqualTo(alpha.getId());
    }

    @Test
    @DisplayName("5. Sans contexte, la vue est globale - c'est celle du SUPER_ADMIN")
    void contexteVideDonneLaVueGlobale() {
        TenantContext.clear();

        List<String> tous = clientRepository.findAll().stream().map(Client::getLastName).toList();

        // Un contexte null veut dire « pas de restriction », pas « aucune donnee ». C'est ce
        // qui permet au back-office de la plateforme de voir toutes les entreprises, et c'est
        // aussi pour ca que ses services doivent remplir ownerCompany eux-memes.
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
