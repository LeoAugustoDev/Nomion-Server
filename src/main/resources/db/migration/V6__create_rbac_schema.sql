CREATE TABLE roles (

    id UUID PRIMARY KEY,

    tenant_id UUID,

    code VARCHAR(100) NOT NULL,

    name VARCHAR(120) NOT NULL,

    description VARCHAR(255),

    scope VARCHAR(20) NOT NULL,

    system_role BOOLEAN NOT NULL DEFAULT FALSE,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_roles_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id),

    CONSTRAINT ck_roles_scope
        CHECK (scope IN ('PLATFORM', 'TENANT')),

    CONSTRAINT ck_platform_role_without_tenant
        CHECK (scope <> 'PLATFORM' OR tenant_id IS NULL),

    CONSTRAINT ck_platform_admin_scope
        CHECK (
            code <> 'PLATFORM_ADMIN'
            OR (scope = 'PLATFORM' AND tenant_id IS NULL)
        )

);

CREATE UNIQUE INDEX uq_roles_global_code
    ON roles(code)
    WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX uq_roles_tenant_code
    ON roles(tenant_id, code)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX idx_roles_tenant_active
    ON roles(tenant_id, active);

CREATE TABLE permissions (

    id UUID PRIMARY KEY,

    code VARCHAR(120) NOT NULL UNIQUE,

    module VARCHAR(80) NOT NULL,

    action VARCHAR(50) NOT NULL,

    description VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP

);

CREATE TABLE role_permission_assignments (

    role_id UUID NOT NULL,

    permission_id UUID NOT NULL,

    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id)
            REFERENCES roles(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id)
            REFERENCES permissions(id)
            ON DELETE CASCADE

);

CREATE INDEX idx_role_permissions_permission_id
    ON role_permission_assignments(permission_id);

CREATE TABLE membership_role_assignments (

    membership_id UUID NOT NULL,

    role_id UUID NOT NULL,

    assigned_by_user_id UUID,

    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (membership_id, role_id),

    CONSTRAINT fk_membership_roles_membership
        FOREIGN KEY (membership_id)
            REFERENCES tenant_memberships(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_membership_roles_role
        FOREIGN KEY (role_id)
            REFERENCES roles(id),

    CONSTRAINT fk_membership_roles_assigned_by
        FOREIGN KEY (assigned_by_user_id)
            REFERENCES users(id)
            ON DELETE SET NULL

);

CREATE INDEX idx_membership_roles_role_id
    ON membership_role_assignments(role_id);

CREATE TABLE user_platform_role_assignments (

    user_id UUID NOT NULL,

    role_id UUID NOT NULL,

    assigned_by_user_id UUID,

    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_platform_roles_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_user_platform_roles_role
        FOREIGN KEY (role_id)
            REFERENCES roles(id),

    CONSTRAINT fk_user_platform_roles_assigned_by
        FOREIGN KEY (assigned_by_user_id)
            REFERENCES users(id)
            ON DELETE SET NULL

);

CREATE INDEX idx_user_platform_roles_role_id
    ON user_platform_role_assignments(role_id);

INSERT INTO roles (
    id,
    code,
    name,
    description,
    scope,
    system_role
) VALUES
    ('00000000-0000-0000-0000-000000000001', 'PLATFORM_ADMIN', 'Administrador da Plataforma', 'Administração global da plataforma', 'PLATFORM', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'TENANT_ADMIN', 'Administrador do Tenant', 'Administração completa do tenant', 'TENANT', TRUE),
    ('00000000-0000-0000-0000-000000000003', 'CONSULTANT', 'Consultor', 'Operação dos módulos de consultoria', 'TENANT', TRUE),
    ('00000000-0000-0000-0000-000000000004', 'ANALYST', 'Analista', 'Análise dos módulos de negócio', 'TENANT', TRUE),
    ('00000000-0000-0000-0000-000000000005', 'VIEWER', 'Visualizador', 'Acesso somente leitura', 'TENANT', TRUE);

INSERT INTO permissions (
    id,
    code,
    module,
    action,
    description
) VALUES
    ('10000000-0000-0000-0000-000000000001', 'TENANT_CREATE', 'TENANT', 'CREATE', 'Criar tenants'),
    ('10000000-0000-0000-0000-000000000002', 'TENANT_READ', 'TENANT', 'READ', 'Consultar tenant ativo'),
    ('10000000-0000-0000-0000-000000000003', 'USER_CREATE', 'USER', 'CREATE', 'Criar usuários'),
    ('10000000-0000-0000-0000-000000000004', 'USER_READ', 'USER', 'READ', 'Consultar usuários'),
    ('10000000-0000-0000-0000-000000000005', 'MEMBERSHIP_CREATE', 'TENANT', 'CREATE_MEMBERSHIP', 'Adicionar usuário a tenant'),
    ('10000000-0000-0000-0000-000000000006', 'MEMBERSHIP_READ', 'TENANT', 'READ_MEMBERSHIP', 'Consultar memberships'),
    ('10000000-0000-0000-0000-000000000007', 'MEMBERSHIP_UPDATE', 'TENANT', 'UPDATE_MEMBERSHIP', 'Atualizar memberships'),
    ('10000000-0000-0000-0000-000000000008', 'ROLE_ASSIGN', 'SECURITY', 'ASSIGN_ROLE', 'Atribuir perfis');

INSERT INTO role_permission_assignments (role_id, permission_id)
SELECT
    '00000000-0000-0000-0000-000000000001',
    id
FROM permissions;

INSERT INTO role_permission_assignments (role_id, permission_id)
SELECT
    '00000000-0000-0000-0000-000000000002',
    id
FROM permissions
WHERE code <> 'TENANT_CREATE';

INSERT INTO role_permission_assignments (role_id, permission_id) VALUES
    ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002');
