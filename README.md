# CaseSim

CaseSim es una plataforma de simulación clínica con IA orientada a uso institucional. Permite que profesores creen casos clínicos, estudiantes entrevisten pacientes simulados por IA y administradores controlen proveedores LLM, costos, métricas y seguridad.
La IA no evalúa al estudiante; actúa únicamente como paciente simulado.

## Comenzando

Estas instrucciones permiten levantar el proyecto localmente para desarrollo y pruebas.

Este repositorio contiene:
- `backend/`: API REST en Spring Boot (Java 21).
- `frontend/`: aplicación Angular.
- `docker-compose.yml`: PostgreSQL local.

## Pre-requisitos

- Java 21
- Node.js + npm
- Docker + Docker Compose
- PostgreSQL vía `docker-compose`
- API key LLM opcional para probar paciente IA real

## Instalación

- clonar repo
```bash
git clone https://github.com/LeoCaste/CaseSim.git
cd CaseSim
```

- docker-compose up -d
```bash
docker-compose up -d
```

- backend: cd backend && ./mvnw spring-boot:run
```bash
cd backend && ./mvnw spring-boot:run
```

- frontend: cd frontend && npm install && npm start
```bash
cd frontend && npm install && npm start
```

- URLs:
  - frontend: `http://localhost:4200`
  - backend: `http://localhost:8080`
  - postgres: `localhost:5433`

## Flujo principal de uso (8 pasos)

1. Admin crea usuarios.
2. Admin configura provider/model/API key LLM.
3. Profesor crea caso clínico.
4. Profesor asigna simulación.
5. Estudiante conversa con paciente IA.
6. Estudiante entrega diagnóstico final.
7. Profesor revisa sesión.
8. Admin revisa métricas LLM.

## Ejecutando las pruebas

- backend: `./mvnw clean test` (desde `backend/`)
```bash
cd backend && ./mvnw clean test
```

- frontend: `npm run build` (desde `frontend/`)
```bash
cd frontend && npm run build
```

- frontend tests si existen: `npm test`
```bash
cd frontend && npm test
```

## Pruebas E2E manuales

Checklist:
- [ ] Login Admin y creación de usuario Profesor + Estudiante.
- [ ] Configuración LLM con provider válido (`openai`, `groq`, `gemini`, `openrouter`) y API key de prueba.
- [ ] Creación de caso clínico por Profesor.
- [ ] Asignación del caso al Estudiante.
- [ ] Entrevista con paciente simulado IA.
- [ ] Envío de diagnóstico final por Estudiante.
- [ ] Revisión docente de sesión.
- [ ] Revisión de métricas, costos y fallback por Admin.
- [ ] Validación de expiración AFK (15 min) en sesión JWT.
- [ ] Validación de degraded frontend ante caída backend/LLM.
- [ ] Validación de reset admin manual cuando `CASESIM_PASSWORD_RESET_MODE=MANUAL`.

## Variables de entorno

| Variable | Propósito | Default dev / nota |
|---|---|---|
| `APP_ENV` | Entorno operativo (`dev`, `staging`, `prod`) | `dev` |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring activo | sin default explícito |
| `JWT_SECRET` | Secreto de firma JWT | default solo para desarrollo local |
| `JWT_EXPIRATION_MS` | Expiración de token JWT en milisegundos | `900000` (15 min) |
| `CASESIM_LLM_CIPHER_KEY` | Clave para cifrado de API keys LLM | default solo para desarrollo local |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes permitidos para CORS | `http://localhost:4200` |
| `SPRING_DATASOURCE_URL` | URL de conexión PostgreSQL | `jdbc:postgresql://localhost:5433/casesim_db` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `casesim_user` |
| `SPRING_DATASOURCE_PASSWORD` | Password de base de datos | `casesim_pass` |
| `CASESIM_PASSWORD_RESET_MODE` | Modo de reseteo de contraseña | `DISABLED` |
| `CASESIM_OPERATIONS_TOKEN` | Token operativo para acciones sensibles | vacío por defecto |
| `APP_FRONTEND_BASE_URL` | URL base pública del frontend | `http://localhost:4200` |

Aclaraciones obligatorias:
- En `dev` se permiten defaults para facilitar ejecución local.
- En `staging/prod` hay validaciones de hardening con fail-fast.
- `APP_ENV` y `SPRING_PROFILES_ACTIVE` deben coincidir en `staging/prod`.
- No versionar secretos ni API keys reales.

## Despliegue

El despliegue institucional requiere configurar variables reales por entorno, base de datos, CORS, secretos JWT, clave de cifrado LLM, `APP_FRONTEND_BASE_URL` y proveedor LLM operativo.

## Construido con

- Spring Boot
- Java 21
- Angular
- TypeScript
- PostgreSQL
- Docker Compose
- JWT
- OpenAI / Groq / Gemini / OpenRouter

## Seguridad

- Autenticación/autorización JWT robustecida.
- Expiración AFK de 15 minutos (`JWT_EXPIRATION_MS=900000`).
- API keys LLM almacenadas cifradas.
- Hardening y fail-fast en staging/prod.
- Soporte de modo degradado en frontend cuando backend/LLM no está disponible.
- Reset administrativo manual controlado por modo/token operativo.

## Roadmap corto

- UX demo.
- Observabilidad fallback.
- Contrato clínico v1.
- Refactor incremental LLM.
- Deploy institucional.

## Autoría

CaseSim fue desarrollado por Leo Castellon como proyecto académico/personal orientado a simulación clínica con IA, con orientación general del problema entregada en contexto universitario.

**Leo Castellon** — Desarrollo, arquitectura e implementación inicial.
