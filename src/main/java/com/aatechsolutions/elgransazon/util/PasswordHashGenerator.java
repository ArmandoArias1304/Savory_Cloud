package com.aatechsolutions.elgransazon.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class for generating BCrypt password hashes
 * Useful for creating initial employee passwords
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // Example passwords to hash
        String[] passwords = {"password123", "admin123", "waiter123", "manager123"};
        
        System.out.println("BCrypt Password Hashes:");
        System.out.println("========================");
        
        for (String password : passwords) {
            String hash = encoder.encode(password);
            System.out.println("\nOriginal: " + password);
            System.out.println("Hash: " + hash);
        }
        
        // Test verification
        System.out.println("\n\nTesting verification:");
        System.out.println("========================");
        String testPassword = "password123";
        String testHash = encoder.encode(testPassword);
        boolean matches = encoder.matches(testPassword, testHash);
        System.out.println("Password: " + testPassword);
        System.out.println("Hash: " + testHash);
        System.out.println("Matches: " + matches);
    }
}
