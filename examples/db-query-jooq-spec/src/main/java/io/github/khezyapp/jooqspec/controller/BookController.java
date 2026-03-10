package io.github.khezyapp.jooqspec.controller;

import io.github.khezyapp.grammar.ASTSpecs;
import io.github.khezyapp.grammar.ast.builder.ASTSpecConditions;
import io.github.khezyapp.jooqspec.JooqPageRequest;
import io.github.khezyapp.jooqspec.data.Book;
import io.github.khezyapp.jooqspec.data.Page;
import io.github.khezyapp.jooqspec.service.BookService;
import io.github.khezyapp.jooqspec.util.JooqPaginationQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("books")
@RequiredArgsConstructor
public class BookController {
    private final BookService bookService;

    @GetMapping
    public ResponseEntity<Page<Book>> getAll(
            @RequestParam("q") final String q,
            @RequestParam(value = "pageSize", defaultValue = "10") final int pageSize,
            @RequestParam(value = "pageNumber", defaultValue = "0") final int pageNumber
    ) {
        final var pageable = new JooqPageRequest.Builder()
                .pageSize(pageSize)
                .pageNumber(pageNumber)
                .sortFields(List.of("book.id"))
                .sortDirections(List.of("ASC"))
                .build();
        final var jooqPagination = JooqPaginationQueries.of(q, pageable);
        final var result = bookService.getBook(jooqPagination);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/builder")
    public ResponseEntity<Page<Book>> getBooksWithBuilder(
            @RequestParam(value = "id", required = false) final Long id,
            @RequestParam(value = "title", required = false) final String title,
            @RequestParam(value = "price", required = false) final Double price,
            @RequestParam(value = "pageSize", defaultValue = "10") final int size,
            @RequestParam(value = "pageNumber", defaultValue = "0") final int page
    ) {
        final var pageable = new JooqPageRequest.Builder()
                .pageSize(size)
                .pageNumber(page)
                .sortFields(List.of("book.id"))
                .sortDirections(List.of("ASC"))
                .build();
        final var querySpec = ASTSpecs.builder()
                .where(
                        ASTSpecConditions.and(
                                ASTSpecConditions.ilike("book.title", title),
                                ASTSpecConditions.eq("book.id", id),
                                ASTSpecConditions.eq("book.price", price)
                        )
                )
                .build();
        final var jooqPagination = JooqPaginationQueries.of(querySpec, pageable);
        final var result = bookService.getBook(jooqPagination);
        return ResponseEntity.ok(result);
    }
}
