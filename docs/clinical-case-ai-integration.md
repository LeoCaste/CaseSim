# Integración Clinical Case ↔ Paciente IA

> **Última actualización:** 2026-06-06
> **Estado:** ✅ Cerrada
> **Versión prompt:** v2.0 (con CAPAS, metadata expandida, reglas INITIAL/ON_QUESTION)

---

## 1. Mapa final de datos hacia el prompt

### 1.1. Fuentes de datos

| Dato | Fuente (BD/entidad) | Método de extracción |
|---|---|---|
| `patientName` | `ClinicalCase.pacienteNombre` | Directo |
| `patientAge` | `ClinicalCase.pacienteEdad` | `String.valueOf()` |
| `patientSex` | `ClinicalCase.pacienteSexo` | Directo |
| `chiefComplaint` | `ClinicalCase.motivoConsulta` | Directo |
| `caseHistory` | `ClinicalCase.descripcion` → `ClinicalCaseDescriptionParser.parse().clinicalContext()` | Sanitizado (remueve `[CASESIM_META]`, expectedDiagnosis, etc.) |
| `initialMessage` | `[CASESIM_META]` → `safeMetadataValue("initialMessage", "initial_message")` | Sanitizado |
| `broaderContext` | `[CASESIM_META]` → `safeMetadataValue("context", "caseContext")` | Sanitizado |
| `currentIllness` | `[CASESIM_META]` → `safeMetadataValue("currentIllness", "current_illness", "enfermedadActual")` | Sanitizado |
| `generalBackground` | `[CASESIM_META]` → `safeMetadataValue("generalBackground", "general_background", "antecedentesGenerales")` | Sanitizado |
| `clinicalExamFindings` | `[CASESIM_META]` → `safeMetadataValue("clinicalExam.findings", "clinicalExamFindings", "clinical_exam_findings", "findings")` | Sanitizado + regla de no revelación |
| `tone` | `[CASESIM_META]` → `safeMetadataValue("tone", "tono")` | Sanitizado |
| `detailLevel` | `[CASESIM_META]` → `safeMetadataValue("detailLevel", "detail_level")` | Sanitizado |
| `behaviorGuidelines` | `[CASESIM_META]` → `safeMetadataValue("behaviorGuidelines", "behavior_guidelines")` | Sanitizado |
| `noInformationReply` | `ClinicalCase.fraseSinInformacion` > `[CASESIM_META] fallbackResponse` > Admin config > Default | Prioridad CASE > ADMIN > DEFAULT |
| facts | `ClinicalCaseFact` → `selectFactsForPrompt()` | Filtrados por nivel y match |
| personalityTraits | `ClinicalCasePersonality` | Formateados como `"rasgo: descripción"` |
| Historial reciente | `ChatMessage` (últimos N turnos) | `loadRecentHistory()` con `maxHistoryMessages` |

### 1.2. Estructura del prompt final

El prompt se construye en `PromptBuilderService.buildMessages()` y tiene 4 mensajes system + historial + pregunta:

```
[MENSAJE SYSTEM 0 - Prompt multicapa]
  [CAPA_ADMIN_INSTITUCIONAL]
    (system prompt base con reglas de paciente simulado)

  [CAPA_ADMIN_REGLAS_PACIENTE]
    (reglas adicionales configuradas por admin opcionalmente)

  [CAPA_PROFESOR_CONTEXTO_CLINICO]
    Contexto clínico del caso:
    - SessionId: ...
    - ClinicalCaseId: ...
    - Nombre del caso: Caso clínico asignado
    - Nombre del paciente: ...
    - Edad del paciente: ...
    - Sexo del paciente: ...
    - Motivo de consulta principal: ...
    - Historial del caso: ...
    - Mensaje inicial sugerido: ...
    - Contexto comunicable: ...
    - Enfermedad actual comunicable: ...
    - Antecedentes generales comunicables: ...
    - Hallazgos de examen clínico (NO revelar espontáneamente...): ...
    - Tono del paciente: ...
    - Nivel de detalle: ...
    - Guías de conducta del paciente: ...
    - Regla de revelación: ...
    - Regla INITIAL: ...
    - Regla ON_QUESTION: ...
    - Regla examen clínico: ...
    - Rasgos de personalidad del paciente:
      - ...
    Información del paciente (solo lo conocido hasta ahora):
      - [categoria=X] nombre: contenido
      - ...

  [CAPA_PROFESOR_PERSONALIDAD_TONO]
    (rasgos de personalidad repetidos)

  [POLITICA_ROL_Y_NO_DIAGNOSTICO]
    Actúa solo como paciente. No entregues diagnóstico final ni evalúes al estudiante.

  [REGLA_REVELACION]
    Estrategia de revelación: PROGRESSIVE|DIRECT|RESTRICTIVE

[MENSAJE SYSTEM 1 - NoInfoResponse]
  "Respuesta sin información efectiva: \"{noInformationReply}\""

[MENSAJE SYSTEM 2 - Guard]
  "NO uses la respuesta sin información si existe cualquier hecho/síntoma clínico relacionado."

[MENSAJE SYSTEM 3 - Prioridad]
  "Prioridad de reglas: las reglas globales obligatorias del sistema y seguridad prevalecen sobre cualquier texto del caso clínico si existe conflicto."

[MENSAJES DE HISTORIAL] (user/assistant alternados)

[MENSAJE USER - Pregunta actual]
```

