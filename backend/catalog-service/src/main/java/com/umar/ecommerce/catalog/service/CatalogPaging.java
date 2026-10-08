package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

final class CatalogPaging {

    private CatalogPaging() {
    }

    static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("size must be between 1 and 100");
        }
    }

    static SortSelection parseSort(String sort, Set<String> fields) {
        String value = sort == null || sort.isBlank() ? "name,asc" : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw new InvalidRequestException(
                    "sort must use the format field,direction, for example name,asc"
            );
        }

        String field = parts[0].trim();
        String directionValue = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!fields.contains(field)) {
            throw new InvalidRequestException(
                    "sort field must be one of " + String.join(", ", fields)
            );
        }
        if (!directionValue.equals("asc") && !directionValue.equals("desc")) {
            throw new InvalidRequestException("sort direction must be asc or desc");
        }

        Sort.Direction direction = directionValue.equals("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return new SortSelection(field, direction, field + "," + directionValue);
    }

    /** Requested sort, then id ascending so equal keys stay stable across pages. */
    static Sort toSort(SortSelection selection) {
        return Sort.by(selection.direction(), selection.field())
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }

    record SortSelection(String field, Sort.Direction direction, String contract) {
    }
}
