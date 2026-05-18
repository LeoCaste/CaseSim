# Checklist Fase A — Staging local (CaseSim)

## Objetivo

Verificar que el hardening de Fase A está activo y observable sin romper el flujo E2E.

---

## 1) Qué cambió y cómo verlo

### A. Guard de seguridad para staging (fail-fast)

**Cambio:** si `APP_ENV=staging`, la app no arranca si:
- `JWT_SECRET` inseguro o corto
- `CASESIM_LLM_CIPHER_KEY` inseguro o corto
- `APP_CORS_ALLOWED_ORIGINS` vacío
- DB usa credenciales/URL dev por defecto

**Cómo verlo:** al arrancar backend en staging con datos inseguros, falla en startup con `IllegalStateException`.

---

### B. CORS parametrizable

**Cambio:** ya no está fijo a localhost en código; usa `APP_CORS_ALLOWED_ORIGINS`.

**Cómo verlo:** cambiar variable y reiniciar backend.

---

### C. Llave de cifrado LLM oficializada por variable

**Cambio:** cifrado API key LLM usa `CASESIM_LLM_CIPHER_KEY`.

**Cómo verlo:** si clave no cumple en staging, backend no inicia.

---

## 2) Archivo de ejemplo

Plantilla disponible en raíz:

- `.env.staging.example`

Copiar como base:

```bash
cp .env.staging.example .env.staging
```

---

## 3) Verificación técnica paso a paso

## Paso 1 — Prueba negativa (debe fallar)

Arrancar con secretos inseguros:

```bash
APP_ENV=staging \
JWT_SECRET=dev-secret-change-me \
CASESIM_LLM_CIPHER_KEY=casesim-default-llm-key \
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/casesim_staging_db \
SPRING_DATASOURCE_USERNAME=casesim_staging_user \
SPRING_DATASOURCE_PASSWORD=StagingPass_ChangeMe_2026! \
./mvnw spring-boot:run
```

**Esperado:** startup aborta con error por `JWT_SECRET` inseguro.

---

## Paso 2 — Prueba positiva (debe iniciar)

```bash
APP_ENV=staging \
JWT_SECRET='staging-jwt-secret-2026-ultra-safe-0123456789' \
CASESIM_LLM_CIPHER_KEY='staging-llm-cipher-key-2026-ultra-safe-9876543210' \
APP_CORS_ALLOWED_ORIGINS='http://localhost:4200' \
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/casesim_staging_db' \
SPRING_DATASOURCE_USERNAME='casesim_staging_user' \
SPRING_DATASOURCE_PASSWORD='StagingPass_ChangeMe_2026!' \
JWT_EXPIRATION_MS=900000 \
./mvnw spring-boot:run
```

**Esperado:** `Started BackendApplication` en logs.

---

## Paso 3 — Smoke E2E mínimo

1. Login admin.
2. Ver `/admin/users`.
3. Ver `/admin/llm-config` y `/admin/llm-usage`.
4. Flujo profesor crea caso + asigna actividad.
5. Estudiante ingresa, entrevista, envía diagnóstico final.
6. Profesor revisa sesión.

**Esperado:** flujo completo sin regresiones funcionales.

---

## 4) Hallazgo operativo detectado en staging local

Al crear `casesim_staging_db` con usuario distinto, fue necesario otorgar permisos de tabla/esquema al usuario staging.

### Comando usado

```bash
docker exec casesim_postgres psql -U casesim_user -d casesim_staging_db -c "GRANT USAGE ON SCHEMA public TO casesim_staging_user; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO casesim_staging_user; GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO casesim_staging_user; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO casesim_staging_user; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO casesim_staging_user;"
```

---

## 5) Criterio de aceptación Fase A

- [ ] Backend bloquea arranque staging con secretos inseguros.
- [ ] Backend inicia con variables seguras y DB staging válida.
- [ ] CORS configurable por variable (sin tocar código).
- [ ] E2E funcional sin ruptura.
- [ ] `./mvnw test` en backend sigue en verde.