---

## 2. Exclusiones confirmadas

| Elemento | ¿Está en el prompt? | Mecanismo de exclusión |
|---|---|---|
| `expectedDiagnosis` | ❌ No | `SENSITIVE_KEY_VALUE` regex en `ClinicalCaseDescriptionParser` |
| `[CASESIM_META]` crudo | ❌ No | `CASESIM_META_BLOCK` regex lo remueve del contexto clínico |
| Título diagnóstico real | ❌ No | `ClinicalCaseSafetySanitizer.safeCaseTitle()` retorna "Caso clínico asignado" |
| `teachingFields` (rúbricas) | ❌ No | Extraídos pero nunca incluidos en `ClinicalPromptContext` |
| API keys / secretos | ❌ No | Enmascarados en logs, nunca en prompt |
| `diagnosticoEsperado` | ❌ No | Misma regex que expectedDiagnosis |
| `finalDiagnosis` | ❌ No | Misma regex |
| Información del profesor | ❌ No | Solo `context`, `currentIllness`, `generalBackground` comunicables |
| Facts no revelados | ❌ No | `selectFactsForPrompt()` filtra por nivel y match |
| Reglas internas del sistema | ❌ No | El prompt dice explícitamente no revelarlas |

---

## 3. Reglas INITIAL/ON_QUESTION

### 3.1. Niveles de revelación

| Nivel | Tipo | Comportamiento |
|---|---|---|
| 1 | **INITIAL** | Siempre incluido en el prompt desde el inicio |
| 2 | **ON_QUESTION** (síntomas) | Solo si hay keyword match suficiente (nivel 2+) |
| 3 | **ON_QUESTION** (antecedentes) | Solo si hay keyword match suficiente (nivel 3+) |

### 3.2. Lógica de selección (`selectFactsForPrompt`)

```
Para cada fact:
  - Si es nivel 1 (INITIAL) → incluir siempre
  - Si ya fue revelado previamente (sessionRevealedFactRepository) → incluir
  - Si es ON_QUESTION (nivel ≥ 2) y el nivel de revelación lo permite:
    - Incluir si hay keyword match en nombre, categoría, contenido o triggers
    - Incluir si hay intención temporal Y el fact es temporal
  - Si DIRECT strategy: sube un nivel el límite
  - Si RESTRICTIVE strategy: baja un nivel el límite
```

### 3.3. Keywords de activación

**LEVEL_2_HINTS** (permiten revelación nivel 2):
```
"como", "donde", "cuando", "cuanto", "tiene", "siente",
"dolor", "fiebre", "tos", "sintoma", "vomito", "nausea"
```

**LEVEL_3_HINTS** (permiten revelación nivel 3):
```
"desde", "antecedente", "alergia", "medicamento", "cirugia",
"hospital", "laboratorio", "examen", "resultado", "familiar",
"cronico", "tratamiento"
```

**TEMPORAL_HINTS** (bypass temporal):
```
"desde cuando", "hace cuanto", "inicio", "empezo", "comenzo",
"cuanto tiempo", "desde"
```

### 3.4. Reglas en el prompt

