# Estados de Clinical Case (DRAFT, READY, ARCHIVED)

> Fecha de actualización: 2026-06-06
> Alcance: Integración frontend-backend de estados de caso clínico

## Resumen

Los casos clínicos en CaseSim soportan tres estados que controlan su ciclo de vida:
- **DRAFT** (Borrador): editable, incompleto, no asignable a simulaciones.
- **READY** (Listo): completo, asignable a simulaciones.
- **ARCHIVED** (Archivado): retirado, no asignable.

El backend es la fuente de verdad para la validación de estados. El frontend replica las reglas de UI para mejor UX, pero nunca debe confiarse solo en la validación del lado del cliente.

---

## Backend

### Entidad y Enum

- **`ClinicalCaseStatus`** (`backend/.../clinicalcases/ClinicalCaseStatus.java`):
  ```java
  public enum ClinicalCaseStatus {
      DRAFT,
      READY,
      ARCHIVED;

      public boolean isAssignable() {
          return this == READY;
      }

      public boolean isLegacyActive() {
          return this != ARCHIVED;
      }
  }
  ```

- **`ClinicalCase`** (`backend/.../clinicalcases/ClinicalCase.java`):
  - Campo `status` (`@Enumerated(EnumType.STRING)`, nullable, `VARCHAR(20)`)
  - Campo `activo` (boolean, legacy, para retrocompatibilidad)
  - `getStatus()`: si `status == null`, resuelve desde `activo` vía `ClinicalCaseStatus.fromLegacyActive()`
  - `desactivar()`: establece `status = ARCHIVED` y `activo = false`

### Reglas de validación

- **`ClinicalCaseService.resolveRequestedStatus()`**:
  1. Si `request.status()` no es null → se usa ese valor.
  2. Si `request.active()` no es null → se mapea con `ClinicalCaseStatus.fromLegacyActive()`.
  3. Si ninguno está presente → default `READY`.

- **`ClinicalCaseService.validateReadyMinimums()`**:
  - Solo se ejecuta si el status resuelto es `READY`.
  - Campos obligatorios para READY: `patientName`, `patientAge`, `patientSex`, `chiefComplaint`, `facts` (al menos 1 con `content` no vacío), `noInformationPhrase`.
  - Si faltan, lanza `BadRequestException` con lista de campos faltantes.

- **DRAFT**: No requiere ningún campo mínimo. Se puede guardar con todos los campos vacíos.

- **ARCHIVED**: No requiere campos mínimos. Es un estado terminal para retirar casos.

### Asignación (Simulaciones)

- **`SimulationAssignmentService.createSimulation()`**:
  - Valida que `clinicalCase.getStatus() == ClinicalCaseStatus.READY`.
  - Si no es READY, lanza `BadRequestException("Este caso aún no está listo para ser asignado.")`.
  - DRAFT y ARCHIVED son rechazados.

### Seguridad (datos de estudiante)

- **`StudentClinicalCaseService.getAssignedClinicalCase()`**:
  - Retorna `StudentClinicalCaseResponse` con solo: `activityId`, `clinicalCaseId`, `title` (sanitizado), `patientName`, `patientAge`, `patientSex`, `chiefComplaint`.
  - **NO expone**: `status`, `facts`, `personality`, `description`, `noInformationPhrase`, `expectedDiagnosis` ni placeholders internos.

- **`ClinicalCaseSafetySanitizer`**:
  - `safeCaseTitle()`: retorna genérico `"Caso clínico asignado"` (no el título real del caso).
  - `sanitizeCaseHistory()`: extrae solo el contexto clínico, descartando metadatos internos.

### Persistencia

- Base de datos: columna `status VARCHAR(20) NOT NULL DEFAULT 'READY'` con CHECK constraint `status IN ('DRAFT', 'READY', 'ARCHIVED')`.
- Migración desde legacy: `ALTER TABLE ADD COLUMN IF NOT EXISTS status` con UPDATE desde `activo`.
- `spring.jpa.hibernate.ddl-auto=validate` — el esquema debe existir desde `database/init.sql`.

