package io.github.khezyapp.examples.data;

import io.github.khezyapp.examples.entity.Book;
import lombok.Data;

import java.util.Objects;

@Data
public class BookData {
    private Long id;
    private String title;
    private Double price;
    private AuthorData author;

    public BookData(final Long id,
                    final String title,
                    final Double price) {
        this.id = id;
        this.title = title;
        this.price = price;
    }

    public static BookData from(final Book book,
                                final boolean includeAuthor) {
        if (Objects.isNull(book)) {
            return null;
        }
        final var bookData = new BookData(book.getId(), book.getTitle(), book.getPrice());
        if (includeAuthor) {
            bookData.setAuthor(AuthorData.from(book.getAuthor(), false));
        }
        return bookData;
    }
}