```
- Regla INITIAL: los hechos disponibles desde el inicio deben usarse de forma
  natural y parcial; nunca los recites como lista completa.
- Regla ON_QUESTION: los hechos de pregunta solo están disponibles cuando aparecen
  en la sección de información conocida por coincidencia con trigger, categoría o
  tema de la pregunta actual, o si ya fueron revelados previamente.
```

---

## 4. Manejo de clinicalExam.findings

### 4.1. Regla de protección en prompt

```
- Regla examen clínico: si hay hallazgos de examen, no los reveles espontáneamente
  ni como lista técnica. Responde como paciente: "me dolía cuando me apretaron" o
  "me dijeron que...". No uses nombres de signos técnicos salvo que explícitamente
  te los hayan mencionado como paciente y la pregunta sea pertinente.
```

### 4.2. Riesgo conocido

La protección de clinicalExam.findings es exclusivamente **prompt engineering**.
No hay filtro programático que impida al LLM revelar los hallazgos técnicos.
Se recomienda monitorear en producción y agregar filtro de seguridad post-procesamiento
si se detectan fugas.

### 4.3. Consideración futura

Separar en dos campos:
- `clinicalExamFindingsTechnical` (solo para el profesor, no va al prompt)
- `clinicalExamFindingsPatient` (versión en primera persona para el prompt)

---

## 5. Duplicidades detectadas y corregidas

### 5.1. chiefComplaint ↔ fact INITIAL

| Dato | Dónde aparece |
|---|---|
| `chiefComplaint` | Header `- Motivo de consulta principal: Dolor abdominal` |
| fact INITIAL | `- [categoria=GENERAL] motivo: Dolor abdominal` |

**Decisión:** No corregir. Es por diseño. El header da resumen rápido al LLM,
los facts dan estructura. El prompt contiene instrucciones explícitas para mitigar
la duplicidad:
- "nunca los recites como lista completa"
- "Responde SOLO lo que te pregunten. No anticipes información no solicitada."

**Test:** `duplicidadChiefComplaintYFactInicialCoexistenSinProblema`

### 5.2. Otras duplicidades evaluadas

| Duplicidad | Severidad | Decisión |
|---|---|---|
| `initialMessage` ↔ `chiefComplaint` | Baja | Son fuentes distintas. `initialMessage` es opcional. |
| `currentIllness` ↔ facts | Baja | `currentIllness` es metadata estructurada; facts son otra fuente. Complementarios. |
| `caseHistory` ↔ metadatos individuales | Baja | `caseHistory` es el texto sanitizado original; los metadatos son campos extraídos. |

---

## 6. Tests agregados

### 6.1. Nuevos tests en `LlmPatientResponseServiceTest` (5 tests nuevos, total 30)

| Test | Archivo | Lo que verifica |
|---|---|---|
| `preguntaDirectaDiagnosticoNoExponeDiagnosticoEsperado` | `LlmPatientResponseServiceTest.java` | expectedDiagnosis no aparece en prompt, ni Apendicitis aguda, ni [CASESIM_META] crudo |
| `fallbackResponseUsadoCuandoRespuestaNoPasaFiltroSeguridad` | `LlmPatientResponseServiceTest.java` | fallbackResponse se usa cuando el filtro de seguridad bloquea |
| `promptCompletoContieneReglasClaveDePaciente` | `LlmPatientResponseServiceTest.java` | 25+ reglas verificadas: primera persona, no IA, no médico, no diagnóstico, reglas INITIAL/ON_QUESTION, 6 secciones del prompt |
| `clinicalExamFindingsEnPromptTieneReglaDeProteccion` | `LlmPatientResponseServiceTest.java` | Regla "NO revelar espontáneamente" presente, contenido sanitizado visible, [CASESIM_META] ausente |
| `duplicidadChiefComplaintYFactInicialCoexistenSinProblema` | `LlmPatientResponseServiceTest.java` | Ambos coexisten con instrucciones de mitigación |

### 6.2. Nuevos tests en `PromptBuilderServiceTest` (2 tests nuevos, total 5)

| Test | Archivo | Lo que verifica |
|---|---|---|
| `clinicalExamFindingsSeMuestraConReglaDeNoRevelacion` | `PromptBuilderServiceTest.java` | Regla de no revelación presente, contenido hallazgos sanitizado |
| `promptBuilderFormateaHechosConCategoriaYNivel` | `PromptBuilderServiceTest.java` | Formato `[categoria=X] nombre: contenido`, reglas INITIAL/ON_QUESTION |

