package com.gescom.backend.mapper;

import com.gescom.backend.dto.activity.ActivityLogResponse;
import com.gescom.backend.dto.category.CategoryRequest;
import com.gescom.backend.dto.category.CategoryResponse;
import com.gescom.backend.dto.client.ClientDataExport;
import com.gescom.backend.dto.client.ClientRequest;
import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.dto.product.ProductRequest;
import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CategoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * Conversions des référentiels : utilisateur, catégorie, client, produit et journal d'activité.
 *
 * Ce sont les entités que les autres mappers citent sans jamais être citées par elles ;
 * les regrouper ici supprime les injections croisées entre mappers et pose une couche basse
 * dont {@link SalesMapper} et {@link StockMapper} dépendent, sans cycle possible.
 *
 * Les méthodes sont surchargées sur le type d'entité : le compilateur choisit la conversion,
 * il n'y a donc qu'un nom à retenir par sens de conversion.
 */
@Component
public class ReferenceMapper {

    private final CategoryRepository categoryRepository;

    public ReferenceMapper(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // ---------------------------------------------------------------- Utilisateur

    public UserResponse toResponse(User user) {
        if (user == null) return null;
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    // ---------------------------------------------------------------- Journal d'activité

    public ActivityLogResponse toResponse(ActivityLog log) {
        if (log == null) return null;
        return new ActivityLogResponse(
                log.getId(),
                toResponse(log.getUser()),
                log.getActionType(),
                log.getEntity(),
                log.getEntityId(),
                log.getDescription(),
                log.getIpAddress(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }

    // ---------------------------------------------------------------- Catégorie

    public CategoryResponse toResponse(Category category) {
        if (category == null) return null;
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCode(),
                category.getActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public Category toEntity(CategoryRequest request) {
        return applyRequest(new Category(), request);
    }

    public Category applyRequest(Category target, CategoryRequest request) {
        target.setName(request.name());
        target.setDescription(request.description());
        target.setCode(request.code());
        if (request.active() != null) {
            target.setActive(request.active());
        }
        return target;
    }

    // ---------------------------------------------------------------- Client

    public ClientResponse toResponse(Client client) {
        if (client == null) return null;
        return new ClientResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                buildFullName(client),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getCity(),
                client.getPostalCode(),
                client.getCountry(),
                client.getCompany(),
                client.getType(),
                client.getActive(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }

    /** Assemble la réponse à une demande d'accès RGPD : le client et son historique de commandes. */
    public ClientDataExport toDataExport(Client client, List<Order> orders) {
        return new ClientDataExport(
                LocalDateTime.now(),
                toResponse(client),
                orders.stream()
                        .map(order -> new ClientDataExport.OrderHistoryEntry(
                                order.getOrderNumber(),
                                order.getCreatedAt(),
                                order.getStatus(),
                                order.getFinalAmount()))
                        .toList()
        );
    }

    public Client toEntity(ClientRequest request) {
        return applyRequest(new Client(), request);
    }

    public Client applyRequest(Client target, ClientRequest request) {
        target.setFirstName(request.firstName());
        target.setLastName(request.lastName());
        target.setEmail(request.email());
        target.setPhone(request.phone());
        target.setAddress(request.address());
        target.setCity(request.city());
        target.setPostalCode(request.postalCode());
        target.setCountry(request.country());
        target.setCompany(request.company());
        target.setType(request.type());
        if (request.active() != null) {
            target.setActive(request.active());
        }
        return target;
    }

    // Concatène prénom et nom en un nom complet propre (gère les valeurs manquantes).
    private String buildFullName(Client client) {
        return Stream.of(client.getFirstName(), client.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    /**
     * Libellé du client d'une vente. Null pour une vente de passage — le client n'est pas
     * obligatoire sur une commande, et l'écran affiche alors un tiret plutôt qu'un nom vide.
     */
    public static String clientName(Client client) {
        if (client == null) return null;
        String name = ((client.getFirstName() != null ? client.getFirstName() : "") + " "
                + (client.getLastName() != null ? client.getLastName() : "")).trim();
        if (!name.isEmpty()) return name;
        return client.getCompany();
    }

    // ---------------------------------------------------------------- Produit

    public ProductResponse toResponse(Product product) {
        if (product == null) return null;
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.getPurchasePrice(),
                product.getSellingPrice(),
                toResponse(product.getCategory()),
                product.getUnit(),
                product.getStockQuantity(),
                product.getMinStockAlert(),
                product.getBarcode(),
                product.getImageUrl(),
                product.getActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public Product toEntity(ProductRequest request) {
        return applyRequest(new Product(), request);
    }

    public Product applyRequest(Product target, ProductRequest request) {
        if (request.code() != null && !request.code().isBlank()) {
            target.setCode(request.code());
        }
        target.setName(request.name());
        target.setDescription(request.description());
        target.setPurchasePrice(request.purchasePrice());
        target.setSellingPrice(request.sellingPrice());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("category", request.categoryId()));
            target.setCategory(category);
        } else {
            target.setCategory(null);
        }

        if (request.unit() != null) target.setUnit(request.unit());
        if (request.stockQuantity() != null) target.setStockQuantity(request.stockQuantity());
        if (request.minStockAlert() != null) target.setMinStockAlert(request.minStockAlert());
        target.setBarcode(request.barcode());
        target.setImageUrl(request.imageUrl());
        if (request.active() != null) target.setActive(request.active());
        return target;
    }
}
