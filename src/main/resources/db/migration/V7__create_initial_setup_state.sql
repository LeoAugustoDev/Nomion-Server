CREATE TABLE initial_setup_state (

    id SMALLINT PRIMARY KEY,

    initialized_at TIMESTAMP WITH TIME ZONE,

    initialized_by_user_id UUID,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_initial_setup_singleton
        CHECK (id = 1),

    CONSTRAINT ck_initial_setup_initialization
        CHECK (
            (initialized_at IS NULL AND initialized_by_user_id IS NULL)
            OR (initialized_at IS NOT NULL AND initialized_by_user_id IS NOT NULL)
        ),

    CONSTRAINT fk_initial_setup_initialized_by
        FOREIGN KEY (initialized_by_user_id)
            REFERENCES users(id)

);

WITH existing_platform_admin AS (
    SELECT
        assignment.user_id,
        assignment.assigned_at
    FROM user_platform_role_assignments assignment
    INNER JOIN roles role
        ON role.id = assignment.role_id
    WHERE role.code = 'PLATFORM_ADMIN'
      AND role.scope = 'PLATFORM'
      AND role.active = TRUE
    ORDER BY assignment.assigned_at
    LIMIT 1
)
INSERT INTO initial_setup_state (
    id,
    initialized_at,
    initialized_by_user_id
)
SELECT
    1,
    existing_platform_admin.assigned_at,
    existing_platform_admin.user_id
FROM existing_platform_admin
UNION ALL
SELECT
    1,
    NULL,
    NULL
WHERE NOT EXISTS (SELECT 1 FROM existing_platform_admin);
