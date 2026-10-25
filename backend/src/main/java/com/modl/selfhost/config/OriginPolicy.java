package com.modl.selfhost.config;

import java.util.ArrayList;
import java.util.List;

public final class OriginPolicy {
    private OriginPolicy() {}

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("*");
        }
        String value = raw.trim();
        if ("*".equals(value)) {
            return List.of("*");
        }
        List<String> origins = new ArrayList<>();
        for (String part : value.split(",")) {
            String clean = part.trim();
            if (!clean.isEmpty()) {
                origins.add(clean.replaceAll("/+$", ""));
            }
        }
        return origins;
    }

    public static boolean isAllowed(String origin, String raw) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        String normalized = origin.replaceAll("/+$", "");
        List<String> allowed = parse(raw);
        if (allowed.contains("*")) {
            return true;
        }
        return allowed.stream().anyMatch(candidate -> {
            String norm = candidate.replaceAll("/+$", "");
            return normalized.equals(norm) || normalized.endsWith(norm);
        });
    }

    public static String[] allowedOriginPatterns(String raw) {
        List<String> allowed = parse(raw);
        if (allowed.contains("*")) {
            return new String[]{"*"};
        }
        return allowed.toArray(String[]::new);
    }
}
