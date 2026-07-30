package com.gescom.backend.exception;

/**
 * Levée lorsqu'une opération demande plus d'unités qu'il n'y en a en stock.
 * Le message indique le produit, le disponible et le demandé pour faciliter le diagnostic.
 */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String productName, int available, int requested) {
        super(
                "stock.insufficient.detail",
                new Object[] { productName, available, requested },
                "Stock insuffisant pour le produit '" + productName + "'. Disponible: " + available
                        + ", Demandé: " + requested
        );
    }
}
