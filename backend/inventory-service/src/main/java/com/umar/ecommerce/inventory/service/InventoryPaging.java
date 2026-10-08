package com.umar.ecommerce.inventory.service;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

final class InventoryPaging {

    private InventoryPaging() {
    }

    static void validate(int page, int size) {
        if (page < 0) {
            throw invalid("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw invalid("size must be between 1 and 100");
        }
    }

    static SortSelection parse(String sort, Set<String> fields, String defaultSort) {
        String value = sort == null || sort.isBlank() ? defaultSort : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw invalid("sort must use the format field,direction");
        }
        String field = parts[0].trim();
        String directionValue = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!fields.contains(field)) {
            throw invalid("sort field must be one of " + String.join(", ", fields));
        }
        if (!directionValue.equals("asc") && !directionValue.equals("desc")) {
            throw invalid("sort direction must be asc or desc");
        }
        Sort.Direction direction = directionValue.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return new SortSelection(field, direction, field + "," + directionValue);
    }

    static Sort toSort(SortSelection selection) {
        return Sort.by(selection.direction(), selection.field()).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    static Sort historySort(SortSelection selection) {
        Sort.Direction idDirection = selection.direction();
        return Sort.by(selection.direction(), selection.field()).and(Sort.by(idDirection, "id"));
    }

    private static InventoryProblem invalid(String message) {
        return new InventoryProblem(HttpStatus.BAD_REQUEST, InventoryProblem.VALIDATION_FAILED, message);
    }

    record SortSelection(String field, Sort.Direction direction, String contract) {
    }
}
