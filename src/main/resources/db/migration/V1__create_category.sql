-- RF-11: content categories. Status values are fixed by the integration standards.
CREATE TABLE category (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO'
);

-- Name is unique regardless of case, including inactive categories.
CREATE UNIQUE INDEX ux_category_name ON category (lower(name));
