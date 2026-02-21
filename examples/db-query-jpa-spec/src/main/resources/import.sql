-- Seed Authors
INSERT INTO author (id, name) VALUES (1, 'J.K. Rowling');
INSERT INTO author (id, name) VALUES (2, 'George R.R. Martin');

-- Seed Books
INSERT INTO book (id, title, price, author_id) VALUES (1, 'Harry Potter and the Sorcerer''s Stone', 29.99, 1);
INSERT INTO book (id, title, price, author_id) VALUES (2, 'Harry Potter and the Chamber of Secrets', 24.50, 1);
INSERT INTO book (id, title, price, author_id) VALUES (3, 'A Game of Thrones', 35.00, 2);
INSERT INTO book (id, title, price, author_id) VALUES (4, 'A Clash of Kings', 32.99, 2);