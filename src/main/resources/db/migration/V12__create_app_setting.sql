-- Small owner preferences that both the single-image and the folder flow must agree on.
CREATE TABLE app_setting (
    name VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