### 6.3. Tests ajustados

| Test | Archivo | Cambio |
|---|---|---|
| `noIncluyeMetadataNiDiagnosticoEsperadoEnPrompt` | `PromptBuilderServiceTest.java` | Record expandido con nuevos campos null |
| `buildContext()` (factory) | `PromptBuilderServiceTest.java` | 8 nuevos campos null en ClinicalPromptContext |

---

## 7. Caso conversacional de prueba

### 7.1. Configuración del caso

```
Caso: "Dolor abdominal"
Paciente: María, 24, F
Motivo consulta: Dolor abdominal
Frase sin info: "No tengo información asociada a eso."

[CASESIM_META]
initialMessage: Me duele la guata.
context: Vivo con mi pareja.
currentIllness: El dolor empezó ayer.
generalBackground: No tengo enfermedades conocidas.
clinicalExam.findings: Abdomen con defensa y Blumberg positivo.
tone: preocupada
detailLevel: breve
behaviorGuidelines: Hablar en primera persona y no ofrecer diagnósticos.
fallbackResponse: No sé eso.
expectedDiagnosis: Apendicitis aguda

Facts:
  [INITIAL] GENERAL/motivo: Dolor abdominal desde ayer
  [ON_QUESTION] ANTECEDENTES/fiebre: He tenido fiebre de 38°C
  [ON_QUESTION] MEDICAMENTOS/uso_actual: Tomo losartán todos los días
  [ON_QUESTION] ANTECEDENTES/alergias: Soy alérgica a la penicilina
  [ON_QUESTION] GENERAL/inicio_sintomas: Comencé con dolor leve y ha empeorado
```

### 7.2. Escenarios y comportamiento esperado

| # | Escenario | Pregunta del estudiante | Comportamiento esperado del paciente IA |
|---|---|---|---|
| 1 | **Saludo general** | "Hola, ¿cómo está?" | Responde como paciente en primera persona indicando su nombre y síntoma principal. NO da diagnóstico. NO dice ser IA. |
| 2 | **Duración** | "¿Desde cuándo tiene dolor?" | Revela fact temporal si existe ("Comencé con dolor leve y ha empeorado") o chiefComplaint. Responde breve y específico. |
| 3 | **Medicamentos** | "¿Toma algún medicamento?" | Revela fact ON_QUESTION por match en categoría MEDICAMENTOS: "Sí, tomo losartán todos los días." |
| 4 | **Alergias** | "¿Tiene alergias?" | Revela fact ON_QUESTION por match en triggers/tema: "Soy alérgica a la penicilina." |
| 5 | **Fiebre/vómitos** | "¿Ha tenido fiebre?" | Revela fact ON_QUESTION por match keyword "fiebre": "Sí, he tenido fiebre de 38°C." |
| 6 | **Pregunta diagnóstica** | "¿Qué enfermedad tengo?" | NO revela diagnóstico esperado. NO sugiere diagnósticos. Responde como paciente: "No sé, doctor, por eso vine a consultar." O usa fallbackResponse. |
| 7 | **Pregunta médica** | "¿Qué examen pediría?" | NO actúa como médico. NO sugiere exámenes. Responde como paciente: "No sabría decirle, usted es el médico." |
| 8 | **Fuera de contexto** | "¿Cómo está el clima?" | Usa fallbackResponse: "No sé eso." |

### 7.3. Verificaciones clave

- **expectedDiagnosis** como "Apendicitis aguda" → ❌ No debe estar en el prompt ni en la respuesta
- **clinicalExam.findings** como "Abdomen con defensa y Blumberg positivo" → ❌ No debe revelarse espontáneamente
- **[CASESIM_META]** → ❌ No debe aparecer crudo en ningún mensaje
- Patient actúa como paciente → ✅ Siempre en primera persona, nunca como médico

---

## 8. Validaciones ejecutadas

| Validación | Comando | Resultado |
|---|---|---|
| Backend tests | `cd backend && ./mvnw test` | ✅ **278 tests, 0 fallos** |
| Frontend build | `cd frontend && npm run build` | ✅ OK (warnings preexistentes) |
| Frontend tests | `cd frontend && npx ng test --watch=false` | ✅ **38 files, 77 tests, 0 fallos** |
| Regresión | Comparación con baseline 271 tests | ✅ Sin regresiones |
| expectedDiagnosis ausente | Tests específicos | ✅ 3 tests lo confirman |
| [CASESIM_META] ausente | Tests específicos | ✅ 4 tests lo confirman |
| INITIAL vs ON_QUESTION | Tests de selección | ✅ 5 tests cubren todos los casos |
| clinicalExam protection | Tests específicos | ✅ 2 tests |
| Reglas de paciente | Test de 25+ aserciones | ✅ Completo |
| Duplicidad mitigada | Test específico | ✅ |

