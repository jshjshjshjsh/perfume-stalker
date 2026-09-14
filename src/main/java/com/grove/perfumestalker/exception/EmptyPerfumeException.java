package com.grove.perfumestalker.exception;

public class EmptyPerfumeException extends RuntimeException {
    public EmptyPerfumeException(String label) {
        super(label);
    }
}