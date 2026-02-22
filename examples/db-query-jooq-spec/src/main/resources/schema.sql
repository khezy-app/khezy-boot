CREATE TABLE author (
    id INT NOT NULL PRIMARY KEY,
    name VARCHAR(150)
);

CREATE TABLE book (
      id INT NOT NULL PRIMARY KEY,
      title VARCHAR(100) NOT NULL,
      price DECIMAL(10, 2), -- 10 total digits, 2 after the decimal point
      author_id INT,
      FOREIGN KEY (author_id) REFERENCES author(id)
);

-- Seed Authors
INSERT INTO author (id, name) VALUES (1, 'J.K. Rowling');
INSERT INTO author (id, name) VALUES (2, 'George R.R. Martin');

-- Seed Books
INSERT INTO book (id, title, price, author_id) VALUES (1, 'Harry Potter and the Sorcerer''s Stone', 29.99, 1);
INSERT INTO book (id, title, price, author_id) VALUES (2, 'Harry Potter and the Chamber of Secrets', 24.50, 1);
INSERT INTO book (id, title, price, author_id) VALUES (3, 'A Game of Thrones', 35.00, 2);
INSERT INTO book (id, title, price, author_id) VALUES (4, 'A Clash of Kings', 32.99, 2);