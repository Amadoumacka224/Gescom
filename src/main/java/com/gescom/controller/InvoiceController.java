package com.gescom.controller;

// Importation des classes nécessaires
import com.gescom.entity.Invoice;
import com.gescom.entity.Order;
import com.gescom.entity.User;
import com.gescom.repository.InvoiceRepository;
import com.gescom.repository.OrderRepository;
import com.gescom.repository.UserRepository;
import com.gescom.service.InvoicePdfService;
import com.gescom.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.mail.MessagingException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Annotation qui indique que cette classe est un contrôleur Spring MVC
@Controller
// Définit que toutes les URL de ce contrôleur commencent par "/invoices"
@RequestMapping("/invoices")
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    // Injection automatique du repository des factures
    @Autowired
    private InvoiceRepository invoiceRepository;

    // Injection automatique du repository des utilisateurs
    @Autowired
    private UserRepository userRepository;

    // Injection automatique du repository des commandes
    @Autowired
    private OrderRepository orderRepository;
    
    // Injection du service PDF
    @Autowired
    private InvoicePdfService pdfService;
    
    // Injection du service Email
    @Autowired
    private EmailService emailService;

    // Méthode qui gère les requêtes GET vers "/invoices"
    @GetMapping
    public String listInvoices(
            // Récupère automatiquement l'utilisateur connecté
            @AuthenticationPrincipal UserDetails userDetails,
            // Paramètre pour la pagination - page courante (défaut: 0)
            @RequestParam(defaultValue = "0") int page,
            // Paramètre pour la pagination - nombre d'éléments par page (défaut: 10)
            @RequestParam(defaultValue = "10") int size,
            // Paramètre pour le tri - colonne de tri (défaut: invoiceDate)
            @RequestParam(defaultValue = "invoiceDate") String sortBy,
            // Paramètre pour le tri - direction (défaut: desc pour décroissant)
            @RequestParam(defaultValue = "desc") String sortDir,
            // Paramètre optionnel pour la recherche textuelle
            @RequestParam(required = false) String search,
            // Paramètre optionnel pour filtrer par statut
            @RequestParam(required = false) String status,
            // Paramètre optionnel pour filtrer par date de début
            @RequestParam(required = false) String dateFrom,
            // Paramètre optionnel pour filtrer par date de fin
            @RequestParam(required = false) String dateTo,
            // Paramètre optionnel pour afficher seulement les factures en retard
            @RequestParam(required = false) Boolean overdue,
            // Objet Model pour passer des données à la vue
            Model model) {

        try {
            // === ÉTAPE 1: RÉCUPÉRATION DE L'UTILISATEUR CONNECTÉ ===

            // Recherche de l'utilisateur dans la base de données par son nom d'utilisateur
            Optional<User> currentUserOpt = userRepository.findByUsername(userDetails.getUsername());

            // Vérification si l'utilisateur existe
            if (currentUserOpt.isEmpty()) {
                // Si l'utilisateur n'existe pas, on ajoute un message d'erreur et on redirige
                model.addAttribute("error", "Utilisateur non trouvé");
                return "redirect:/dashboard";
            }

            // Récupération de l'utilisateur depuis l'Optional
            User currentUser = currentUserOpt.get();

            // === ÉTAPE 2: RÉCUPÉRATION DES FACTURES SELON LES DROITS ===

            // Déclaration de la liste qui contiendra toutes les factures
            List<Invoice> allInvoices;

            // Vérification des rôles de l'utilisateur
            if (currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER")) {
                // Les admins et managers voient toutes les factures
                allInvoices = invoiceRepository.findAll();
            } else {
                // Les utilisateurs normaux voient seulement leurs propres factures
                allInvoices = invoiceRepository.findAll().stream()
                        // Filtre pour garder seulement les factures de l'utilisateur connecté
                        .filter(invoice -> invoice.getOrder() != null &&
                                invoice.getOrder().getUser().getId().equals(currentUser.getId()))
                        // Conversion du stream en liste
                        .collect(Collectors.toList());
            }

            // === ÉTAPE 3: APPLICATION DES FILTRES ===

            // FILTRE PAR RECHERCHE TEXTUELLE
            if (search != null && !search.trim().isEmpty()) {
                // Conversion en minuscules pour une recherche insensible à la casse
                String searchLower = search.toLowerCase();
                allInvoices = allInvoices.stream()
                        .filter(invoice ->
                                // Recherche dans le numéro de facture
                                invoice.getInvoiceNumber().toLowerCase().contains(searchLower) ||
                                        // Recherche dans le nom du client (avec vérifications de null)
                                        (invoice.getOrder() != null && invoice.getOrder().getClient() != null &&
                                                invoice.getOrder().getClient().getName().toLowerCase().contains(searchLower)))
                        .collect(Collectors.toList());
            }

            // FILTRE PAR STATUT
            if (status != null && !status.trim().isEmpty()) {
                try {
                    // Conversion du string en enum InvoiceStatus
                    Invoice.InvoiceStatus invoiceStatus = Invoice.InvoiceStatus.valueOf(status);
                    allInvoices = allInvoices.stream()
                            // Filtre par statut exact
                            .filter(invoice -> invoice.getStatus() == invoiceStatus)
                            .collect(Collectors.toList());
                } catch (IllegalArgumentException e) {
                    // Si le statut est invalide, on ignore le filtre
                    // (pas de crash, filtre simplement ignoré)
                }
            }

            // FILTRE PAR FACTURES EN RETARD
            if (overdue != null && overdue) {
                // Récupération de la date d'aujourd'hui
                LocalDate today = LocalDate.now();
                allInvoices = allInvoices.stream()
                        .filter(invoice ->
                                // Vérification que la date d'échéance existe
                                invoice.getDueDate() != null &&
                                        // La date d'échéance est avant aujourd'hui
                                        invoice.getDueDate().isBefore(today) &&
                                        // ET la facture n'est pas payée
                                        invoice.getStatus() != Invoice.InvoiceStatus.PAID)
                        .collect(Collectors.toList());
            }

            // FILTRE PAR DATE DE DÉBUT
            if (dateFrom != null && !dateFrom.trim().isEmpty()) {
                try {
                    // Conversion du string en LocalDate
                    LocalDate fromDate = LocalDate.parse(dateFrom);
                    allInvoices = allInvoices.stream()
                            .filter(invoice ->
                                    // Vérification que la date de facture existe
                                    invoice.getInvoiceDate() != null &&
                                            // La date de facture est après la date de début (- 1 jour pour inclusion)
                                            invoice.getInvoiceDate().isAfter(fromDate.minusDays(1)))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    // Si la date est invalide, on ignore le filtre
                }
            }

            // FILTRE PAR DATE DE FIN
            if (dateTo != null && !dateTo.trim().isEmpty()) {
                try {
                    // Conversion du string en LocalDate
                    LocalDate toDate = LocalDate.parse(dateTo);
                    allInvoices = allInvoices.stream()
                            .filter(invoice ->
                                    // Vérification que la date de facture existe
                                    invoice.getInvoiceDate() != null &&
                                            // La date de facture est avant la date de fin (+ 1 jour pour inclusion)
                                            invoice.getInvoiceDate().isBefore(toDate.plusDays(1)))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    // Si la date est invalide, on ignore le filtre
                }
            }

            // === ÉTAPE 4: APPLICATION DU TRI ===

            switch (sortBy) {
                case "invoiceNumber" ->
                    // Tri par numéro de facture
                        allInvoices.sort((a, b) -> sortDir.equals("desc") ?
                                // Tri décroissant
                                b.getInvoiceNumber().compareTo(a.getInvoiceNumber()) :
                                // Tri croissant
                                a.getInvoiceNumber().compareTo(b.getInvoiceNumber()));

                case "client" ->
                    // Tri par nom de client
                        allInvoices.sort((a, b) -> {
                            // Récupération sécurisée du nom du client A
                            String clientA = (a.getOrder() != null && a.getOrder().getClient() != null) ?
                                    a.getOrder().getClient().getName() : "";
                            // Récupération sécurisée du nom du client B
                            String clientB = (b.getOrder() != null && b.getOrder().getClient() != null) ?
                                    b.getOrder().getClient().getName() : "";
                            // Comparaison selon la direction
                            return sortDir.equals("desc") ? clientB.compareTo(clientA) : clientA.compareTo(clientB);
                        });

                case "amount" ->
                    // Tri par montant
                        allInvoices.sort((a, b) -> {
                            // Récupération sécurisée du montant A (0 si null)
                            BigDecimal amountA = a.getTotalAmount() != null ? a.getTotalAmount() : BigDecimal.ZERO;
                            // Récupération sécurisée du montant B (0 si null)
                            BigDecimal amountB = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
                            // Comparaison selon la direction
                            return sortDir.equals("desc") ? amountB.compareTo(amountA) : amountA.compareTo(amountB);
                        });

                case "dueDate" ->
                    // Tri par date d'échéance
                        allInvoices.sort((a, b) -> {
                            LocalDate dateA = a.getDueDate();
                            LocalDate dateB = b.getDueDate();
                            // Gestion des valeurs null
                            if (dateA == null && dateB == null) return 0;
                            if (dateA == null) return sortDir.equals("desc") ? 1 : -1;
                            if (dateB == null) return sortDir.equals("desc") ? -1 : 1;
                            // Comparaison normale si les deux dates existent
                            return sortDir.equals("desc") ? dateB.compareTo(dateA) : dateA.compareTo(dateB);
                        });

                default ->
                    // Tri par défaut : date de facture
                        allInvoices.sort((a, b) -> {
                            LocalDate dateA = a.getInvoiceDate();
                            LocalDate dateB = b.getInvoiceDate();
                            // Gestion des valeurs null
                            if (dateA == null && dateB == null) return 0;
                            if (dateA == null) return sortDir.equals("desc") ? 1 : -1;
                            if (dateB == null) return sortDir.equals("desc") ? -1 : 1;
                            // Comparaison normale si les deux dates existent
                            return sortDir.equals("desc") ? dateB.compareTo(dateA) : dateA.compareTo(dateB);
                        });
            }

            // === ÉTAPE 5: PAGINATION MANUELLE ===

            // Calcul de l'index de début (évite le dépassement)
            int start = Math.min(page * size, allInvoices.size());
            // Calcul de l'index de fin (évite le dépassement)
            int end = Math.min(start + size, allInvoices.size());
            // Extraction de la sous-liste pour la page courante
            List<Invoice> invoicesPage = allInvoices.subList(start, end);

            // Calculs pour les informations de pagination
            int totalPages = (int) Math.ceil((double) allInvoices.size() / size);
            boolean hasNext = page < totalPages - 1;
            boolean hasPrevious = page > 0;

            // === ÉTAPE 6: CALCUL DES STATISTIQUES ===

            // Nombre total de factures
            long totalInvoices = allInvoices.size();

            // Nombre de factures payées
            long paidInvoices = allInvoices.stream()
                    .filter(i -> i.getStatus() == Invoice.InvoiceStatus.PAID)
                    .count();

            // Nombre de factures en retard
            LocalDate today = LocalDate.now();
            long overdueInvoices = allInvoices.stream()
                    .filter(i -> i.getDueDate() != null &&
                            i.getDueDate().isBefore(today) &&
                            i.getStatus() != Invoice.InvoiceStatus.PAID)
                    .count();

            // === ÉTAPE 7: CALCUL DES MONTANTS ===

            // Somme de tous les montants totaux (en excluant les null)
            BigDecimal totalAmount = allInvoices.stream()
                    .map(Invoice::getTotalAmount)          // Récupère le montant total
                    .filter(Objects::nonNull)              // Exclut les valeurs null
                    .reduce(BigDecimal.ZERO, BigDecimal::add); // Somme avec valeur initiale 0

            // Somme de tous les montants payés (en excluant les null)
            BigDecimal paidAmount = allInvoices.stream()
                    .map(Invoice::getPaidAmount)           // Récupère le montant payé
                    .filter(Objects::nonNull)              // Exclut les valeurs null
                    .reduce(BigDecimal.ZERO, BigDecimal::add); // Somme avec valeur initiale 0

            // Calcul du montant restant à encaisser
            BigDecimal outstandingAmount = totalAmount.subtract(paidAmount);

            // === ÉTAPE 8: CALCUL DU POURCENTAGE DE PAIEMENT ===
            // (C'EST ICI QU'ON RÉSOUT LE PROBLÈME DU TEMPLATE)

            // Initialisation du pourcentage à 0
            // AJOUTEZ CES LIGNES dans votre contrôleur InvoiceController
// (après le calcul de outstandingAmount et avant les model.addAttribute)

// Calcul du pourcentage de paiement pour la barre de progression
            BigDecimal paymentPercentage = BigDecimal.ZERO;
            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                paymentPercentage = paidAmount
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalAmount, 2, RoundingMode.HALF_UP);
            }

