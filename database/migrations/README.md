# CaseSim DB migrations (manual incremental)

Este proyecto actualmente inicializa esquema con `database/init.sql` solo al crear un volumen nuevo de PostgreSQL.
Para bases existentes, aplica scripts incrementales manualmente.

## Fase B - fix arranque (`password_reset_token`)

Ejecuta desde la raíz del repo:

```bash
docker exec -i casesim_postgres psql -U casesim_user -d casesim_db < database/migrations/2026-05-18_add_password_reset_and_setup_state.sql
```

Verificación rápida:

```bash
docker exec casesim_postgres psql -U casesim_user -d casesim_db -tAc "SELECT to_regclass('public.password_reset_token'), to_regclass('public.platform_setup_state');"
```

Debe devolver ambos nombres de tabla.
