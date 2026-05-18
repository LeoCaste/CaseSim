# Plan de implementación pre-deploy institucional (CaseSim)

> Fecha: 2026-05-18  
> Alcance: Backend + Frontend + Operación  
> Objetivo: llegar a despliegue institucional seguro **sin romper E2E**

---

## 1) Estado actual

CaseSim está funcional en ~90% para flujo E2E (admin → profesor → estudiante → profesor → admin), con backend Spring + frontend Angular integrados y providers LLM operativos.

### Lo que ya está bien

- Flujo académico completo implementado.
- Roles y autorización por JWT funcionando.
- Panel admin para usuarios y configuración LLM.
- Entrevista clínica con fallback y métricas LLM.
- Build frontend y tests backend pasan.

### Brechas antes de producción

1. Configuración sensible aún con defaults de desarrollo (riesgo en prod).
2. JWT funcional pero no “oficializado” para operación institucional endurecida.
3. Observabilidad de prompt/chat mejorable para soporte operativo.
4. Falta onboarding institucional de **primer admin** (sin credenciales por defecto).
5. Falta recuperación de contraseña admin robusta.
6. Estandarización clínica aún en transición (esperando casos oficiales).

---

## 2) Implementación (pasos concretos)

## Fase A — Seguridad/configuración (Go/No-Go)

### A1. Endurecer configuración por entorno
- Forzar variables obligatorias en `staging/prod`:
  - `JWT_SECRET`
  - clave de cifrado de API keys LLM
  - credenciales DB
  - credenciales SMTP (si aplica reset password)
- Eliminar dependencia operacional de valores por defecto inseguros.

### A2. CORS por dominio productivo
- Parametrizar orígenes permitidos por entorno.
- Mantener localhost sólo para dev.

### A3. Política JWT institucional
- Definir expiración oficial del token.
- Definir comportamiento estándar ante 401/403.
- Documentar ciclo de sesión (login, expiración, logout, re-login).

---

## Fase B — Inicio institucional (nuevo requerimiento)

### B1. First-Run Admin Setup (inicio único)
- Condición: si no existe admin activo / instancia no inicializada.
- Flujo: crear admin institucional con email y contraseña propia.
- Cierre: marcar instancia como inicializada y deshabilitar setup abierto.

### B2. Bootstrap token de instalación
- Requerir token de bootstrap (entregado por despliegue) para ejecutar B1.
- Token de un solo uso o con expiración corta.

### B3. Eliminar admin default en producción
- No permitir operación con cuenta default embebida en entorno productivo.

### B4. Recuperación de contraseña admin
- Flujo “Olvidé contraseña” → token temporal de un uso → reset seguro.
- Invalidar token tras uso/expiración.
- Mensajería neutra para no filtrar existencia de cuentas.

### B5. Auditoría de seguridad
- Registrar eventos:
  - setup inicial completado
  - intentos de login fallidos
  - solicitud reset
  - reset completado

---

## Fase C — Estabilidad UX/E2E

### C1. Checklist anti-regresión async
- Pantallas críticas con:
  - loading visible
  - finalize en success/error
  - error visible
  - empty state explícito

### C2. Manejo auth frontend robusto
- Uniformar reacción a 401/403/network en toda la app.
- Evitar sesiones “zombie” por estado local inconsistente.

---

## Fase D — Observabilidad LLM y operación

### D1. Trazabilidad por request
- Correlación con requestId/sessionId/activityId en logs.

### D2. Higiene de logs
- Prohibido exponer secretos/API keys.
- No registrar prompt completo en producción (solo metadata útil).

### D3. Métricas operativas
- Distinción de errores LLM por clase (401/403/429/5xx/timeout/fallback).

---

## Fase E — Preparación para casos oficiales (sin bloquear avance)

### E1. Contrato clínico canónico v1
- Definir campos obligatorios, formatos y enums.

### E2. Normalización compatible
- Mantener compatibilidad con payload actual.
- Mapear a estructura canónica interna.

### E3. Fixtures clínicos sintéticos
- Crear set de casos de prueba para validar pipeline antes de casos oficiales.

---

## 3) Resultados esperados

Al cerrar estas fases, CaseSim debería quedar:

1. **Desplegable institucionalmente** sin credenciales por defecto ni secretos inseguros.
2. Con **primer acceso admin controlado** (bootstrap + setup único).
3. Con **recuperación de contraseña admin** operativa y auditable.
4. Con **sesión/JWT formalizada** para operación real.
5. Con **E2E estable** (sin regresión en flujo académico principal).
6. Con mejor **trazabilidad LLM** para soporte productivo.
7. Preparado para absorber casos oficiales con menor fricción.

---

## 4) Cómo verificar / validar implementación

## 4.1 Validación técnica mínima

### Backend
- Ejecutar: `./mvnw test`
- Verificar arranque en entorno target con variables reales.

### Frontend
- Ejecutar: `npm run build`
- Verificar rutas protegidas y manejo de errores de sesión.

---

## 4.2 Checklist funcional de aceptación

### A) Setup institucional
1. Despliegue nuevo sin admin creado.
2. Acceso a flujo de setup inicial.
3. Requiere bootstrap token válido.
4. Crea admin institucional exitosamente.
5. Setup deja de estar disponible después del alta.

### B) Login/seguridad
1. Login admin/profesor/estudiante funcional.
2. Rutas por rol bloqueadas correctamente.
3. 401/403 muestran error y redirigen según política definida.

### C) Recuperación de contraseña admin
1. Solicitud de reset genera token temporal.
2. Token expira correctamente.
3. Token sólo se puede usar una vez.
4. Contraseña nueva permite login.

### D) Flujo E2E académico
1. Admin crea usuarios.
2. Profesor crea caso y asigna simulación.
3. Estudiante entrevista y envía diagnóstico final.
4. Profesor revisa sesión.
5. Admin revisa métricas LLM.

### E) LLM/observabilidad
1. Chat responde como paciente simulado.
2. No entrega diagnóstico/nota al estudiante.
3. Errores/fallback quedan trazados en logs/métricas.
4. No se exponen API keys ni secretos en logs/UI.

---

## 4.3 Criterio Go / No-Go

**GO** solo si:
- Setup inicial institucional validado.
- No existen credenciales default activas en producción.
- JWT y secretos configurados por entorno real.
- E2E completo pasa sin bloqueos.
- Validación de seguridad básica aprobada.

**NO-GO** si falla cualquiera de los anteriores.

---

## 5) Orden sugerido de ejecución

1. Fase A (seguridad/config)
2. Fase B (setup inicial + recuperación admin)
3. Fase C (estabilidad UX/auth)
4. Fase D (observabilidad LLM)
5. Fase E (estandarización clínica progresiva)

---

## 6) Nota de alcance

Este plan prioriza estabilidad y seguridad para deploy institucional, evitando refactors masivos y preservando contratos E2E actuales.