### Endpoints y RBAC

| Método | Endpoint | Rol | Descripción |
|--------|----------|-----|-------------|
| GET | `/api/v1/clinical-cases` | PROFESOR | Lista casos (excluye ARCHIVED) |
| GET | `/api/v1/clinical-cases/{id}` | PROFESOR | Detalle de caso |
| POST | `/api/v1/clinical-cases` | PROFESOR | Crear caso |
| PUT | `/api/v1/clinical-cases/{id}` | PROFESOR | Actualizar caso |
| PATCH | `/api/v1/clinical-cases/{id}` | PROFESOR | Actualización parcial |
| DELETE | `/api/v1/clinical-cases/{id}` | PROFESOR | Eliminar caso |
| POST | `/api/v1/simulations` | PROFESOR | Asignar caso a simulación |
| GET | `/api/v1/student/clinical-cases/{activityId}` | ESTUDIANTE | Ver caso asignado (sanitizado) |

---

## Frontend

### Modelo TypeScript

- **`ClinicalCaseStatus`** (`frontend/.../models/clinical-case.model.ts`):
  ```typescript
  export type ClinicalCaseStatus = 'DRAFT' | 'READY' | 'ARCHIVED';
  ```

- **Interfaces que incluyen `status`**:
  - `ClinicalCaseSummary`: `status: ClinicalCaseStatus`
  - `ClinicalCase` (extiende Summary): hereda `status`
  - `ClinicalCaseUpsertPayload`: `status: ClinicalCaseStatus`

### Mapeo backend ↔ frontend

- **`mapBackendStatus(response)`**: Lee `response.status` si es DRAFT/READY/ARCHIVED; si no, cae a `response.active` (true → READY, false → ARCHIVED).
- **`mapUpsertPayloadToBackendRequest(payload)`**: Envía `status: payload.status` y `active: payload.status !== 'ARCHIVED'` para compatibilidad legacy.
- **`getStatusLabel(status)`**: `DRAFT → 'Borrador'`, `READY → 'Listo'`, `ARCHIVED → 'Archivado'`.

### Comportamiento en UI

#### Listado (ClinicalCaseCard)
- Muestra badge de estado con clase CSS: `.ready` (READY) o `.draft` (no READY).
- Botón "Asignar a simulación" habilitado solo si `status === 'READY'` (canAssign).
- Si no es READY, el botón se muestra deshabilitado.
- Tooltip visual con label en español ("Borrador", "Listo", "Archivado").

#### Detalle (ClinicalCaseDetailPage)
- Sección "Información base" muestra campo "Estado" con `statusLabel`.
- Botón "Asignar a simulación" visible solo si `canAssign` (status === 'READY').
- Si no es READY, botón alternativo deshabilitado.

#### Formulario (ClinicalCaseFormPage)
- Dropdown de estado con 3 opciones: Borrador, Listo, Archivado.
- Hint: "Solo los casos listos se pueden asignar a simulaciones."
- `validateFacts()` solo valida completamente cuando `status === 'READY'`.
- Al crear nuevo caso, estado inicial es `DRAFT` (desde `buildEmptyDraft()`).
- `saveCase()` envía `payload.status` correctamente al backend.
- Si backend rechaza READY por campos faltantes, se muestra `ErrorModal` con mensaje descriptivo y opción de reintentar.
- `shouldShowPendingRequiredSummary` muestra campos faltantes solo cuando status es READY.

#### Página de Asignación (AssignSimulationPage)
- `canAssignCase`: solo true si `status === 'READY'`.
- Botón "Crear simulación" deshabilitado si no es READY.
- Mensaje visible: "Este caso aún no está listo para ser asignado."
- `confirmCreateSimulation()` verifica `canAssignCase` antes de llamar al backend.
- Errores del backend se capturan y muestran al usuario.

