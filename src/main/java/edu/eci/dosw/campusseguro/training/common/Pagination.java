package edu.eci.dosw.campusseguro.training.common;

import edu.eci.dosw.campusseguro.training.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Builds a Pageable from the page and size query parameters, with the limits of the standards.
public final class Pagination {

    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("page", "La página debe ser 0 o mayor.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size", "El tamaño de página debe estar entre 1 y " + MAX_SIZE + ".");
        }
        return PageRequest.of(page, size, sort);
    }
}
