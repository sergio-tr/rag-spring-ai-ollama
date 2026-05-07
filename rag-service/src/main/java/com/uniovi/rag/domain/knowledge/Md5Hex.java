package com.uniovi.rag.domain.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class Md5Hex {

    private Md5Hex() {}

    static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute md5", e);
        }
    }
}

