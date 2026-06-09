package com.geointelli.ai.property.service.util;

import java.security.MessageDigest;

public class HashUtil {
    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } 
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
