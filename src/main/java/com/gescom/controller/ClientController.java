package com.gescom.controller;

import com.gescom.entity.Client;
import com.gescom.entity.User;
import com.gescom.repository.ClientRepository;
import com.gescom.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients")
public class ClientController {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String listClients(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Model model) {

        try {
            // Récupérer l'utilisateur connecté
            Optional<User> currentUserOpt = userRepository.findByUsername(userDetails.getUsername());
            if (currentUserOpt.isEmpty()) {
                model.addAttribute("error", "Utilisateur non trouvé");
                return "redirect:/dashboard";
            }

            User currentUser = currentUserOpt.get();

            // Direction du tri
            Sort.Direction direction = sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            // Récupérer les clients selon le rôle
            List<Client> allClients;
            if (currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER")) {
                // Admin et Manager voient tous les clients
                allClients = clientRepository.findAll();
            } else {
                // Utilisateur normal voit seulement ses clients assignés
                allClients = clientRepository.findAll().stream()
                        .filter(client -> client.getAssignedUser() != null &&
                                client.getAssignedUser().getId().equals(currentUser.getId()))
                        .collect(Collectors.toList());
            }

            // Filtrage par recherche
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                allClients = allClients.stream()
                        .filter(client ->
                                client.getName().toLowerCase().contains(searchLower) ||
                                        client.getEmail().toLowerCase().contains(searchLower) ||
                                        (client.getCompanyName() != null && client.getCompanyName().toLowerCase().contains(searchLower)))
                        .collect(Collectors.toList());
            }

            // Filtrage par statut
            if (status != null && !status.trim().isEmpty()) {
                Client.ClientStatus clientStatus = Client.ClientStatus.valueOf(status);
                allClients = allClients.stream()
                        .filter(client -> client.getStatus() == clientStatus)
                        .collect(Collectors.toList());
            }

            // Pagination manuelle (pour simplifier, en production utiliser des requêtes paginées)
            int start = Math.min(page * size, allClients.size());
            int end = Math.min(start + size, allClients.size());
            List<Client> clientsPage = allClients.subList(start, end);

            // Calculs pour la pagination
            int totalPages = (int) Math.ceil((double) allClients.size() / size);
            boolean hasNext = page < totalPages - 1;
            boolean hasPrevious = page > 0;

            // Statistiques
            long totalClients = allClients.size();
            long activeClients = allClients.stream().filter(c -> c.getStatus() == Client.ClientStatus.ACTIVE).count();
            long prospects = allClients.stream().filter(c -> c.getStatus() == Client.ClientStatus.PROSPECT).count();
            long companies = allClients.stream().filter(c -> c.getClientType() == Client.ClientType.COMPANY).count();

            // Récupérer la liste des commerciaux pour l'assignation
            List<User> commercials = userRepository.findAll().stream()
                    .filter(user -> user.hasRole("USER"))
                    .collect(Collectors.toList());

            // Ajouter les attributs au modèle
            model.addAttribute("clients", clientsPage);
            model.addAttribute("commercials", commercials);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", hasNext);
            model.addAttribute("hasPrevious", hasPrevious);
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("search", search);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("size", size);

            // Statistiques
            model.addAttribute("totalClients", totalClients);
            model.addAttribute("activeClients", activeClients);
            model.addAttribute("prospects", prospects);
            model.addAttribute("companies", companies);

            // Vérification des permissions
            model.addAttribute("canEdit", currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER"));
            model.addAttribute("canDelete", currentUser.hasRole("ADMIN"));
            model.addAttribute("canCreate", true); // Tous peuvent créer des clients

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des clients: " + e.getMessage());
        }

        return "clients/list";
    }

    @GetMapping("/new")
    public String newClient(Model model) {
        model.addAttribute("client", new Client());
        model.addAttribute("isEdit", false);

        // Liste des commerciaux pour assignation
        List<User> commercials = userRepository.findAll().stream()
                .filter(user -> user.hasRole("USER"))
                .collect(Collectors.toList());
        model.addAttribute("commercials", commercials);

        return "clients/form";
    }

    @GetMapping("/{id}")
    public String viewClient(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Client> clientOpt = clientRepository.findById(id);
        if (clientOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Client non trouvé");
            return "redirect:/clients";
        }

        model.addAttribute("client", clientOpt.get());
        return "clients/detail";
    }

    @GetMapping("/{id}/edit")
    public String editClient(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Client> clientOpt = clientRepository.findById(id);
        if (clientOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Client non trouvé");
            return "redirect:/clients";
        }

        model.addAttribute("client", clientOpt.get());
        model.addAttribute("isEdit", true);

        // Liste des commerciaux pour assignation
        List<User> commercials = userRepository.findAll().stream()
                .filter(user -> user.hasRole("USER"))
                .collect(Collectors.toList());
        model.addAttribute("commercials", commercials);

        return "clients/form";
    }

    @PostMapping
    public String saveClient(
            @Valid @ModelAttribute Client client,
            BindingResult result,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            model.addAttribute("isEdit", client.getId() != null);
            List<User> commercials = userRepository.findAll().stream()
                    .filter(user -> user.hasRole("USER"))
                    .collect(Collectors.toList());
            model.addAttribute("commercials", commercials);
            return "clients/form";
        }

        try {
            // Si nouveau client et pas d'utilisateur assigné, assigner à l'utilisateur connecté
            if (client.getId() == null && client.getAssignedUser() == null) {
                Optional<User> currentUser = userRepository.findByUsername(userDetails.getUsername());
                if (currentUser.isPresent() && currentUser.get().hasRole("USER")) {
                    client.setAssignedUser(currentUser.get());
                }
            }

            clientRepository.save(client);

            String message = client.getId() != null ? "Client modifié avec succès" : "Client créé avec succès";
            redirectAttributes.addFlashAttribute("success", message);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
        }

        return "redirect:/clients";
    }

    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Client> clientOpt = clientRepository.findById(id);
            if (clientOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Client non trouvé");
                return "redirect:/clients";
            }

            clientRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Client supprimé avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression: " + e.getMessage());
        }

        return "redirect:/clients";
    }

    @PostMapping("/{id}/assign")
    public String assignClient(
            @PathVariable Long id,
            @RequestParam Long userId,
            RedirectAttributes redirectAttributes) {

        try {
            Optional<Client> clientOpt = clientRepository.findById(id);
            Optional<User> userOpt = userRepository.findById(userId);

            if (clientOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Client non trouvé");
                return "redirect:/clients";
            }

            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
                return "redirect:/clients";
            }

            Client client = clientOpt.get();
            client.setAssignedUser(userOpt.get());
            clientRepository.save(client);

            redirectAttributes.addFlashAttribute("success", "Client assigné avec succès à " + userOpt.get().getFullName());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'assignation: " + e.getMessage());
        }

        return "redirect:/clients";
    }
}