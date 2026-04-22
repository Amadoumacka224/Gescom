package com.gescom.backend.exception;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String productName, int available, int requested) {
        super("Stock insuffisant pour le produit '" + productName + "'. Disponible: " + available + ", Demandé: " + requested);
    }
}
