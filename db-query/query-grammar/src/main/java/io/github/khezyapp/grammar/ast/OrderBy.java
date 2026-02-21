package io.github.khezyapp.grammar.ast;

public record OrderBy(
        String path,
        Direction direction
) {

    public enum Direction {
        ASC,
        DESC;

        public static Direction of(final String value) {
            for (final var direction : Direction.values()) {
                if (direction.name().equalsIgnoreCase(value)) {
                    return direction;
                }
            }
            return null;
        }
    }
}
