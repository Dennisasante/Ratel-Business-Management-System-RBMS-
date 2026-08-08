package com.ratel.rbms.service;

import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Slugifies a business name into a URL-friendly identifier for the hosted
 * booking page (ratel.app/book/{slug}), appending a numeric suffix on
 * collision. Used both at business creation and when an owner edits their
 * slug from Business Profile.
 */
@Component
public class SlugGenerator {

    public String generate(String name, Predicate<String> exists) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "business";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("-+$", "");
        }

        String candidate = base;
        int suffix = 2;
        while (exists.test(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
