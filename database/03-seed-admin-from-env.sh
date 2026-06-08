#!/bin/sh
set -eu

ADMIN_EMAIL="${CASESIM_SEED_ADMIN_EMAIL:-}"
ADMIN_NAME="${CASESIM_SEED_ADMIN_NAME:-Administrador CaseSim}"
ADMIN_PASSWORD_HASH="${CASESIM_SEED_ADMIN_PASSWORD_HASH:-}"

fail() {
  printf '%s\n' "ERROR: $1" >&2
  exit 1
}

contains_placeholder() {
  value_lower=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  case "$value_lower" in
    *changeme*) return 0 ;;
    *change_me*) return 0 ;;
    *placeholder*) return 0 ;;
    *) return 1 ;;
  esac
}

[ -n "$ADMIN_EMAIL" ] || fail "CASESIM_SEED_ADMIN_EMAIL es obligatorio para crear el usuario ADMIN inicial."
[ -n "$ADMIN_PASSWORD_HASH" ] || fail "CASESIM_SEED_ADMIN_PASSWORD_HASH es obligatorio para crear el usuario ADMIN inicial."
[ -n "$ADMIN_NAME" ] || fail "CASESIM_SEED_ADMIN_NAME no puede estar vacío."

contains_placeholder "$ADMIN_EMAIL" && fail "CASESIM_SEED_ADMIN_EMAIL contiene un placeholder; use un email privado real en .env."
contains_placeholder "$ADMIN_NAME" && fail "CASESIM_SEED_ADMIN_NAME contiene un placeholder; use un nombre válido o elimine la variable para usar el default."
contains_placeholder "$ADMIN_PASSWORD_HASH" && fail "CASESIM_SEED_ADMIN_PASSWORD_HASH contiene un placeholder; genere un hash BCrypt privado."

case "$ADMIN_EMAIL" in
  *@*.*) ;;
  *) fail "CASESIM_SEED_ADMIN_EMAIL no tiene formato básico de email." ;;
esac

case "$ADMIN_PASSWORD_HASH" in
  \$2a\$[0-9][0-9]\$????????????????????????????????????????????????????? | \
  \$2b\$[0-9][0-9]\$????????????????????????????????????????????????????? | \
  \$2y\$[0-9][0-9]\$????????????????????????????????????????????????????? ) ;;
  *) fail 'CASESIM_SEED_ADMIN_PASSWORD_HASH debe ser un hash BCrypt válido ($2a$, $2b$ o $2y$).' ;;
esac

printf '%s\n' "Inicializando usuario ADMIN desde variables privadas (email/hash no impresos)."

psql -v ON_ERROR_STOP=1 \
  -v admin_email="$ADMIN_EMAIL" \
  -v admin_name="$ADMIN_NAME" \
  -v admin_password_hash="$ADMIN_PASSWORD_HASH" \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" <<'SQL'
BEGIN;

INSERT INTO rol (nombre)
VALUES ('ADMIN')
ON CONFLICT (nombre) DO NOTHING;

WITH admin_seed AS (
    SELECT
        lower(:'admin_email') AS email,
        :'admin_name' AS nombre,
        :'admin_password_hash' AS password_hash
), updated_admin AS (
    UPDATE usuario u
    SET nombre = admin_seed.nombre,
        email = admin_seed.email,
        password_hash = admin_seed.password_hash,
        activo = TRUE
    FROM admin_seed
    WHERE lower(u.email) = admin_seed.email
    RETURNING u.id
), inserted_admin AS (
    INSERT INTO usuario (nombre, email, password_hash, activo)
    SELECT admin_seed.nombre, admin_seed.email, admin_seed.password_hash, TRUE
    FROM admin_seed
    WHERE NOT EXISTS (SELECT 1 FROM updated_admin)
    RETURNING id
), admin_user AS (
    SELECT id FROM updated_admin
    UNION ALL
    SELECT id FROM inserted_admin
), admin_role AS (
    SELECT id
    FROM rol
    WHERE nombre = 'ADMIN'
)
INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT admin_user.id, admin_role.id
FROM admin_user
CROSS JOIN admin_role
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

COMMIT;
SQL

printf '%s\n' "Usuario ADMIN inicial creado/actualizado correctamente (hash no impreso)."
