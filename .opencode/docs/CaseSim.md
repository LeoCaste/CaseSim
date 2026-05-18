# CaseSim — Fuente de Verdad Técnica (Estado Actual)

> Última actualización: 2026-05-18  
> Alcance: estado real del código en este repositorio.  
> Este documento **reemplaza** como referencia principal a docs históricas desalineadas.

---

## 1) Qué es CaseSim

CaseSim es una plataforma institucional de simulación clínica con IA para flujo académico controlado:

1. Admin gestiona usuarios y configuración LLM.
2. Profesor crea casos y asigna simulaciones.
3. Estudiante entrevista a paciente IA y envía diagnóstico final.
4. Profesor revisa sesiones.
5. Admin monitorea métricas de uso LLM.

Regla crítica: la IA **no evalúa** ni califica; solo actúa como paciente simulado.

---

## 2) Estado funcional actual

### Flujo E2E disponible

- Auth (`/auth/pre-check`, `/auth/login`, `/auth/me`, `/auth/logout`)
- Gestión de usuarios ADMIN
- Configuración y métricas LLM ADMIN
- CRUD de casos clínicos
- Asignación de simulaciones
- Entrevista clínica (sesión + mensajes)
- Envío de diagnóstico final
- Revisión docente de sesiones

### Providers LLM implementados

- OpenAI
- Groq
- Gemini

Arquitectura preparada con fallback y trazabilidad de uso.

---

## 3) Arquitectura

## Frontend

- Angular 21
- Guards por rol
- Interceptor JWT
- Servicios por dominio
- `environment.useMocks=false` (dev/prod en este repo)

## Backend

- Spring Boot 4.0.6
- Java 21
- API REST `/api/v1`
- Spring Security + JWT stateless
- JPA/Hibernate
- PostgreSQL

## Base de datos

- PostgreSQL vía `docker-compose.yml` (puerto 5433)
- Script de inicialización en `database/init.sql`

---

## 4) Backend (módulos y clases principales)

## Controladores

- `AuthController` → `/api/v1/auth/*`
- `AdminUsersController` → `/api/v1/admin/users/*`
- `LlmAdminController` → `/api/v1/admin/llm/*`
- `ClinicalCaseController` → `/api/v1/clinical-cases/*`
- `SimulationAssignmentController` → `/api/v1/simulations`, `/api/v1/student/activities`
- `SessionController` → `/api/v1/sessions/*`
- `ProfessorSessionsController` / `ProfessorSessionController` → revisión docente
- `ProfessorStudentsController` → listado estudiantes
- `HealthController` → `/api/v1/health`

## Servicios

- Auth: `AuthService`, `JwtService`, `UserPrincipalService`
- Admin users: `AdminUsersService`
- Casos clínicos: `ClinicalCaseService`
- Simulaciones: `SimulationAssignmentService`
- Sesiones: `SessionService`, `PatientResponseService`, `MockPatientResponseService`
- Profesor: `ProfessorSessionsService`, `ProfessorSessionService`, `ProfessorStudentsService`
- LLM: `LlmAdminService`, `LlmPatientResponseService`, `PromptBuilderService`, `LlmUsageService`

## Seguridad

- Permite público: `POST /auth/login`, `POST /auth/pre-check`, `GET /health`
- Resto protegido por rol (`ADMIN`, `PROFESOR`, `ESTUDIANTE`)
- 401/403 con body JSON consistente (`status`, `error`, `message`)

---

## 5) Frontend (rutas y features principales)

## Rutas clave

- Login: `/login`
- Estudiante: `/student/dashboard`, `/student/waiting-room`, `/interview`, `/session-completed`, `/student/session-detail`
- Profesor: `/professor/dashboard`, `/professor/sessions`, `/professor/sessions/:id`, `/clinical-cases/*`
- Admin: `/admin/users`, `/admin/llm-config`, `/admin/llm-usage`

## Servicios core

- `auth.service.ts`
- `admin-user.service.ts`
- `admin-llm.service.ts`
- `clinical-case.service.ts`
- `simulation-assignment.service.ts`
- `student-session.service.ts`
- `interview-session.service.ts`
- `professor-dashboard.service.ts`

Patrón UX requerido y aplicado: loading + error visible + empty state (evitar pantallas colgadas).

---

## 6) Endpoints API (resumen)

## Auth

- `POST /api/v1/auth/pre-check`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`

## Admin Users

- `GET /api/v1/admin/users`
- `GET /api/v1/admin/users/roles`
- `GET /api/v1/admin/users/{id}`
- `POST /api/v1/admin/users`
- `PUT /api/v1/admin/users/{id}`
- `PATCH /api/v1/admin/users/{id}/status`
- `DELETE /api/v1/admin/users/{id}`

## Admin LLM

- `GET /api/v1/admin/llm/config`
- `PUT /api/v1/admin/llm/config`
- `DELETE /api/v1/admin/llm/config/api-key`
- `POST /api/v1/admin/llm/test-connection`
- `GET /api/v1/admin/llm/usage`
- `GET /api/v1/admin/llm/summary`

## Casos / Simulaciones / Sesiones

- `GET|POST /api/v1/clinical-cases`
- `GET|PUT|PATCH|DELETE /api/v1/clinical-cases/{id}`
- `POST /api/v1/simulations`
- `GET /api/v1/student/activities`
- `POST /api/v1/sessions`
- `GET /api/v1/sessions/{id}`
- `GET /api/v1/sessions/{id}/messages`
- `POST /api/v1/sessions/{id}/messages`
- `POST /api/v1/sessions/{id}/complete`
- `POST /api/v1/sessions/{id}/final-diagnosis`

---

## 7) Ejecución local

## Backend

```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```

## Frontend

```bash
cd frontend
npm run build
npm start
```

## Base de datos

```bash
docker-compose up -d
```

---

## 8) Validación mínima de estabilidad

- Backend: `./mvnw test`
- Frontend: `npm run build`
- Smoke recomendado:
  - admin → profesor → estudiante → profesor → admin

---

## 9) Riesgos/deuda vigente

- `LlmPatientResponseService` concentra demasiada responsabilidad (deuda conocida).
- Warning de budget frontend no bloqueante en build.
- Evitar cambios que rompan compatibilidad OpenAI/Groq/Gemini.

---

## 10) Política documental desde ahora

1. Este archivo (`CaseSim.md`) es la referencia técnica principal del estado actual.
2. `CASESIM_CONTEXT.md` es complemento de contexto de producto/reglas.
3. Documentos de fase se consideran históricos salvo que indiquen explícitamente “vigente”.
4. Toda doc operativa debe incluir fecha de última validación.

---

## 11) Documentos marcados como DEPRECATED

- `.opencode/docs/CASESIM_OVERVIEW.md`
- `.opencode/docs/QUICK_START.md`
- `.opencode/docs/analisis-casesim-preimplementacion.txt`

Archivo duplicado eliminado:

- `.opencode/docs/Proyecto-simulador.md` (duplicaba `Proyecto-simulador.md` en raíz)