// N'oubliez pas d'ajouter cette ligne avec les autres model.addAttribute :
            model.addAttribute("paymentPercentage", paymentPercentage);
            // === ÉTAPE 9: AJOUT DES DONNÉES AU MODÈLE ===
            // (Toutes ces données seront disponibles dans le template Thymeleaf)


            // Données de pagination et tri
            model.addAttribute("invoices", invoicesPage);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", hasNext);
            model.addAttribute("hasPrevious", hasPrevious);
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);

            // Données de filtrage
            model.addAttribute("search", search);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("dateFrom", dateFrom);
            model.addAttribute("dateTo", dateTo);
            model.addAttribute("overdueFilter", overdue);
            model.addAttribute("size", size);


            // Statistiques
            model.addAttribute("totalInvoices", totalInvoices);
            model.addAttribute("paidInvoices", paidInvoices);
            model.addAttribute("overdueInvoices", overdueInvoices);

            // Montants financiers
            model.addAttribute("totalAmount", totalAmount);
            model.addAttribute("paidAmount", paidAmount);
            model.addAttribute("outstandingAmount", outstandingAmount);
            model.addAttribute("paymentPercentage", paymentPercentage); // ← NOUVEAU - résout l'erreur

            // Permissions utilisateur
            model.addAttribute("canEdit", currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER"));
            model.addAttribute("canDelete", currentUser.hasRole("ADMIN"));

        } catch (Exception e) {
            // En cas d'erreur, on ajoute un message d'erreur au modèle
            model.addAttribute("error", "Erreur lors du chargement des factures: " + e.getMessage());
            // On affiche l'erreur dans la console pour le débogage
            e.printStackTrace();
        }

        // Retourne le nom du template Thymeleaf à afficher
        return "invoices/list";
    }

    // === AUTRES MÉTHODES DU CONTRÔLEUR ===

    // Méthode pour afficher le détail d'une facture
    @GetMapping("/{id}")
    public String viewInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        // Recherche de la facture par son ID
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
        System.out.printf("=======================|%d=======================|%n", id);

        // Vérification si la facture existe
        if (invoiceOpt.isEmpty()) {
            // Si elle n'existe pas, message d'erreur et redirection
            redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
            return "redirect:/invoices";
        }

        // Ajout de la facture au modèle pour l'affichage
        model.addAttribute("invoice", invoiceOpt.get());
        return "invoices/detail";
    }

    // Méthode pour marquer une facture comme payée
    @PostMapping("/{id}/mark-paid")
    public String markInvoiceAsPaid(
            @PathVariable Long id,                    // ID de la facture dans l'URL
            @RequestParam BigDecimal paidAmount,      // Montant payé depuis le formulaire
            @RequestParam String paymentDate,        // Date de paiement depuis le formulaire
            RedirectAttributes redirectAttributes) {  // Pour les messages flash

        try {
            // Recherche de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
            if (invoiceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
                return "redirect:/invoices";
            }

            Invoice invoice = invoiceOpt.get();

            // Validation du montant (doit être positif)
            if (paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("error", "Le montant payé doit être positif");
                return "redirect:/invoices/" + id;
            }

            // Validation et conversion de la date
            LocalDate parsedPaymentDate;
            try {
                parsedPaymentDate = LocalDate.parse(paymentDate);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Format de date invalide");
                return "redirect:/invoices/" + id;
            }

            // Mise à jour de la facture
            invoice.setPaidAmount(paidAmount);
            invoice.setPaymentDate(parsedPaymentDate);

            // Détermination du nouveau statut selon le montant payé
            if (paidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
                // Si le montant payé >= montant total, facture complètement payée
                invoice.setStatus(Invoice.InvoiceStatus.PAID);
            } else {
                // Sinon, facture partiellement payée
                invoice.setStatus(Invoice.InvoiceStatus.PARTIAL);
            }

            // Sauvegarde en base de données
            invoiceRepository.save(invoice);

            // Message de succès
            redirectAttributes.addFlashAttribute("success", "Paiement enregistré avec succès");

        } catch (Exception e) {
            // En cas d'erreur, message d'erreur
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'enregistrement du paiement: " + e.getMessage());
        }

        // Redirection vers la liste des factures
        return "redirect:/invoices";
    }

    // Méthode pour envoyer une facture
    @PostMapping("/{id}/send")
    public String sendInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Recherche de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
            if (invoiceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
                return "redirect:/invoices";
            }

            Invoice invoice = invoiceOpt.get();

            // Vérification que la facture est en brouillon
            if (invoice.getStatus() == Invoice.InvoiceStatus.DRAFT) {
                // Changement du statut à "envoyée"
                invoice.setStatus(Invoice.InvoiceStatus.SENT);
                invoiceRepository.save(invoice);
                redirectAttributes.addFlashAttribute("success", "Facture envoyée avec succès");
            } else {
                // Si déjà envoyée, message d'avertissement
                redirectAttributes.addFlashAttribute("warning", "Cette facture a déjà été envoyée");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'envoi: " + e.getMessage());
        }

        return "redirect:/invoices";
    }

    // Méthode pour annuler une facture
    @PostMapping("/{id}/cancel")
    public String cancelInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Recherche de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
            if (invoiceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
                return "redirect:/invoices";
            }

            Invoice invoice = invoiceOpt.get();

            // Vérification que la facture n'est pas payée (une facture payée ne peut pas être annulée)
            if (invoice.getStatus() != Invoice.InvoiceStatus.PAID) {
                // Changement du statut à "annulée"
                invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
                invoiceRepository.save(invoice);
                redirectAttributes.addFlashAttribute("success", "Facture annulée avec succès");
            } else {
                // Si payée, refus de l'annulation
                redirectAttributes.addFlashAttribute("error", "Une facture payée ne peut pas être annulée");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'annulation: " + e.getMessage());
        }

        return "redirect:/invoices";
    }

    // Méthode pour télécharger le PDF
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Recherche de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
            if (invoiceOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Invoice invoice = invoiceOpt.get();
            
            // Génération du PDF
            byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);
            
            // Configuration des headers HTTP
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Facture_" + invoice.getInvoiceNumber() + ".pdf");
            headers.setContentLength(pdfBytes.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
                    
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du PDF pour la facture {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Méthode pour envoyer la facture par email
    @PostMapping("/{id}/send-email")
    public String sendInvoiceByEmail(
            @PathVariable Long id,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "true") boolean attachPdf,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Recherche de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
            if (invoiceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
                return "redirect:/invoices";
            }
            
            Invoice invoice = invoiceOpt.get();
            
            // Déterminer l'email du destinataire
            String recipientEmail = email;
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                if (invoice.getOrder() != null && invoice.getOrder().getClient() != null) {
                    recipientEmail = invoice.getOrder().getClient().getEmail();
                } else {
                    redirectAttributes.addFlashAttribute("error", "Aucun email de destinataire trouvé");
                    return "redirect:/invoices/" + id;
                }
            }
            
            // Envoi de l'email
            emailService.sendInvoiceEmail(invoice, recipientEmail, attachPdf);
            
            // Mise à jour du statut et de la date d'envoi
            invoice.setEmailSent(true);
            invoice.setEmailSentDate(java.time.LocalDateTime.now());
            if (invoice.getStatus() == Invoice.InvoiceStatus.DRAFT) {
                invoice.setStatus(Invoice.InvoiceStatus.SENT);
            }
            invoiceRepository.save(invoice);
            
            redirectAttributes.addFlashAttribute("success", 
                "Facture envoyée avec succès à " + recipientEmail);
                
        } catch (MessagingException e) {
            logger.error("Erreur lors de l'envoi de l'email pour la facture {}", id, e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur lors de l'envoi de l'email: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Erreur générale lors de l'envoi de l'email pour la facture {}", id, e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur lors de l'envoi: " + e.getMessage());
        }
        
        return "redirect:/invoices/" + id;
    }
    
    // Méthode pour envoyer une relance
    @PostMapping("/{id}/remind")
    public String sendReminder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Recherche de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
            if (invoiceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
                return "redirect:/invoices";
            }

            Invoice invoice = invoiceOpt.get();

            // Vérification que la facture nécessite une relance
            if (invoice.getStatus() == Invoice.InvoiceStatus.SENT ||
                    invoice.getStatus() == Invoice.InvoiceStatus.OVERDUE ||
                    invoice.getStatus() == Invoice.InvoiceStatus.PARTIAL) {
                
                // Déterminer l'email du client
                String clientEmail = null;
                if (invoice.getOrder() != null && invoice.getOrder().getClient() != null) {
                    clientEmail = invoice.getOrder().getClient().getEmail();
                }
                
                if (clientEmail == null || clientEmail.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Aucun email client trouvé pour envoyer la relance");
                    return "redirect:/invoices/" + id;
                }
                
                // Envoi de la relance
                emailService.sendInvoiceReminder(invoice, clientEmail);
                
                // Mise à jour du statut si nécessaire
                if (invoice.isOverdue() && invoice.getStatus() != Invoice.InvoiceStatus.OVERDUE) {
                    invoice.setStatus(Invoice.InvoiceStatus.OVERDUE);
                    invoiceRepository.save(invoice);
                }
                
                redirectAttributes.addFlashAttribute("success", "Relance envoyée avec succès à " + clientEmail);
            } else {
                redirectAttributes.addFlashAttribute("warning", "Cette facture ne nécessite pas de relance (statut: " + invoice.getStatus().getDisplayName() + ")");
            }

        } catch (MessagingException e) {
            logger.error("Erreur lors de l'envoi de la relance pour la facture {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'envoi de la relance: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Erreur générale lors de l'envoi de la relance pour la facture {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'envoi de la relance: " + e.getMessage());
        }

        return "redirect:/invoices/" + id;
    }

    @GetMapping("/new")
    public String newInvoice(@RequestParam(required = false) Long orderId, Model model, RedirectAttributes redirectAttributes) {
        try {
            System.out.println("=== CRÉATION NOUVELLE FACTURE ===");
            System.out.println("OrderId reçu: " + orderId);
            
            Invoice invoice = new Invoice();
            
            // Si une commande est spécifiée, pré-remplir la facture
            if (orderId != null) {
                Optional<Order> orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isEmpty()) {
                    System.err.println("ERREUR: Commande " + orderId + " non trouvée");
                    redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                    return "redirect:/orders";
                }
                
                Order order = orderOpt.get();
                System.out.println("Commande trouvée: " + order.getOrderNumber());
                
                // Vérifier si la commande n'a pas déjà une facture
                if (order.getInvoice() != null) {
                    System.out.println("Commande déjà facturée, redirection vers facture existante");
                    redirectAttributes.addFlashAttribute("warning", "Cette commande a déjà une facture");
                    return "redirect:/invoices/" + order.getInvoice().getId();
                }
                
                // Pré-remplir la facture avec les données de la commande
                invoice.setOrder(order);
                invoice.setBillingAddress(order.getBillingAddress());
                invoice.setDiscountRate(order.getDiscountRate());
                invoice.setShippingCost(order.getShippingCost());
                invoice.setTotalAmountHT(order.getTotalAmountHT());
                invoice.setTotalVatAmount(order.getTotalVatAmount());
                invoice.setTotalAmount(order.getTotalAmount());
                invoice.setDiscountAmount(order.getDiscountAmount());
                
                System.out.println("Facture pré-remplie avec montant: " + invoice.getTotalAmount());
            }
            
            model.addAttribute("invoice", invoice);
            model.addAttribute("isEdit", false);
            
            // Liste des commandes disponibles pour facturation (confirmées, en traitement, expédiées ou livrées sans facture)
            List<Order> availableOrders = orderRepository.findAll().stream()
                    .filter(order -> (order.getStatus() == Order.OrderStatus.CONFIRMEE ||
                                    order.getStatus() == Order.OrderStatus.EN_COURS ||
                                    order.getStatus() == Order.OrderStatus.EXPEDIE ||
                                    order.getStatus() == Order.OrderStatus.LIVREE) &&
                                   order.getInvoice() == null)
                    .collect(Collectors.toList());
            model.addAttribute("availableOrders", availableOrders);
            
            System.out.println("Redirection vers formulaire facture");
            return "invoices/form";
            
        } catch (Exception e) {
            System.err.println("ERREUR création facture: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la création de la facture: " + e.getMessage());
            return "redirect:/invoices";
        }
    }

    @PostMapping
    public String createOrUpdateInvoice(
            @ModelAttribute Invoice invoice,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            System.out.println("=== CRÉATION/MODIFICATION FACTURE ===");
            System.out.println("Facture ID: " + invoice.getId());
            System.out.println("Commande ID: " + (invoice.getOrder() != null ? invoice.getOrder().getId() : "null"));

            // Récupérer l'utilisateur connecté
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
                return "redirect:/invoices";
            }
            User currentUser = userOpt.get();

            boolean isEdit = invoice.getId() != null;
            
            if (isEdit) {
                // Modification d'une facture existante
                Optional<Invoice> existingInvoiceOpt = invoiceRepository.findById(invoice.getId());
                if (existingInvoiceOpt.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
                    return "redirect:/invoices";
                }
                
                Invoice existingInvoice = existingInvoiceOpt.get();
                
                // Mettre à jour les champs modifiables
                existingInvoice.setInvoiceNumber(invoice.getInvoiceNumber());
                existingInvoice.setInvoiceDate(invoice.getInvoiceDate());
                existingInvoice.setDueDate(invoice.getDueDate());
                existingInvoice.setBillingAddress(invoice.getBillingAddress());
                existingInvoice.setNotes(invoice.getNotes());
                existingInvoice.setDiscountRate(invoice.getDiscountRate());
                existingInvoice.setShippingCost(invoice.getShippingCost());
                
                // Recalculer les totaux
                existingInvoice.calculateTotals();
                
                invoiceRepository.save(existingInvoice);
                redirectAttributes.addFlashAttribute("success", "Facture modifiée avec succès");
                return "redirect:/invoices/" + existingInvoice.getId();
                
            } else {
                // Création d'une nouvelle facture
                
                // Générer un numéro de facture si nécessaire
                if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
                    invoice.setInvoiceNumber(generateInvoiceNumber());
                }
                
                // Définir les dates par défaut
                if (invoice.getInvoiceDate() == null) {
                    invoice.setInvoiceDate(LocalDate.now());
                }
                if (invoice.getDueDate() == null) {
                    invoice.setDueDate(LocalDate.now().plusDays(30)); // 30 jours par défaut
                }
                
                // Définir le statut initial
                invoice.setStatus(Invoice.InvoiceStatus.DRAFT);
                
                // Si une commande est associée, copier les détails
                if (invoice.getOrder() != null && invoice.getOrder().getId() != null) {
                    Optional<Order> orderOpt = orderRepository.findById(invoice.getOrder().getId());
                    if (orderOpt.isPresent()) {
                        Order order = orderOpt.get();
                        
                        // Vérifier que la commande n'a pas déjà une facture
                        if (order.getInvoice() != null) {
                            redirectAttributes.addFlashAttribute("error", "Cette commande a déjà une facture");
                            return "redirect:/orders/" + order.getId();
                        }
                        
                        // Copier toutes les informations de la commande vers la facture
                        copyOrderToInvoice(order, invoice);
                        
                        System.out.println("Informations copiées de la commande vers la facture");
                        System.out.println("Nombre d'articles dans la commande: " + order.getOrderItems().size());
                    }
                }
                
                // Calculer les totaux
                invoice.calculateTotals();
                
                // Sauvegarder la facture
                Invoice savedInvoice = invoiceRepository.save(invoice);
                
                // Si une commande est associée, la lier à la facture
                if (savedInvoice.getOrder() != null) {
                    Order order = savedInvoice.getOrder();
                    order.setInvoice(savedInvoice);
                    orderRepository.save(order);
                }
                
                System.out.println("Facture créée avec ID: " + savedInvoice.getId());
                redirectAttributes.addFlashAttribute("success", "Facture créée avec succès");
                return "redirect:/invoices/" + savedInvoice.getId();
            }
            
        } catch (Exception e) {
            System.err.println("ERREUR lors de la création/modification de la facture: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
            return "redirect:/invoices/new";
        }
    }

    private void copyOrderToInvoice(Order order, Invoice invoice) {
        // Copier les informations de base
        invoice.setOrder(order);
        invoice.setBillingAddress(order.getBillingAddress());
        invoice.setDiscountRate(order.getDiscountRate() != null ? order.getDiscountRate() : BigDecimal.ZERO);
        invoice.setShippingCost(order.getShippingCost() != null ? order.getShippingCost() : BigDecimal.ZERO);
        
        // Copier les totaux (ils seront recalculés)
        invoice.setTotalAmountHT(order.getTotalAmountHT());
        invoice.setTotalVatAmount(order.getTotalVatAmount());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setDiscountAmount(order.getDiscountAmount());
        
        // Les articles de la commande sont automatiquement liés via la relation Order
        // La facture aura accès aux articles via invoice.getOrder().getOrderItems()
        
        System.out.println("Données copiées:");
        System.out.println("- Commande: " + order.getOrderNumber());
        System.out.println("- Total HT: " + order.getTotalAmountHT());
        System.out.println("- Total TTC: " + order.getTotalAmount());
        System.out.println("- Nombre d'articles: " + order.getOrderItems().size());
    }

    private String generateInvoiceNumber() {
        // Générer un numéro de facture unique
        String datePrefix = LocalDate.now().toString().substring(0, 7).replace("-", ""); // YYYYMM
        
        try {
            // Récupérer toutes les factures
            List<Invoice> allInvoices = invoiceRepository.findAll();
            
            // Compter les factures du mois
            String monthPattern = "FACT-" + datePrefix;
            long monthlyCount = allInvoices.stream()
                    .filter(inv -> inv.getInvoiceNumber() != null)
                    .filter(inv -> inv.getInvoiceNumber().startsWith(monthPattern))
                    .count();
            
            // Générer et vérifier l'unicité
            int nextNumber = (int) monthlyCount + 1;
            String proposedNumber;
            boolean exists;
            
            do {
                proposedNumber = String.format("FACT-%s-%04d", datePrefix, nextNumber);
                final String checkNumber = proposedNumber;
                exists = allInvoices.stream()
                        .anyMatch(inv -> inv.getInvoiceNumber() != null && inv.getInvoiceNumber().equals(checkNumber));
                if (exists) {
                    nextNumber++;
                }
            } while (exists);
            
            return proposedNumber;
            
        } catch (Exception e) {
            // Fallback simple avec timestamp
            return String.format("FACT-%s-%d", datePrefix, System.currentTimeMillis() % 10000);
        }
    }

    /**
     * API endpoint pour récupérer les données d'une commande
     * Utilisé par AJAX pour pré-remplir le formulaire de facture
     */
    @GetMapping("/api/orders/{orderId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOrderData(@PathVariable Long orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Order order = orderOpt.get();
            Map<String, Object> response = new HashMap<>();
            
            // Informations de base de la commande
            response.put("id", order.getId());
            response.put("orderNumber", order.getOrderNumber());
            response.put("orderDate", order.getOrderDate());
            response.put("status", order.getStatus().getDisplayName());
            response.put("billingAddress", order.getBillingAddress());
            response.put("totalAmount", order.getTotalAmount());
            response.put("totalAmountHT", order.getTotalAmountHT());
            response.put("totalVatAmount", order.getTotalVatAmount());
            response.put("discountRate", order.getDiscountRate());
            response.put("shippingCost", order.getShippingCost());
            
            // Informations client
            if (order.getClient() != null) {
                Map<String, Object> clientData = new HashMap<>();
                clientData.put("id", order.getClient().getId());
                clientData.put("name", order.getClient().getName());
                clientData.put("email", order.getClient().getEmail());
                clientData.put("companyName", order.getClient().getCompanyName());
                response.put("client", clientData);
            }
            
            // Articles de la commande
            List<Map<String, Object>> orderItemsData = order.getOrderItems().stream()
                    .map(item -> {
                        Map<String, Object> itemData = new HashMap<>();
                        itemData.put("quantity", item.getQuantity());
                        itemData.put("unitPrice", item.getUnitPrice());
                        itemData.put("discountRate", item.getDiscountRate());
                        itemData.put("vatRate", item.getVatRate());
                        itemData.put("totalPriceHT", item.getTotalPriceHT());
                        itemData.put("description", item.getDescription());
                        
                        if (item.getProduct() != null) {
                            Map<String, Object> productData = new HashMap<>();
                            productData.put("id", item.getProduct().getId());
                            productData.put("name", item.getProduct().getName());
                            productData.put("reference", item.getProduct().getReference());
                            productData.put("unit", item.getProduct().getUnit());
                            itemData.put("product", productData);
                        }
                        
                        return itemData;
                    })
                    .collect(Collectors.toList());
            
            response.put("orderItems", orderItemsData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Erreur lors de la récupération des données de la commande: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}