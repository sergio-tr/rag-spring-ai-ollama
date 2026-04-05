-- Idempotent dev seed when DB is empty (local / CI smoke).

INSERT INTO users (id, email, password_hash, name, role, created_at)
SELECT 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid,
       'dev@local.test',
       '{noop}dev',
       'Dev User',
       'USER',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM users LIMIT 1);

INSERT INTO projects (id, owner_id, name, description, created_at, updated_at)
SELECT 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'::uuid,
       u.id,
       'Default project',
       'Seed project for chat and documents',
       NOW(),
       NOW()
FROM (SELECT id FROM users ORDER BY created_at LIMIT 1) u
WHERE NOT EXISTS (SELECT 1 FROM projects LIMIT 1);