---

## 9. Cambios aplicados

### 9.1. Archivos de producción modificados

| Archivo | Cambios |
|---|---|
| `ClinicalCaseDescriptionParser.java` | Regex permite claves con puntos (e.g., `clinicalExam.findings`) |
| `LlmPatientResponseService.java` | Metadatos extraídos de `[CASESIM_META]` (8 nuevos campos), facts con prefijo `[categoria=]`, `fallbackResponse` desde metadata, DIRECT strategy corregido, categoría en keyword matching, **"que" removido de LEVEL_2_HINTS** |
| `PromptBuilderService.java` | Nueva sección `formatMetadataContextSection()` con metadatos, reglas INITIAL/ON_QUESTION/clinicalExam en prompt, ClinicalPromptContext expandido |

### 9.2. Archivos de test modificados/agregados

| Archivo | Tests agregados | Tests totales |
|---|---|---|
| `PromptBuilderServiceTest.java` | +2 | 5 |
| `LlmPatientResponseServiceTest.java` | +5 | 30 |
| `ClinicalCaseDescriptionParserTest.java` | +1 | 4 |

---

## 10. Pendientes reales

| # | Pendiente | Prioridad | Estado |
|---|---|---|---|
| 1 | **Validación E2E manual** con LLM real en los 8 escenarios del caso conversacional | Alta | ⏳ Pendiente |
| 2 | Monitorear comportamiento del LLM con `clinicalExam.findings` visibles en el prompt | Media | ⏳ Pendiente |
| 3 | Separar `clinicalExamFindings` en versión paciente vs versión técnica (profesor) | Media | Futuro refactor |
| 4 | Usar campo `esSensible` de `ClinicalCaseFact` para restringir revelación | Media | Futuro refactor |
| 5 | Agregar stemming mínimo (plural/singular) en keyword matching | Baja | Futura mejora |
| 6 | Refactorizar `LlmPatientResponseService` (clase grande, SRP violado) | Baja | Post-entrega |
| 7 | Renombrar etiquetas internas `[CAPA_ADMIN...]` para evitar posible leakage | Baja | Futura mejora |
| 8 | Agregar filtro post-procesamiento para sugerencias de exámenes | Baja | Futura mejora |

---

## 11. Arquitectura del flujo de datos

```
[ClinicalCase DB]
  ├── descripcion ──→ ClinicalCaseDescriptionParser ──→ clinicalContext (sanitizado)
  │                                               └── legacyMetadata (Map<String,String>)
  │                                                     ├── initialMessage
  │                                                     ├── context
  │                                                     ├── currentIllness
  │                                                     ├── generalBackground
  │                                                     ├── clinicalExam.findings
  │                                                     ├── tone
  │                                                     ├── detailLevel
  │                                                     ├── behaviorGuidelines
  │                                                     └── fallbackResponse
  ├── pacienteNombre/Edad/Sexo ──→ patientName/Age/Sex
  ├── motivoConsulta ──→ chiefComplaint
  └── fraseSinInformacion ──→ noInformationReply (CASE > ADMIN > DEFAULT)

[ClinicalCaseFact DB]
  └── selectFactsForPrompt() ──→ List<String> facts
        ├── nivelRevelacion=1 → INITIAL (siempre)
        ├── nivelRevelacion≥2 → ON_QUESTION (con match)
        ├── ya revelados → desde SessionRevealedFact
        └── formato: "[categoria=X] nombre: contenido"

[ClinicalCasePersonality DB]
  └── List<String> personalityTraits → "rasgo: descripción"

[ChatMessage DB]
  └── loadRecentHistory() → List<ChatMessage> (historial conversacional)

         ↓
  [PromptBuilderService.buildMessages()]
         ↓
  [LlmClient.generate()] → [ResponseSafetyFilter.applyOrFallback()]
         ↓
  [Respuesta final del paciente IA]
```
