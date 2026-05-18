# ⚠️ DEPRECATED — CaseSim Overview (histórico)

> **Estado:** Documento desactualizado.
> **Motivo:** Contiene afirmaciones que ya no reflejan el estado real del sistema (por ejemplo: backend "en etapa inicial", admin "futuro", dependencia de mocks).
> **Fuente de verdad actual:** [`CaseSim.md`](./CaseSim.md)
> **Referencia operativa complementaria:** [`../../CASESIM_CONTEXT.md`](../../CASESIM_CONTEXT.md)

---

# CaseSim · Visión técnica del proyecto

## 1. Qué es CaseSim

CaseSim es una plataforma de simulación clínica asistida por IA.

El sistema permite que un estudiante realice una entrevista con un paciente simulado, registre notas clínicas, envíe un diagnóstico final y deje la sesión disponible para revisión docente.

## 2. Roles

### Estudiante
- Ingresa con correo institucional.
- Ve simulaciones disponibles.
- Realiza entrevistas clínicas.
- Usa un cuadernillo clínico.
- Envía diagnóstico final.
- Consulta historial de sesiones registradas.

### Profesor
- Gestiona casos clínicos.
- Crea o edita casos simulados.
- Asigna casos como simulaciones.
- Revisa sesiones finalizadas.
- Agrega feedback.

### Admin institucional
- Se implementará después del LLM.
- Gestionará configuración de proveedor IA.
- Revisará métricas de uso, tokens, costos y errores.

## 3. Flujo principal

Profesor crea caso clínico.
Profesor asigna simulación a estudiantes.
Estudiante inicia entrevista.
Paciente simulado responde.
Estudiante registra notas e hipótesis.
Estudiante concluye atención con diagnóstico final.
Profesor revisa sesión finalizada.

## 4. Arquitectura actual

Frontend:
- Angular
- Componentes por dominio
- Servicios mock preparados para backend
- Guards por rol
- Login mock institucional

Backend:
- Spring Boot
- PostgreSQL
- Docker Compose
- Endpoint health inicial

Base de datos:
- PostgreSQL
- Tablas futuras para casos, simulaciones, sesiones, mensajes, diagnósticos y uso LLM.

## 5. Estado actual

El frontend está integration-ready:
- UI completa
- rutas protegidas por rol
- mocks centralizados
- servicios preparados
- modelos TypeScript definidos

El backend está en etapa inicial:
- estructura base
- conexión PostgreSQL
- endpoint health

## 6. Próxima etapa

Implementar backend por módulos.

Orden recomendado:
1. Clinical cases
2. Simulations
3. Sessions
4. Professor review
5. Student history
6. LLM simulation
7. LLM usage metrics
8. Admin institucional

## 7. Principios del proyecto

- MVP primero
- No sobreingeniería
- Separar UI, backend y LLM
- El backend controla la simulación
- El LLM solo redacta respuestas como paciente
- No permitir revisión de sesiones en curso
- No exponer API keys al frontend

## 8. Objetivo final

CaseSim debe poder operar como una plataforma institucional desplegable en una universidad, facultad u hospital, permitiendo simulaciones clínicas controladas y trazables con IA.
