-- Seed data for row-level security demo
-- acme tenant invoices
INSERT INTO invoices (invoice_number, tenant_id, customer_name, amount, issue_date)
VALUES ('INV-001', 'acme', 'Alice Corp', 1000.00, '2026-01-15');
INSERT INTO invoices (invoice_number, tenant_id, customer_name, amount, issue_date)
VALUES ('INV-002', 'acme', 'Bob Industries', 2500.00, '2026-02-20');
INSERT INTO invoices (invoice_number, tenant_id, customer_name, amount, issue_date)
VALUES ('INV-003', 'acme', 'Charlie LLC', 750.00, '2026-03-10');

-- globex tenant invoices (should NOT be visible to acme users)
INSERT INTO invoices (invoice_number, tenant_id, customer_name, amount, issue_date)
VALUES ('INV-004', 'globex', 'Globex Corp', 5000.00, '2026-01-01');
INSERT INTO invoices (invoice_number, tenant_id, customer_name, amount, issue_date)
VALUES ('INV-005', 'globex', 'Delta Inc', 3200.00, '2026-02-15');

-- Customers
INSERT INTO customers (name, tenant_id) VALUES ('Alice Corp', 'acme');
INSERT INTO customers (name, tenant_id) VALUES ('Bob Industries', 'acme');
INSERT INTO customers (name, tenant_id) VALUES ('Charlie LLC', 'acme');
INSERT INTO customers (name, tenant_id) VALUES ('Globex Corp', 'globex');
INSERT INTO customers (name, tenant_id) VALUES ('Delta Inc', 'globex');
