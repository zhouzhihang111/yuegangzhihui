CREATE TABLE product_specification (
    sku_id BIGINT NOT NULL,
    spec_key VARCHAR(64) NOT NULL,
    spec_value VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (sku_id, spec_key),
    CONSTRAINT fk_product_specification_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE CASCADE
);