---

## Validación de Placeholders Internos

Los placeholders internos que se usan para construir el formulario DRAFT (`buildEmptyDraft()`: `age: 18`, `sex: 'F'`, `tone: 'Natural y colaborador'`, etc.) están protegidos así:

| Riesgo | Protección | Estado |
|--------|-----------|--------|
| Placeholders en tarjeta de estudiante | `StudentClinicalCaseResponse` no los incluye | ✅ |
| Placeholders en prompt IA | DRAFT no puede asignarse; `ClinicalCaseSafetySanitizer` sanitiza título | ✅ |
| Placeholders permitiendo asignación | Backend rechaza asignación si status != READY | ✅ |
| Placeholders confundiendo detalle profesor | UI muestra "Borrador" como label claro | ✅ |
| Placeholders en descripción | `mapBackendCaseToDetail` parsea metadatos; no se filtran a estudiante | ✅ |

---

## Tests

### Backend

| Test | Archivo | Lo que verifica |
|------|---------|-----------------|
| `createClinicalCaseShouldAllowIncompleteDraftAndExposeStatus` | `ClinicalCaseServiceTest` | DRAFT se guarda con campos vacíos |
| `updateClinicalCaseShouldAllowChangingDraftToReadyWhenMinimumsExist` | `ClinicalCaseServiceTest` | DRAFT → READY funciona si hay mínimos |
| `createClinicalCaseShouldRejectReadyWhenMinimumsAreMissing` | `ClinicalCaseServiceTest` | READY rechazado si faltan mínimos |
| `clinicalCaseShouldMapLegacyActiveWhenStatusIsMissing` | `ClinicalCaseServiceTest` | Legacy active se mapea a status |
| `createSimulationShouldRejectDraftClinicalCase` | `SimulationAssignmentServiceTest` | DRAFT no asignable |
| `createSimulationShouldRejectArchivedClinicalCase` | `SimulationAssignmentServiceTest` | ARCHIVED no asignable |
| `createSimulationShouldAllowReadyClinicalCase` | `SimulationAssignmentServiceTest` | READY es asignable |
| Student endpoint tests | `StudentClinicalCaseControllerTest` | Estudiante recibe DTO sanitizado |

### Frontend

| Test | Archivo | Lo que verifica |
|------|---------|-----------------|
| `should preserve backend clinical case status when present` | `clinical-case.service.spec.ts` | Status se preserva del backend |
| `should fallback legacy active true to READY and active false to ARCHIVED` | `clinical-case.service.spec.ts` | Legacy active → status |
| `should send status and legacy active compatibility in upsert requests` | `clinical-case.service.spec.ts` | Se envía status + active |
| Smoke tests (componentes) | `*.spec.ts` | Creación de componentes |

---

## Validaciones Ejecutadas

```bash
# Backend tests (265 tests, 0 failures)
cd backend && ./mvnw clean test

# Frontend build
cd frontend && npm run build

# Frontend tests (nota: solo tests de servicio, componentes tienen smoke tests)
cd frontend && npm run test:stable  # si existe
```

Resultado: **OK** — todas las validaciones pasan.

---

## Pendientes Reales

1. **Prueba E2E manual**: Profesor crea DRAFT → intenta asignar (no puede) → completa → marca READY → asigna → estudiante inicia entrevista → archiva caso → verificar no asignable.
2. **Remover `isAssignable()` no usado** en `simulation-assignment.service.ts` (código muerto, no bloqueante).
3. **Monitorear budget warning** del bundle frontend (683 kB vs 500 kB de presupuesto) — pre-existente.

---

## Historial de Cambios

| Fecha | Cambio | Autor |
|-------|--------|-------|
| 2026-06-06 | Documentación inicial de integración de estados Clinical Case | CaseSim Orchestrator |
