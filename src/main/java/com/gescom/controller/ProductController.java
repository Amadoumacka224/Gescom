package com.gescom.controller;

import com.gescom.entity.Product;
import com.gescom.entity.User;
import com.gescom.repository.ProductRepository;
import com.gescom.repository.UserRepository;
import com.gescom.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImageService imageService;

    @GetMapping
    public String listProducts(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lowStock,
            Model model) {

        try {
            // Récupérer l'utilisateur connecté
            Optional<User> currentUserOpt = userRepository.findByUsername(userDetails.getUsername());
            if (currentUserOpt.isEmpty()) {
                model.addAttribute("error", "Utilisateur non trouvé");
                return "redirect:/dashboard";
            }

            User currentUser = currentUserOpt.get();

            // Récupérer tous les produits
            List<Product> allProducts = productRepository.findAll();

            // Filtrage par recherche
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                allProducts = allProducts.stream()
                        .filter(product ->
                                product.getName().toLowerCase().contains(searchLower) ||
                                        product.getDescription().toLowerCase().contains(searchLower) ||
                                        product.getReference().toLowerCase().contains(searchLower))
                        .collect(Collectors.toList());
            }

            // Filtrage par catégorie
            if (category != null && !category.trim().isEmpty()) {
                allProducts = allProducts.stream()
                        .filter(product -> product.getCategory() != null &&
                                product.getCategory().equalsIgnoreCase(category))
                        .collect(Collectors.toList());
            }

            // Filtrage par stock bas
            if (lowStock != null && lowStock) {
                allProducts = allProducts.stream()
                        .filter(product -> product.getMinStock() != null && product.getStock() <= product.getMinStock())
                        .collect(Collectors.toList());
            }

            // Tri
            switch (sortBy) {
                case "name" -> allProducts.sort((a, b) -> sortDir.equals("desc") ?
                        b.getName().compareTo(a.getName()) : a.getName().compareTo(b.getName()));
                case "price" -> allProducts.sort((a, b) -> {
                    BigDecimal priceA = a.getUnitPrice();
                    BigDecimal priceB = b.getUnitPrice();
                    return sortDir.equals("desc") ? priceB.compareTo(priceA) : priceA.compareTo(priceB);
                });
                case "stock" -> allProducts.sort((a, b) -> {
                    Integer stockA = a.getStock();
                    Integer stockB = b.getStock();
                    return sortDir.equals("desc") ? stockB.compareTo(stockA) : stockA.compareTo(stockB);
                });
            }

            // Pagination manuelle
            int start = Math.min(page * size, allProducts.size());
            int end = Math.min(start + size, allProducts.size());
            List<Product> productsPage = allProducts.subList(start, end);

            // Calculs pour la pagination
            int totalPages = (int) Math.ceil((double) allProducts.size() / size);
            boolean hasNext = page < totalPages - 1;
            boolean hasPrevious = page > 0;

            // Statistiques
            long totalProducts = allProducts.size();
            long activeProducts = allProducts.stream().filter(p -> p.getIsActive() != null && p.getIsActive()).count();
            long lowStockProducts = allProducts.stream()
                    .filter(p -> p.getMinStock() != null && p.getStock() <= p.getMinStock())
                    .count();

            // Valeur totale du stock
            BigDecimal totalStockValue = allProducts.stream()
                    .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getStock())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Catégories disponibles
            List<String> categories = allProducts.stream()
                    .map(Product::getCategory)
                    .filter(cat -> cat != null && !cat.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            // Ajouter les attributs au modèle
            model.addAttribute("products", productsPage);
            model.addAttribute("categories", categories);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", hasNext);
            model.addAttribute("hasPrevious", hasPrevious);
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("search", search);
            model.addAttribute("selectedCategory", category);
            model.addAttribute("lowStockFilter", lowStock);
            model.addAttribute("size", size);

            // Statistiques
            model.addAttribute("totalProducts", totalProducts);
            model.addAttribute("activeProducts", activeProducts);
            model.addAttribute("lowStockProducts", lowStockProducts);
            model.addAttribute("totalStockValue", totalStockValue);

            // Vérification des permissions
            model.addAttribute("canEdit", currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER"));
            model.addAttribute("canDelete", currentUser.hasRole("ADMIN"));
            model.addAttribute("canCreate", currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER"));

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des produits: " + e.getMessage());
        }

        return "products/list";
    }




    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("isEdit", false);
        return "products/form";
    }

    @GetMapping("/{id}")
    public String viewProduct(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (!productOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Produit non trouvé");
            return "redirect:/products";
        }

        model.addAttribute("product", productOpt.get());
        return "products/detail";
    }

    @GetMapping("/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Produit non trouvé");
            return "redirect:/products";
        }

        model.addAttribute("product", productOpt.get());
        model.addAttribute("isEdit", true);
        return "products/form";
    }

    @PostMapping
    public String saveProduct(
            @Valid @ModelAttribute Product product,
            BindingResult result,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            model.addAttribute("isEdit", product.getId() != null);
            return "products/form";
        }

        try {
            // Validation métier
            if (product.getPurchasePrice() != null && product.getUnitPrice().compareTo(product.getPurchasePrice()) < 0) {
                result.rejectValue("unitPrice", "error.product", "Le prix de vente doit être supérieur au prix d'achat");
                model.addAttribute("isEdit", product.getId() != null);
                return "products/form";
            }

            // Gestion de l'image
            String oldImageUrl = null;
            if (product.getId() != null) {
                // Récupérer l'ancienne image pour suppression éventuelle
                Optional<Product> existingProduct = productRepository.findById(product.getId());
                if (existingProduct.isPresent()) {
                    oldImageUrl = existingProduct.get().getImageUrl();
                }
            }

            // Upload de la nouvelle image si un fichier est fourni
            if (imageFile != null && !imageFile.isEmpty()) {
                try {
                    String imageUrl = imageService.saveImage(imageFile);
                    product.setImageUrl(imageUrl);
                    
                    // Supprimer l'ancienne image si elle existe
                    if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
                        imageService.deleteImage(oldImageUrl);
                    }
                } catch (Exception e) {
                    redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload de l'image: " + e.getMessage());
                    model.addAttribute("isEdit", product.getId() != null);
                    return "products/form";
                }
            } else if (product.getImageUrl() != null && !product.getImageUrl().trim().isEmpty()) {
                // Valider l'URL si fournie
                if (!imageService.isValidImageUrl(product.getImageUrl())) {
                    redirectAttributes.addFlashAttribute("error", "URL d'image invalide");
                    model.addAttribute("isEdit", product.getId() != null);
                    return "products/form";
                }
            }

            // Générer une référence si elle n'existe pas
            if (product.getReference() == null || product.getReference().trim().isEmpty()) {
                product.setReference(generateProductReference(product));
            }

            // S'assurer que le produit est actif par défaut
            if (product.getIsActive() == null) {
                product.setIsActive(true);
            }

            productRepository.save(product);

            String message = product.getId() != null ? "Produit modifié avec succès" : "Produit créé avec succès";
            redirectAttributes.addFlashAttribute("success", message);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
        }

        return "redirect:/products";
    }

    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Product> productOpt = productRepository.findById(id);
            if (productOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Produit non trouvé");
                return "redirect:/products";
            }

            Product product = productOpt.get();
            
            // Supprimer l'image associée si elle existe
            if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                imageService.deleteImage(product.getImageUrl());
            }

            productRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Produit supprimé avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression: " + e.getMessage());
        }

        return "redirect:/products";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleProductStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Product> productOpt = productRepository.findById(id);
            if (productOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Produit non trouvé");
                return "redirect:/products";
            }

            Product product = productOpt.get();
            product.setIsActive(!product.getIsActive());
            productRepository.save(product);

            String status = product.getIsActive() ? "activé" : "désactivé";
            redirectAttributes.addFlashAttribute("success", "Produit " + status + " avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors du changement de statut: " + e.getMessage());
        }

        return "redirect:/products";
    }

    @PostMapping("/{id}/adjust-stock")
    public String adjustStock(
            @PathVariable Long id,
            @RequestParam Integer adjustment,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        try {
            Optional<Product> productOpt = productRepository.findById(id);
            if (productOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Produit non trouvé");
                return "redirect:/products";
            }

            Product product = productOpt.get();
            int currentStock = product.getStock();
            int newStock = currentStock + adjustment;

            if (newStock < 0) {
                redirectAttributes.addFlashAttribute("error", "Le stock ne peut pas être négatif");
                // Retourner vers la page de détails si elle existe
                String referer = request.getHeader("Referer");
                if (referer != null && referer.contains("/products/" + id)) {
                    return "redirect:/products/" + id;
                }
                return "redirect:/products";
            }

            product.setStock(newStock);
            productRepository.save(product);

            String message = String.format("Stock ajusté: %+d (Raison: %s)", adjustment, reason);
            redirectAttributes.addFlashAttribute("success", message);

            // Retourner vers la page de détails si elle existe
            String referer = request.getHeader("Referer");
            if (referer != null && referer.contains("/products/" + id)) {
                return "redirect:/products/" + id;
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'ajustement: " + e.getMessage());
        }

        return "redirect:/products";
    }

    private String generateProductReference(Product product) {
        // Générer une référence basée sur la catégorie et un numéro séquentiel
        String categoryPrefix = "PROD";
        if (product.getCategory() != null) {
            categoryPrefix = switch (product.getCategory().toUpperCase()) {
                case "LOGICIELS" -> "LOG";
                case "MATÉRIEL", "MATERIEL" -> "MAT";
                case "SERVICES" -> "SRV";
                default -> "PROD";
            };
        }

        // Compter les produits existants pour générer un numéro séquentiel
        long count = productRepository.count() + 1;
        return String.format("%s-%03d", categoryPrefix, count);
    }
}