package com.gescom.controller;

// Importation des classes nécessaires
import com.gescom.entity.Invoice;
import com.gescom.entity.User;
import com.gescom.repository.InvoiceRepository;
import com.gescom.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// Annotation qui indique que cette classe est un contrôleur Spring MVC
@Controller
// Définit que toutes les URL de ce contrôleur commencent par "/invoices"
@RequestMapping("/invoices")
public class InvoiceController {

    // Injection automatique du repository des factures
    @Autowired
    private InvoiceRepository invoiceRepository;

    // Injection automatique du repository des utilisateurs
    @Autowired
    private UserRepository userRepository;

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

    // Méthode pour télécharger le PDF (à implémenter)
    @GetMapping("/{id}/pdf")
    public String downloadInvoicePdf(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // TODO: Implémenter la génération PDF
        redirectAttributes.addFlashAttribute("info", "Génération PDF en cours de développement");
        return "redirect:/invoices/" + id;
    }

    // Méthode pour envoyer une relance (à implémenter)
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
                    invoice.getStatus() == Invoice.InvoiceStatus.OVERDUE) {

                // TODO: Implémenter l'envoi de relance par email
                redirectAttributes.addFlashAttribute("success", "Relance envoyée avec succès");
            } else {
                redirectAttributes.addFlashAttribute("warning", "Cette facture ne nécessite pas de relance");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'envoi de la relance: " + e.getMessage());
        }

        return "redirect:/invoices";
    }
}