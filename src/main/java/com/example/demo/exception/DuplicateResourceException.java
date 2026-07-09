package com.example.demo.exception;

/** Thrown when a resource with a conflicting unique field (e.g., email) already exists. Results in HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
