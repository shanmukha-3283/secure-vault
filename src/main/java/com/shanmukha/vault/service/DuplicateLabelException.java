package com.shanmukha.vault.service;

/**
 * Raised when an entry label is already used (case-insensitive, after trimming).
 * Labels act as a human-readable unique key inside the vault.
 */
public class DuplicateLabelException extends Exception {

    public DuplicateLabelException(String message) {
        super(message);
    }
}
