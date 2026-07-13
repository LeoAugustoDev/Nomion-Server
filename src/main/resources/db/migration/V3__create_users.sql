CREATE TABLE users (

                       id UUID PRIMARY KEY,

                       name VARCHAR(255) NOT NULL,

                       email VARCHAR(255) NOT NULL UNIQUE,

                       password VARCHAR(255) NOT NULL,

                       tenant_id UUID NOT NULL,

                       CONSTRAINT fk_users_tenant
                           FOREIGN KEY (tenant_id)
                               REFERENCES tenants(id)

);