package io.github.khezyapp.jooqspec.service;

import io.github.khezyapp.jooqspec.JooqPaginationQuery;
import io.github.khezyapp.jooqspec.data.Author;
import io.github.khezyapp.jooqspec.data.Book;
import io.github.khezyapp.jooqspec.data.Page;
import io.github.khezyapp.jooqspec.data.PageMetadata;
import lombok.RequiredArgsConstructor;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookService {
    // Tables
    private static Table<org.jooq.Record> BOOK = DSL.table("book");
    private static Table<org.jooq.Record> AUTHOR = DSL.table("author");

    // Fields (Note: use table.field() to ensure they are qualified, e.g., "book.id")
    private static Field<Long> BOOK_ID = DSL.field(DSL.name("book", "id"), Long.class);
    private static Field<String> BOOK_TITLE = DSL.field(DSL.name("book", "title"), String.class);
    private static Field<Double> BOOK_PRICE = DSL.field(DSL.name("book", "price"), Double.class);
    private static Field<Long> BOOK_AUTHOR_ID = DSL.field(DSL.name("book", "author_id"), Long.class);

    private static Field<Long> AUTHOR_ID = DSL.field(DSL.name("author", "id"), Long.class);
    private static Field<String> AUTHOR_NAME = DSL.field(DSL.name("author", "name"), String.class);

    private final DSLContext dsl;

    public Page<Book> getBook(final JooqPaginationQuery query) {
        final var spec = query.specification();

        var totalQuery = dsl.selectCount()
                .from(BOOK)
                .join(AUTHOR).on(BOOK_AUTHOR_ID.eq(AUTHOR_ID))
                .where(spec.where())
                .getQuery();
        if (!spec.groupBy().isEmpty()) {
            totalQuery.addGroupBy(spec.groupBy());
            totalQuery.addHaving(spec.having());
        }
        final var total = totalQuery.fetchOne(0, Integer.class);
        final var totalPage = (int) Math.ceil(Objects.requireNonNullElse(total, 0) / (double) query.getPageSize());
        final var metadata = new PageMetadata(query.getPageNumber(), query.getPageSize(), total, totalPage);

        final var authorId = BOOK_AUTHOR_ID.as("authorId");
        final var authorName = AUTHOR_NAME.as("authorName");
        final var select = dsl.select(
                        BOOK_ID,
                        BOOK_TITLE,
                        BOOK_PRICE,
                        authorId,
                        authorName
                )
                .from(BOOK)
                .leftJoin(AUTHOR).on(BOOK_AUTHOR_ID.eq(AUTHOR_ID))
                .where(spec.where())
                .getQuery();
        if (!spec.groupBy().isEmpty()) {
            select.addGroupBy(spec.groupBy());
            select.addHaving(spec.having());
        }
        select.addLimit(query.getPageSize());
        select.addOffset(query.getOffset());
        final var resultSet = select.fetch();

        // One-to-One mapping
        final var books = new ArrayList<Book>();
        for (final var record : resultSet) {
            final var bookId = record.get(BOOK_ID);
            final var book = new Book();
            book.setId(bookId);
            book.setTitle(record.get(BOOK_TITLE));
            book.setPrice(record.get(BOOK_PRICE));

            if (Objects.nonNull(record.get(authorId))) {
                final var author = new Author();
                author.setId(record.get(authorId));
                author.setName(record.get(authorName));
                book.setAuthor(author);
            }

            books.add(book);
        }

        return new Page<>(books, metadata);
    }
}
