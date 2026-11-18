package edu.eci.dosw.campusseguro.training.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

// Common page envelope. JSON names are fixed by the integration standards (section 9).
public record PageResponse<T>(
        @JsonProperty("contenido") List<T> content,
        int page,
        int size,
        @JsonProperty("total_elementos") long totalElements,
        @JsonProperty("total_paginas") int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
