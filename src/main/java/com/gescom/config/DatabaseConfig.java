package com.gescom.config;

import com.gescom.entity.*;
import com.gescom.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDatabase() {
        return args -> {
            logger.info("🚀 Initialisation de la base de données PostgreSQL...");

            try {
                // Vérifier si les données existent déjà
                if (userRepository.count() > 0) {
                    logger.info("✅ Base de données déjà initialisée, skip");
                    return;
                }

                // Création séquentielle des données
                createRolesAndPermissions();
                List<User> users = createUsers();
                List<Client> clients = createClients(users);
                List<Product> products = createProducts();
                createOrdersAndInvoices(users, clients, products);

                logger.info("🎉 Base de données initialisée avec succès !");
                logger.info("📊 Données créées : {} utilisateurs, {} clients, {} produits",
                        users.size(), clients.size(), products.size());

                printDefaultAccounts();

            } catch (Exception e) {
                logger.error("❌ Erreur lors de l'initialisation de la base de données", e);
                throw e;
            }
        };
    }

    @Transactional
    public void createRolesAndPermissions() {
        logger.info("🔐 Création des rôles et permissions...");

        // Créer les permissions si elles n'existent pas
        Permission readPerm = findOrCreatePermission("READ");
        Permission writePerm = findOrCreatePermission("WRITE");
        Permission deletePerm = findOrCreatePermission("DELETE");
        Permission managePerm = findOrCreatePermission("MANAGE");

        // Créer les rôles si ils n'existent pas
        Role adminRole = findOrCreateRole("ADMIN", Set.of(readPerm, writePerm, deletePerm, managePerm));
        Role managerRole = findOrCreateRole("MANAGER", Set.of(readPerm, writePerm, managePerm));
        Role userRole = findOrCreateRole("USER", Set.of(readPerm, writePerm));

        logger.info("✅ Rôles et permissions créés");
    }

    private Permission findOrCreatePermission(String name) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setName(name);
                    permission.setDescription("Permission " + name);
                    return permissionRepository.save(permission);
                });
    }

    private Role findOrCreateRole(String name, Set<Permission> permissions) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    role.setDescription("Rôle " + name);
                    role.setPermissions(permissions);
                    return roleRepository.save(role);
                });
    }

    @Transactional
    public List<User> createUsers() {

        logger.info("👥 Création des utilisateurs...");

        List<User> users = new ArrayList<>();

        // Récupérer les rôles
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        // 1. Administrateur
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setEmail("admin@commercial.com");
        admin.setFirstName("Jean");
        admin.setLastName("Administrateur");
        admin.addRole(adminRole);
        admin.setPersonalTarget(BigDecimal.ZERO);
        users.add(userRepository.save(admin));

        // 2. Managers
        User manager1 = createUser("manager1", "Sophie", "Dubois", "manager1@commercial.com", managerRole, BigDecimal.valueOf(300000));
        manager1.setPassword(passwordEncoder.encode("password123"));
        users.add(userRepository.save(manager1));

        User manager2 = createUser("manager2", "Pierre", "Martin", "manager2@commercial.com",
                userRole, BigDecimal.valueOf(280000));
        manager2.setPassword(passwordEncoder.encode("password123"));
        users.add(userRepository.save(manager2));

        // 3. Commerciaux équipe 1
        User[] team1 = {
                createUser("marie.commercial", "Marie", "Lemoine", "marie@commercial.com", userRole, BigDecimal.valueOf(120000)),
                createUser("paul.commercial", "Paul", "Durand", "paul@commercial.com", userRole, BigDecimal.valueOf(110000)),
                createUser("julie.commercial", "Julie", "Bernard", "julie@commercial.com", userRole, BigDecimal.valueOf(100000)),
                createUser("antoine.commercial", "Antoine", "Rousseau", "antoine@commercial.com", userRole, BigDecimal.valueOf(90000))
        };

        for (User user : team1) {
            users.add(userRepository.save(user));
        }

        // 4. Commerciaux équipe 2
        User[] team2 = {
                createUser("lucas.commercial", "Lucas", "Moreau", "lucas@commercial.com", userRole, BigDecimal.valueOf(115000)),
                createUser("emma.commercial", "Emma", "Lefebvre", "emma@commercial.com", userRole, BigDecimal.valueOf(105000)),
                createUser("thomas.commercial", "Thomas", "Roux", "thomas@commercial.com", userRole, BigDecimal.valueOf(95000)),
                createUser("clara.commercial", "Clara", "Fournier", "clara@commercial.com", userRole, BigDecimal.valueOf(85000))
        };

        for (User user : team2) {
            users.add(userRepository.save(user));
        }

        logger.info("✅ {} utilisateurs créés", users.size());
        return users;
    }

    @Transactional
    public List<Client> createClients(List<User> users) {
        logger.info("👥 Création des clients...");

        List<Client> clients = new ArrayList<>();

        // Filtrer les utilisateurs ayant le rôle USER
        List<User> commercials = users.stream()
                .filter(u -> u.hasRole("USER"))
                .collect(Collectors.toList());

        // Clients entreprises
        String[][] companies = {
                {"TechCorp Solutions", "contact@techcorp.fr", "01.23.45.67.89", "123 Avenue des Champs", "Paris", "75001", "SIRET123456789"},
                {"Digital Innovation", "info@digital-innov.fr", "02.34.56.78.90", "45 Rue de la Tech", "Lyon", "69001", "SIRET234567890"},
                {"Green Energy SA", "hello@green-energy.fr", "03.45.67.89.01", "78 Boulevard Écolo", "Marseille", "13001", "SIRET345678901"},
                {"Startup Factory", "team@startup-factory.fr", "04.56.78.90.12", "12 Rue Innovation", "Toulouse", "31000", "SIRET456789012"},
                {"Global Trade Ltd", "sales@global-trade.fr", "05.67.89.01.23", "89 Avenue Commerce", "Bordeaux", "33000", "SIRET567890123"},
                {"Smart Solutions", "contact@smart-sol.fr", "01.78.90.12.34", "34 Place Technologie", "Lille", "59000", "SIRET678901234"},
                {"Future Corp", "info@future-corp.fr", "02.89.01.23.45", "56 Rue Futur", "Nantes", "44000", "SIRET789012345"},
                {"Innovation Hub", "hello@innov-hub.fr", "03.90.12.34.56", "67 Boulevard Créatif", "Strasbourg", "67000", "SIRET890123456"}
        };

        for (int i = 0; i < companies.length; i++) {
            String[] company = companies[i];
            Client client = new Client();
            client.setName(company[0]);
            client.setEmail(company[1]);
            client.setPhoneNumber(company[2]);
            client.setAddress(company[3]);
            client.setCity(company[4]);
            client.setPostalCode(company[5]);
            client.setCountry("France");
            client.setCompanyName(company[0]);
            client.setSiretNumber(company[6]);
            client.setClientType(Client.ClientType.COMPANY);
            client.setStatus(Client.ClientStatus.ACTIVE);
            if (!commercials.isEmpty()) {
                client.setAssignedUser(commercials.get(i % commercials.size()));
            }
            client.setNotes("Client entreprise créé automatiquement");
            clients.add(clientRepository.save(client));
        }

        // Clients particuliers
        String[][] individuals = {
                {"Jean Dupont", "jean.dupont@email.fr", "06.12.34.56.78", "12 Rue de la Paix", "Paris", "75002"},
                {"Marie Durand", "marie.durand@email.fr", "06.23.45.67.89", "23 Avenue Victor Hugo", "Lyon", "69002"},
                {"Pierre Martin", "pierre.martin@email.fr", "06.34.56.78.90", "34 Boulevard Saint-Michel", "Marseille", "13002"},
                {"Sophie Bernard", "sophie.bernard@email.fr", "06.45.67.89.01", "45 Rue Nationale", "Toulouse", "31001"},
                {"Antoine Moreau", "antoine.moreau@email.fr", "06.56.78.90.12", "56 Place Gambetta", "Bordeaux", "33001"},
                {"Julie Rousseau", "julie.rousseau@email.fr", "06.67.89.01.23", "67 Rue de la République", "Lille", "59001"}
        };

        for (int i = 0; i < individuals.length; i++) {
            String[] individual = individuals[i];
            Client client = new Client();
            client.setName(individual[0]);
            client.setEmail(individual[1]);
            client.setPhoneNumber(individual[2]);
            client.setAddress(individual[3]);
            client.setCity(individual[4]);
            client.setPostalCode(individual[5]);
            client.setCountry("France");
            client.setClientType(Client.ClientType.INDIVIDUAL);
            client.setStatus(Client.ClientStatus.ACTIVE);
            if (!commercials.isEmpty()) {
                client.setAssignedUser(commercials.get(i % commercials.size()));
            }
            client.setNotes("Client particulier créé automatiquement");
            clients.add(clientRepository.save(client));
        }

        // Quelques prospects
        for (int i = 0; i < 3; i++) {
            Client prospect = new Client();
            prospect.setName("Prospect " + (i + 1));
            prospect.setEmail("prospect" + (i + 1) + "@email.fr");
            prospect.setPhoneNumber("06.00.00.00.0" + i);
            prospect.setClientType(Client.ClientType.COMPANY);
            prospect.setStatus(Client.ClientStatus.PROSPECT);
            if (!commercials.isEmpty()) {
                prospect.setAssignedUser(commercials.get(i % commercials.size()));
            }
            prospect.setFollowUpDate(LocalDateTime.now().plusDays(i + 1));
            clients.add(clientRepository.save(prospect));
        }

        logger.info("✅ {} clients créés", clients.size());
        return clients;
    }

    @Transactional
    public List<Product> createProducts() {
        logger.info("📦 Création des produits...");

        List<Product> products = new ArrayList<>();

        // Catégorie Logiciels
        String[][] software = {
                {"Logiciel CRM Pro", "Solution CRM complète pour entreprises", "CRM-001", "299.99", "199.99", "50"},
                {"Suite Office Business", "Suite bureautique professionnelle", "OFF-001", "149.99", "89.99", "100"},
                {"Antivirus Enterprise", "Protection antivirus pour entreprises", "AV-001", "89.99", "49.99", "75"},
                {"Logiciel Comptabilité", "Gestion comptable simplifiée", "COMPTA-001", "199.99", "129.99", "30"}
        };

        for (String[] soft : software) {
            Product product = createProduct(soft[0], soft[1], soft[2], soft[3], soft[4], soft[5], "Logiciels", "Microsoft", "licence");
            products.add(productRepository.save(product));
        }

        // Catégorie Matériel
        String[][] hardware = {
                {"Ordinateur Portable Pro", "Laptop professionnel haute performance", "LAPTOP-001", "899.99", "699.99", "25"},
                {"Écran 27 pouces 4K", "Moniteur professionnel 4K", "SCREEN-001", "349.99", "249.99", "40"},
                {"Clavier Mécanique RGB", "Clavier gaming professionnel", "KEYB-001", "129.99", "79.99", "60"},
                {"Souris Ergonomique", "Souris sans fil ergonomique", "MOUSE-001", "49.99", "29.99", "80"},
                {"Webcam HD Pro", "Caméra HD pour visioconférence", "CAM-001", "79.99", "49.99", "45"}
        };

        for (String[] hard : hardware) {
            Product product = createProduct(hard[0], hard[1], hard[2], hard[3], hard[4], hard[5], "Matériel", "TechBrand", "pièce");
            products.add(productRepository.save(product));
        }

        // Catégorie Services
        String[][] services = {
                {"Formation Office 365", "Formation complète Office 365", "FORM-001", "499.99", "299.99", "999"},
                {"Support Technique", "Support technique premium", "SUPP-001", "99.99", "59.99", "999"},
                {"Consultation IT", "Consultation informatique", "CONS-001", "150.00", "100.00", "999"},
                {"Installation Réseau", "Installation réseau entreprise", "INSTALL-001", "299.99", "199.99", "999"}
        };

        for (String[] service : services) {
            Product product = createProduct(service[0], service[1], service[2], service[3], service[4], service[5], "Services", "TechServices", "heure");
            products.add(productRepository.save(product));
        }

        logger.info("✅ {} produits créés", products.size());
        return products;
    }

    @Transactional
    public void createOrdersAndInvoices(List<User> users, List<Client> clients, List<Product> products) {
        logger.info("🛒 Création des commandes et factures...");

        List<User> commercials = users.stream()
                .filter(u -> u.hasRole("USER"))
                .collect(Collectors.toList());;

        if (commercials.isEmpty()) {
            logger.warn("Aucun commercial trouvé, impossible de créer des commandes");
            return;
        }

        Random random = new Random();
        int orderNumber = 1000;

        // Créer des commandes sur les 90 derniers jours
        for (int day = 90; day >= 0; day--) {
            LocalDateTime orderDate = LocalDateTime.now().minusDays(day);

            // 1-3 commandes par jour
            int ordersToday = random.nextInt(3) + 1;

            for (int i = 0; i < ordersToday; i++) {
                User commercial = commercials.get(random.nextInt(commercials.size()));
                List<Client> actives = clients.stream()
                        .filter(c -> c.getStatus() == Client.ClientStatus.ACTIVE)
                        .collect(Collectors.toList());

                if (actives.isEmpty()) {
                    throw new NoSuchElementException("Aucun client actif");
                }

                Client client = actives.get(random.nextInt(actives.size()));

                Order order = new Order();
                order.setOrderNumber("CMD-" + (orderNumber++));
                order.setOrderDate(orderDate);
                order.setUser(commercial);
                order.setClient(client);
                order.setStatus(getRandomOrderStatus(day));
                order.setBillingAddress(client.getFullAddress());
                order.setShippingAddress(client.getFullAddress());

                // Ajouter 1-5 produits à la commande
                int itemCount = random.nextInt(5) + 1;
                List<OrderItem> orderItems = new ArrayList<>();

                for (int j = 0; j < itemCount; j++) {
                    Product product = products.get(random.nextInt(products.size()));

                    OrderItem item = new OrderItem();
                    item.setOrder(order);
                    item.setProduct(product);
                    item.setQuantity(random.nextInt(10) + 1);
                    item.setUnitPrice(product.getUnitPrice());
                   // item.setVatRate(product.getVatRate() != null ? product.getVatRate() : BigDecimal.valueOf(20.0));

                    // Remise aléatoire parfois
                    if (random.nextBoolean() && random.nextDouble() < 0.3) {
                        item.setDiscountRate(BigDecimal.valueOf(random.nextDouble() * 10)); // 0-10%
                    } else {
                        item.setDiscountRate(BigDecimal.ZERO); // Initialiser à ZERO si pas de remise
                    }

                    orderItems.add(item);
                }

                order.setOrderItems(orderItems);
                order.calculateTotals();

                Order savedOrder = orderRepository.save(order);

                // Créer une facture si la commande est confirmée
                if (savedOrder.getStatus() != Order.OrderStatus.DRAFT &&
                        savedOrder.getStatus() != Order.OrderStatus.CANCELLED) {
                    createInvoiceForOrder(savedOrder, random);
                }
            }
        }

        logger.info("✅ Commandes et factures créées");
    }

    private void createInvoiceForOrder(Order order, Random random) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FACT-" + order.getOrderNumber().substring(4));
        invoice.setInvoiceDate(order.getOrderDate().toLocalDate());
        invoice.setDueDate(order.getOrderDate().toLocalDate().plusDays(30));
        invoice.setOrder(order);

        // S'assurer que les montants ne sont pas null
        invoice.setTotalAmountHT(order.getTotalAmountHT() != null ? order.getTotalAmountHT() : BigDecimal.ZERO);
        invoice.setTotalVatAmount(order.getTotalVatAmount() != null ? order.getTotalVatAmount() : BigDecimal.ZERO);
        invoice.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        invoice.setBillingAddress(order.getBillingAddress());

        // Statut de facture selon l'âge
        int daysOld = (int) order.getOrderDate().until(LocalDateTime.now(), java.time.temporal.ChronoUnit.DAYS);
        if (daysOld > 60) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaidAmount(invoice.getTotalAmount());
            invoice.setPaymentDate(order.getOrderDate().toLocalDate().plusDays(random.nextInt(30) + 5));
        } else if (daysOld > 30) {
            if (random.nextDouble() < 0.8) {
                invoice.setStatus(Invoice.InvoiceStatus.PAID);
                invoice.setPaidAmount(invoice.getTotalAmount());
                invoice.setPaymentDate(order.getOrderDate().toLocalDate().plusDays(random.nextInt(25) + 5));
            } else {
                invoice.setStatus(Invoice.InvoiceStatus.OVERDUE);
            }
        } else if (daysOld > 7) {
            invoice.setStatus(random.nextDouble() < 0.6 ? Invoice.InvoiceStatus.PAID : Invoice.InvoiceStatus.SENT);
            if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
                invoice.setPaidAmount(invoice.getTotalAmount());
                invoice.setPaymentDate(order.getOrderDate().toLocalDate().plusDays(random.nextInt(20) + 3));
            }
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.SENT);
        }

        // Créer les lignes de facture
        List<InvoiceItem> invoiceItems = new ArrayList<>();
        for (OrderItem orderItem : order.getOrderItems()) {
            InvoiceItem invoiceItem = new InvoiceItem();
            invoiceItem.setInvoice(invoice);
            invoiceItem.setDescription(orderItem.getProduct().getName());
            invoiceItem.setQuantity(orderItem.getQuantity());
            invoiceItem.setUnitPrice(orderItem.getUnitPrice() != null ? orderItem.getUnitPrice() : BigDecimal.ZERO);
            invoiceItem.setVatRate(orderItem.getVatRate() != null ? orderItem.getVatRate() : BigDecimal.valueOf(20.0));
            invoiceItem.setDiscountRate(orderItem.getDiscountRate() != null ? orderItem.getDiscountRate() : BigDecimal.ZERO);
            invoiceItem.setReference(orderItem.getProduct().getReference());
            invoiceItem.setUnit(orderItem.getProduct().getUnit());
            invoiceItems.add(invoiceItem);
        }

        invoice.setInvoiceItems(invoiceItems);
        invoiceRepository.save(invoice);
    }

    private User createUser(String username, String firstName, String lastName, String email,
                            Role role, BigDecimal personalTarget) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.addRole(role);
        user.setEnabled(true);
        user.setPersonalTarget(personalTarget);
        return user;
    }

    private Product createProduct(String name, String description, String reference,
                                  String price, String purchasePrice, String stock,
                                  String category, String brand, String unit) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setReference(reference);
        product.setUnitPrice(new BigDecimal(price));
        product.setPurchasePrice(new BigDecimal(purchasePrice));
        product.setStock(Integer.parseInt(stock));
        product.setMinStock(Integer.parseInt(stock) / 5);
        product.setCategory(category);
        product.setBrand(brand);
        product.setUnit(unit);
       // product.setVatRate(BigDecimal.valueOf(20.0));
        product.setIsActive(true);
        product.setBarCode("BC" + reference);

        // S'assurer que tous les BigDecimal sont non-null
        if (product.getPurchasePrice() == null) product.setPurchasePrice(BigDecimal.ZERO);
        //if (product.getVatRate() == null) product.setVatRate(BigDecimal.valueOf(20.0));

        return product;
    }

    private Order.OrderStatus getRandomOrderStatus(int daysOld) {
        Random random = new Random();
        if (daysOld > 60) return Order.OrderStatus.DELIVERED;
        if (daysOld > 30) return random.nextDouble() < 0.9 ? Order.OrderStatus.DELIVERED : Order.OrderStatus.SHIPPED;
        if (daysOld > 7) return random.nextDouble() < 0.7 ? Order.OrderStatus.DELIVERED : Order.OrderStatus.PROCESSING;
        if (daysOld > 3) return random.nextDouble() < 0.8 ? Order.OrderStatus.CONFIRMED : Order.OrderStatus.PROCESSING;
        return random.nextDouble() < 0.6 ? Order.OrderStatus.CONFIRMED : Order.OrderStatus.DRAFT;
    }

    private void printDefaultAccounts() {
        logger.info("");
        logger.info("🔐 COMPTES PAR DÉFAUT CRÉÉS :");
        logger.info("┌─────────────────────────────────────────┐");
        logger.info("│ ADMIN    : admin     / admin123         │");
        logger.info("│ MANAGER1 : manager1  / password123      │");
        logger.info("│ MANAGER2 : manager2  / password123      │");
        logger.info("│ USER     : marie.commercial / password123│");
        logger.info("└─────────────────────────────────────────┘");
        logger.info("");
    }
}