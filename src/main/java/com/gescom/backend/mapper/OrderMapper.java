package com.gescom.backend.mapper;

import com.gescom.backend.dto.order.OrderCreateRequest;
import com.gescom.backend.dto.order.OrderItemRequest;
import com.gescom.backend.dto.order.OrderItemResponse;
import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.dto.order.OrderUpdateRequest;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderMapper {

    private final ClientMapper clientMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    public OrderMapper(ClientMapper clientMapper,
                       UserMapper userMapper,
                       ProductMapper productMapper,
                       ClientRepository clientRepository,
                       ProductRepository productRepository) {
        this.clientMapper = clientMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
    }

    public OrderResponse toResponse(Order order) {
        if (order == null) return null;
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                clientMapper.toResponse(order.getClient()),
                order.getCreatedBy() != null ? userMapper.toResponse(order.getCreatedBy()) : null,
                order.getItems() != null
                        ? order.getItems().stream().map(this::toItemResponse).toList()
                        : List.of(),
                order.getTotalAmount(),
                order.getDiscount(),
                order.getTax(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) return null;
        return new OrderItemResponse(
                item.getId(),
                productMapper.toResponse(item.getProduct()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getDiscount()
        );
    }

    public Order toEntity(OrderCreateRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));

        Order order = new Order();
        order.setClient(client);
        order.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        order.setTax(request.tax() != null ? request.tax() : BigDecimal.ZERO);
        order.setNotes(request.notes());
        order.getItems().addAll(request.items().stream().map(this::buildItem).toList());
        return order;
    }

    public Order toUpdate(OrderUpdateRequest request) {
        Order patch = new Order();
        if (request.status() != null) {
            patch.setStatus(request.status());
        }
        if (request.discount() != null) {
            patch.setDiscount(request.discount());
        }
        if (request.tax() != null) {
            patch.setTax(request.tax());
        }
        patch.setNotes(request.notes());
        patch.getItems().addAll(request.items().stream().map(this::buildItem).toList());
        return patch;
    }

    private OrderItem buildItem(OrderItemRequest req) {
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit", req.productId()));
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(req.quantity());
        return item;
    }
}
