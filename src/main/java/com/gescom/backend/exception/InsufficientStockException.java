package com.gescom.backend.exception;

/**
 * Lancée quand une opération demande plus d'unités qu'il n'y en a en stock.
 * Le message donne le produit, la quantité disponible et la quantité demandée.
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
