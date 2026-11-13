package com.reconiq.web;

import com.reconiq.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/** Builds safe pageable objects from untrusted query parameters. */
public final class PageRequests {

    private static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, String sortBy, String direction,
                                 Set<String> allowedSortFields, String defaultSortField) {
        return of(page, size, sortBy, direction, allowedSortFields, defaultSortField, Sort.Direction.DESC);
    }

    public static PageRequest of(int page, int size, String sortBy, String direction,
                                 Set<String> allowedSortFields, String defaultSortField,
                                 Sort.Direction defaultDirection) {
        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Page size must be at least 1");
        }
        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        String field = sortBy == null || sortBy.isBlank() ? defaultSortField : sortBy;
        if (!allowedSortFields.contains(field)) {
            throw new BadRequestException("Unsupported sort field: " + field);
        }

        Sort.Direction dir = parseDirection(direction, defaultDirection);
        return PageRequest.of(page, safeSize, Sort.by(dir, field));
    }

    private static Sort.Direction parseDirection(String direction, Sort.Direction fallback) {
        if (direction == null || direction.isBlank()) {
            return fallback;
        }
        try {
            return Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Sort direction must be 'asc' or 'desc'");
        }
    }

    public static List<String> supported(Set<String> fields) {
        return fields.stream().sorted().toList();
    }
}
