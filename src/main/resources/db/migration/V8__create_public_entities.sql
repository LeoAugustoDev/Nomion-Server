CREATE TABLE public_entities (

    id UUID PRIMARY KEY,

    tenant_id UUID NOT NULL,

    name VARCHAR(150) NOT NULL,

    cnpj VARCHAR(14),

    type VARCHAR(30) NOT NULL,

    municipality VARCHAR(120) NOT NULL,

    state VARCHAR(2) NOT NULL,

    email VARCHAR(150),

    phone VARCHAR(30),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_public_entities_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id),

    CONSTRAINT ck_public_entities_cnpj
        CHECK (cnpj IS NULL OR cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$'),

    CONSTRAINT ck_public_entities_type
        CHECK (type IN (
            'CITY_HALL',
            'CITY_COUNCIL',
            'AUTARCHY',
            'FOUNDATION',
            'PUBLIC_CONSORTIUM',
            'OTHER'
        )),

    CONSTRAINT ck_public_entities_state
        CHECK (state ~ '^[A-Z]{2}$')

);

CREATE UNIQUE INDEX uq_public_entities_tenant_cnpj
    ON public_entities(tenant_id, cnpj)
    WHERE cnpj IS NOT NULL;

CREATE INDEX idx_public_entities_tenant_active_name
    ON public_entities(tenant_id, active, name);

CREATE INDEX idx_public_entities_tenant_name_search
    ON public_entities(tenant_id, LOWER(name) text_pattern_ops);

CREATE INDEX idx_public_entities_tenant_type
    ON public_entities(tenant_id, type);

CREATE INDEX idx_public_entities_tenant_location
    ON public_entities(tenant_id, state, municipality);

CREATE INDEX idx_public_entities_tenant_municipality_search
    ON public_entities(tenant_id, LOWER(municipality) text_pattern_ops);

INSERT INTO permissions (
    id,
    code,
    module,
    action,
    description
) VALUES
    ('10000000-0000-0000-0000-000000000009', 'PUBLIC_ENTITY_CREATE', 'PUBLIC_ENTITY', 'CREATE', 'Cadastrar órgãos públicos'),
    ('10000000-0000-0000-0000-000000000010', 'PUBLIC_ENTITY_READ', 'PUBLIC_ENTITY', 'READ', 'Consultar órgãos públicos'),
    ('10000000-0000-0000-0000-000000000011', 'PUBLIC_ENTITY_UPDATE', 'PUBLIC_ENTITY', 'UPDATE', 'Atualizar órgãos públicos');

INSERT INTO role_permission_assignments (role_id, permission_id)
SELECT
    role.id,
    permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.tenant_id IS NULL
  AND role.code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN', 'CONSULTANT')
  AND permission.code IN (
      'PUBLIC_ENTITY_CREATE',
      'PUBLIC_ENTITY_READ',
      'PUBLIC_ENTITY_UPDATE'
  );

INSERT INTO role_permission_assignments (role_id, permission_id)
SELECT
    role.id,
    permission.id
FROM roles role
INNER JOIN permissions permission
    ON permission.code = 'PUBLIC_ENTITY_READ'
WHERE role.tenant_id IS NULL
  AND role.code IN ('ANALYST', 'VIEWER');
