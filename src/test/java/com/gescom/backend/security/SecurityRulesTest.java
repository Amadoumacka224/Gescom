package com.gescom.backend.security;

import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.UserRepository;
import com.gescom.backend.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Règles d'accès de SecurityConfig, vérifiées par de vraies requêtes HTTP.
 *
 * Les autres tests appellent les services directement, avec un SecurityContext posé à la
 * main : ils ne passent ni par le filtre JWT ni par les règles sur les URL. Une règle
 * supprimée par erreur dans SecurityConfig ne ferait donc échouer aucun d'eux. Ici chaque
 * requête porte un vrai jeton signé et traverse toute la chaîne de filtres.
 *
 * Même base H2 que TenantIsolationTest (voir le commentaire de la dépendance H2 dans le pom).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "platform.admin.username=",
        "platform.admin.email=",
        "platform.admin.password=",
        "jwt.secret=test-secret-suffisamment-long-pour-hmac-sha256-aaaaaaaaaaaaaaaa",
})
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;

    @MockitoSpyBean private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setName("Alpha");
        company.setSlug("alpha");
        company.setEmail("alpha@example.test");
        company.setCountry("Belgique");
        company = companyRepository.save(company);

        userRepository.save(user("admin", User.Role.ADMIN, company));
        userRepository.save(user("caisse", User.Role.CAISSIER, company));
        userRepository.save(user("owner", User.Role.SUPER_ADMIN, null));
        clearInvocations(userDetailsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Sans jeton, une route métier répond 401")
    void sansJeton() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un jeton signé avec un autre secret est refusé")
    void jetonFalsifie() throws Exception {
        String forged = io.jsonwebtoken.Jwts.builder()
                .subject("admin")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "un-autre-secret-de-plus-de-trente-deux-octets-bbbbbbbb".getBytes()))
                .compact();

        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un caissier accède aux écrans métier, pas aux routes réservées à l'ADMIN")
    void caissier() throws Exception {
        mockMvc.perform(get("/api/clients").header("Authorization", bearer("caisse")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header("Authorization", bearer("caisse")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un ADMIN d'entreprise n'a pas accès au back-office de la plateforme")
    void adminHorsPlateforme() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", bearer("admin")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/companies").header("Authorization", bearer("admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Le SUPER_ADMIN n'a accès qu'au back-office, jamais aux écrans métier")
    void superAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/companies").header("Authorization", bearer("owner")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clients").header("Authorization", bearer("owner")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Le filtre JWT ne charge l'utilisateur qu'une fois par requête")
    void filtreJwtExecuteUneFois() throws Exception {
        mockMvc.perform(get("/api/clients").header("Authorization", bearer("caisse")))
                .andExpect(status().isOk());

        verify(userDetailsService, times(1)).loadUserByUsername(anyString());
    }

    private String bearer(String username) {
        return "Bearer " + jwtUtils.generateTokenFromUsername(username);
    }

    private User user(String username, User.Role role, Company company) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.test");
        u.setPassword("{noop}inutilise");
        u.setFirstName(username);
        u.setLastName("Test");
        u.setRole(role);
        u.setOwnerCompany(company);
        return u;
    }
}
