package com.gescom.backend.mapper;

import com.gescom.backend.dto.payment.PaymentResponse;
import com.gescom.backend.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    private final InvoiceMapper invoiceMapper;

    public PaymentMapper(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

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
                invoiceMapper.toResponse(payment.getInvoice())
        );
    }
}
