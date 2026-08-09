package com.gescom.backend.mapper;

import com.gescom.backend.dto.delivery.DeliveryCreateRequest;
import com.gescom.backend.dto.delivery.DeliveryResponse;
import com.gescom.backend.dto.delivery.DeliveryUpdateRequest;
import com.gescom.backend.dto.invoice.InvoiceCreateRequest;
import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.dto.order.OrderCreateRequest;
import com.gescom.backend.dto.order.OrderItemRequest;
import com.gescom.backend.dto.order.OrderItemResponse;
import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.dto.order.OrderUpdateRequest;
import com.gescom.backend.dto.payment.PaymentResponse;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Payment;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Conversions de la chaîne de vente : commande, facture, livraison et paiement carte.
 *
 * Ces quatre documents se citent en cascade — une réponse de paiement contient sa facture,
 * qui contient sa commande, qui contient ses lignes. Les réunir évite d'injecter trois mappers
 * les uns dans les autres ; ne reste que la dépendance vers {@link ReferenceMapper} pour le
 * client, le produit et l'utilisateur.
 *
 * Les résolutions de clés étrangères restent ici (contrat des DTO : les *Request ne portent que
 * des identifiants scalaires).
 */
@Component
public class SalesMapper {

    private final ReferenceMapper referenceMapper;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public SalesMapper(ReferenceMapper referenceMapper,
                       ClientRepository clientRepository,
                       ProductRepository productRepository,
                       OrderRepository orderRepository) {
        this.referenceMapper = referenceMapper;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    // ---------------------------------------------------------------- Commande

    public OrderResponse toResponse(Order order) {
        return toResponse(order, null);
    }

    /** `invoice` peut être null : la commande n'est alors pas encore facturée. */
    public OrderResponse toResponse(Order order, Invoice invoice) {
        if (order == null) return null;
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                referenceMapper.toResponse(order.getClient()),
                referenceMapper.toResponse(order.getCreatedBy()),
                order.getItems() != null
                        ? order.getItems().stream().map(this::toItemResponse).toList()
                        : List.of(),
                order.getTotalAmount(),
                order.getDiscount(),
                order.getFinalAmount(),
                order.getStatus(),
                invoice != null ? invoice.getStatus() : null,
                invoice != null ? invoice.getTotalAmount() : null,
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) return null;
        return new OrderItemResponse(
                item.getId(),
                referenceMapper.toResponse(item.getProduct()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getDiscount()
        );
    }

    public Order toEntity(OrderCreateRequest request) {
        Order order = new Order();

        // Client optionnel : null = vente de passage (aucune fiche client).
        if (request.clientId() != null) {
            Client client = clientRepository.findById(request.clientId())
                    .orElseThrow(() -> new ResourceNotFoundException("client", request.clientId()));
            order.setClient(client);
        }

        order.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        order.setNotes(request.notes());
        order.getItems().addAll(request.items().stream().map(this::buildItem).toList());
        return order;
    }

    // Objet de patch : les champs absents de la requête restent à null pour que
    // OrderService.updateOrder les distingue d'une valeur envoyée et les laisse intacts.
    // D'où le null explicite sur la remise, que l'entité initialise sinon à zéro.
    public Order toUpdate(OrderUpdateRequest request) {
        Order patch = new Order();
        patch.setDiscount(request.discount());
        patch.setNotes(request.notes());
        patch.getItems().addAll(request.items().stream().map(this::buildItem).toList());
        return patch;
    }

    private OrderItem buildItem(OrderItemRequest req) {
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("product", req.productId()));
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(req.quantity());
        item.setDiscount(req.discount() != null ? req.discount() : BigDecimal.ZERO);
        return item;
    }

    // ---------------------------------------------------------------- Facture

    public InvoiceResponse toResponse(Invoice invoice) {
        if (invoice == null) return null;
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                toResponse(invoice.getOrder()),
                invoice.getDelivery() != null ? invoice.getDelivery().getId() : null,
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getDiscount(),
                invoice.getTaxAmount(),
                invoice.getTaxRate(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getRemainingAmount(),
                invoice.getStatus(),
                invoice.getPaymentMethod(),
                invoice.getPaymentDate(),
                invoice.getNotes(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }

    public Invoice toEntity(InvoiceCreateRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("order", request.orderId()));

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setPaymentMethod(request.paymentMethod());
        invoice.setTaxRate(request.taxRate() != null ? request.taxRate() : BigDecimal.ZERO);
        invoice.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        invoice.setPaidAmount(request.paidAmount() != null ? request.paidAmount() : BigDecimal.ZERO);
        invoice.setNotes(request.notes());
        return invoice;
    }

    // ---------------------------------------------------------------- Livraison

    public DeliveryResponse toResponse(Delivery delivery) {
        if (delivery == null) return null;
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getDeliveryNumber(),
                toResponse(delivery.getOrder()),
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

    // ---------------------------------------------------------------- Paiement carte

    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) return null;
        return new PaymentResponse(
                payment.getId(),
                payment.getProvider(),
                payment.getIntentId(),
                payment.getClientSecret(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getCardBrand(),
                payment.getCardLast4(),
                payment.getFailureMessage(),
                payment.isSimulated(),
                payment.getCreatedAt(),
                payment.getConfirmedAt(),
                toResponse(payment.getInvoice())
        );
    }
}
