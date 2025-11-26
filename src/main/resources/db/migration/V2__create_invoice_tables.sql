-- Create invoice table
CREATE TABLE invoice (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
    organization_id BIGINT NOT NULL,
    customer_id BIGINT,
    subscription_id BIGINT,
    rate_plan_id BIGINT,
    invoice_date DATE NOT NULL,
    due_date DATE,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    subtotal DECIMAL(19,2),
    discount_amount DECIMAL(19,2),
    total_amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    quickbooks_invoice_id VARCHAR(100),
    quickbooks_sync_status VARCHAR(20),
    quickbooks_sync_date TIMESTAMP,
    quickbooks_sync_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create invoice_line_item table
CREATE TABLE invoice_line_item (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    calculation_detail TEXT,
    quantity DECIMAL(19,2),
    unit_price DECIMAL(19,2),
    amount DECIMAL(19,2) NOT NULL,
    line_order INT,
    CONSTRAINT fk_invoice_line_item_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoice(id)
        ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX idx_invoice_organization_id ON invoice(organization_id);
CREATE INDEX idx_invoice_status ON invoice(status);
CREATE INDEX idx_invoice_qb_sync_status ON invoice(quickbooks_sync_status);
CREATE INDEX idx_invoice_period ON invoice(period_start, period_end);
CREATE INDEX idx_invoice_subscription ON invoice(subscription_id);
CREATE INDEX idx_invoice_line_item_invoice_id ON invoice_line_item(invoice_id);
