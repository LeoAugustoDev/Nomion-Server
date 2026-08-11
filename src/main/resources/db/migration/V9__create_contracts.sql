ALTER TABLE public_entities
    ADD CONSTRAINT uq_public_entities_id_tenant
        UNIQUE (id, tenant_id);

ALTER TABLE tenant_memberships
    ADD CONSTRAINT uq_tenant_memberships_id_tenant
        UNIQUE (id, tenant_id);

CREATE TABLE contracts (

    id UUID PRIMARY KEY,

    tenant_id UUID NOT NULL,

    public_entity_id UUID NOT NULL,

    manager_membership_id UUID,

    number VARCHAR(50) NOT NULL,

    object VARCHAR(2000) NOT NULL,

    start_date DATE NOT NULL,

    end_date DATE NOT NULL,

    amount NUMERIC(19, 2),

    status VARCHAR(20) NOT NULL,

    created_by_user_id UUID NOT NULL,

    updated_by_user_id UUID NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_contracts_tenant_number
        UNIQUE (tenant_id, number),

    CONSTRAINT fk_contracts_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id),

    CONSTRAINT fk_contracts_public_entity_tenant
        FOREIGN KEY (public_entity_id, tenant_id)
            REFERENCES public_entities(id, tenant_id),

    CONSTRAINT fk_contracts_manager_tenant
        FOREIGN KEY (manager_membership_id, tenant_id)
            REFERENCES tenant_memberships(id, tenant_id),

    CONSTRAINT fk_contracts_created_by_tenant
        FOREIGN KEY (created_by_user_id, tenant_id)
            REFERENCES tenant_memberships(user_id, tenant_id),

    CONSTRAINT fk_contracts_updated_by_tenant
        FOREIGN KEY (updated_by_user_id, tenant_id)
            REFERENCES tenant_memberships(user_id, tenant_id),

    CONSTRAINT ck_contracts_dates
        CHECK (start_date <= end_date),

    CONSTRAINT ck_contracts_amount
        CHECK (amount IS NULL OR amount >= 0),

    CONSTRAINT ck_contracts_active_manager
        CHECK (status <> 'ACTIVE' OR manager_membership_id IS NOT NULL),

    CONSTRAINT ck_contracts_status
        CHECK (status IN (
            'DRAFT',
            'ACTIVE',
            'SUSPENDED',
            'EXPIRED',
            'TERMINATED'
        ))

);

CREATE INDEX idx_contracts_tenant_status_end_date
    ON contracts(tenant_id, status, end_date);

CREATE INDEX idx_contracts_tenant_public_entity
    ON contracts(tenant_id, public_entity_id);

CREATE INDEX idx_contracts_tenant_manager
    ON contracts(tenant_id, manager_membership_id)
    WHERE manager_membership_id IS NOT NULL;

CREATE INDEX idx_contracts_tenant_number_search
    ON contracts(tenant_id, LOWER(number) text_pattern_ops);

INSERT INTO permissions (
    id,
    code,
    module,
    action,
    description
) VALUES
    ('10000000-0000-0000-0000-000000000012', 'CONTRACT_CREATE', 'CONTRACT', 'CREATE', 'Cadastrar contratos'),
    ('10000000-0000-0000-0000-000000000013', 'CONTRACT_READ', 'CONTRACT', 'READ', 'Consultar contratos'),
    ('10000000-0000-0000-0000-000000000014', 'CONTRACT_UPDATE', 'CONTRACT', 'UPDATE', 'Atualizar contratos');

INSERT INTO role_permission_assignments (role_id, permission_id)
SELECT
    role.id,
    permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.tenant_id IS NULL
  AND role.code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN', 'CONSULTANT')
  AND permission.code IN (
      'CONTRACT_CREATE',
      'CONTRACT_READ',
      'CONTRACT_UPDATE'
  );

INSERT INTO role_permission_assignments (role_id, permission_id)
SELECT
    role.id,
    permission.id
FROM roles role
INNER JOIN permissions permission
    ON permission.code = 'CONTRACT_READ'
WHERE role.tenant_id IS NULL
  AND role.code IN ('ANALYST', 'VIEWER');
