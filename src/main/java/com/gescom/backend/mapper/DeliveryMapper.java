package com.gescom.backend.mapper;

import com.gescom.backend.dto.delivery.DeliveryCreateRequest;
import com.gescom.backend.dto.delivery.DeliveryResponse;
import com.gescom.backend.dto.delivery.DeliveryUpdateRequest;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.OrderRepository;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMapper {

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;

    public DeliveryMapper(OrderMapper orderMapper, OrderRepository orderRepository) {
        this.orderMapper = orderMapper;
        this.orderRepository = orderRepository;
    }

    public DeliveryResponse toResponse(Delivery delivery) {
        if (delivery == null) return null;
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getDeliveryNumber(),
                orderMapper.toResponse(delivery.getOrder()),
                delivery.getDeliveryAddress(),
                delivery.getDeliveryCity(),
                delivery.getDeliveryPostalCode(),
                delivery.getDeliveryCountry(),
                delivery.getContactName(),
                delivery.getContactPhone(),
                delivery.getStatus(),
                delivery.getScheduledDate(),
                delivery.getDeliveredDate(),
                delivery.getDeliveredBy(),
                delivery.getNotes(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }

    public Delivery toEntity(DeliveryCreateRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("order", request.orderId()));

        Delivery delivery = new Delivery();
        delivery.setOrder(order);
        delivery.setDeliveryAddress(request.deliveryAddress());
        delivery.setDeliveryCity(request.deliveryCity());
        delivery.setDeliveryPostalCode(request.deliveryPostalCode());
        delivery.setDeliveryCountry(request.deliveryCountry());
        delivery.setContactName(request.contactName());
        delivery.setContactPhone(request.contactPhone());
        delivery.setScheduledDate(request.scheduledDate());
        delivery.setNotes(request.notes());
        return delivery;
    }

    public Delivery applyUpdate(Delivery target, DeliveryUpdateRequest request) {
        target.setDeliveryAddress(request.deliveryAddress());
        target.setDeliveryCity(request.deliveryCity());
        target.setDeliveryPostalCode(request.deliveryPostalCode());
        target.setDeliveryCountry(request.deliveryCountry());
        target.setContactName(request.contactName());
        target.setContactPhone(request.contactPhone());
        target.setScheduledDate(request.scheduledDate());
        target.setStatus(request.status());
        target.setNotes(request.notes());
        return target;
    }
}
