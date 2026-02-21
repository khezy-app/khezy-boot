package io.github.khezyapp.examples.data;

import io.github.khezyapp.examples.entity.Author;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class AuthorData {
    private Long id;
    private String name;
    private List<BookData> books;

    public AuthorData(final Long id,
                      final String name) {
        this.id = id;
        this.name = name;
    }

    public static AuthorData from(final Author author,
                                  final boolean includeBook) {
        if (Objects.isNull(author)) {
            return null;
        }
        final var authorData = new AuthorData(author.getId(), author.getName());

        if (includeBook) {
            authorData.setBooks(author.getBooks().stream().map(b -> BookData.from(b, false)).toList());
        }

        return authorData;
    }
}
