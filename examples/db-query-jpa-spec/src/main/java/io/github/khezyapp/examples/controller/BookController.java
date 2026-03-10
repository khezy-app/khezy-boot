package io.github.khezyapp.examples.controller;

import io.github.khezyapp.examples.data.BookData;
import io.github.khezyapp.examples.entity.Book;
import io.github.khezyapp.examples.repo.BookRepository;
import io.github.khezyapp.grammar.ASTSpecs;
import io.github.khezyapp.grammar.ast.builder.ASTSpecConditions;
import io.github.khezyapp.jpaspec.JpaPaginationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/books")
public class BookController {
    private final BookRepository bookRepository;

    @GetMapping
    public ResponseEntity<Page<BookData>> getBooks(
            @RequestParam("q") final String q,
            @RequestParam(value = "sizeSize", defaultValue = "10") final int size,
            @RequestParam(value = "pageNumber", defaultValue = "0") final int page
    ) {
        final var pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");
        final var filter = new JpaPaginationQuery<Book>(q, pageable);
        final var books = bookRepository.findAll(
                filter.getSpecification(),
                filter.getPageable()
        );
        final var bookData = books.stream()
                .map(b -> BookData.from(b, true))
                .toList();
        final var returnData = new PageImpl<>(bookData, filter.getPageable(), books.getTotalElements());
        return ResponseEntity.ok(returnData);
    }

    @GetMapping("/builder")
    public ResponseEntity<Page<BookData>> getBooksWithBuilder(
            @RequestParam(value = "id", required = false) final Long id,
            @RequestParam(value = "title", required = false) final String title,
            @RequestParam(value = "price", required = false) final Double price,
            @RequestParam(value = "sizeSize", defaultValue = "10") final int size,
            @RequestParam(value = "pageNumber", defaultValue = "0") final int page
    ) {
        final var pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");
        final var querySpec = ASTSpecs.builder()
                .where(
                        ASTSpecConditions.and(
                                ASTSpecConditions.ilike("title", title),
                                ASTSpecConditions.eq("id", id),
                                ASTSpecConditions.eq("price", price)
                        )
                )
                .build();
        final var filter = new JpaPaginationQuery<Book>(querySpec, pageable);
        final var books = bookRepository.findAll(
                filter.getSpecification(),
                filter.getPageable()
        );
        final var bookData = books.stream()
                .map(b -> BookData.from(b, true))
                .toList();
        final var returnData = new PageImpl<>(bookData, filter.getPageable(), books.getTotalElements());
        return ResponseEntity.ok(returnData);
    }
}
