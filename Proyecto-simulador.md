# 🏥 Simulador Clínico IA  
## Documento de Arquitectura y Hoja de Ruta (Versión Revisada)

---

## 1. Presentación y contexto

El presente proyecto consiste en una plataforma de simulación clínica orientada a la evaluación y entrenamiento de estudiantes de medicina, con proyección de escalabilidad hacia entornos hospitalarios.

Actualmente, las simulaciones se realizan mediante interfaces abiertas de modelos de lenguaje (LLM) alimentados con documentos clínicos en formato PDF, lo que genera:

- Falta de trazabilidad y auditoría académica  
- Costos de uso impredecibles (billing)  
- Riesgo de manipulación mediante Prompt Injection  
- Inconsistencia en la entrega de información clínica  

---

## 2. Objetivo del sistema

Diseñar una arquitectura cliente-servidor que:

- Controle completamente la interacción con el LLM  
- Permita simulaciones clínicas consistentes y auditables  
- Mejore la seguridad y control de costos  
- Mantenga una experiencia inmersiva para el estudiante  

---

## 3. Principio central de diseño

El sistema se basa en la siguiente separación de responsabilidades:

Backend = control, lógica, seguridad, datos clínicos  
LLM = generación de lenguaje (actuación del paciente)  
Profesor = evaluación clínica  

El LLM no toma decisiones clínicas ni controla la simulación. Solo representa al paciente con la información que el backend le entrega.

---

## 4. Arquitectura del sistema

### Stack tecnológico

- Frontend: Angular  
- Backend: Spring Boot  
- Base de datos: PostgreSQL  
- Proxy: Nginx  
- Infraestructura: Docker  
- Control de versiones: Git  

---

## 5. Motor de Simulación Clínica

### Simulation Engine (Backend)

Responsable de:

- Controlar el estado de la sesión  
- Gestionar el tiempo de la simulación  
- Determinar qué información puede revelarse  
- Detectar intentos de manipulación (prompt injection)  
- Construir el contexto enviado al LLM  
- Registrar auditoría completa  

---

## 6. Modelo de interacción

Flujo de conversación:

Estudiante → Backend → Simulation Engine → LLM → Backend → Estudiante

### Detalle

1. Estudiante envía mensaje  
2. Backend registra mensaje  
3. Simulation Engine:
   - analiza intención  
   - valida seguridad  
   - decide qué datos revelar  
4. Construye contexto limitado  
5. LLM responde como paciente  
6. Se guarda la respuesta  
7. Se muestra al usuario  

---

## 7. Estructura de datos clínicos

Los casos clínicos dejan de ser documentos PDF y pasan a ser datos estructurados.

Ejemplo conceptual:

- Datos del paciente  
- Motivo de consulta  
- Síntomas  
- Antecedentes  
- Personalidad  
- Reglas de revelación  

---

## 8. Modelo de revelación de información

Se reemplaza el enfoque 40/60 por un sistema determinístico:

- Nivel 1: información inicial  
- Nivel 2: información ante preguntas generales  
- Nivel 3: información ante preguntas específicas  
- Nivel 4: información sensible  

---

## 9. Tipos de usuario

### Estudiante
- Interactúa con el paciente simulado  
- No accede a configuración del caso  
- Envía diagnóstico final  
- Puede usar cuadernillo clínico  

### Profesor
- Configura casos clínicos  
- Define parámetros de simulación  
- Accede a transcripciones completas  
- Evalúa desempeño del estudiante  

---

## 10. Gestión de casos clínicos

Los casos se crean mediante formulario estructurado:

- Datos del paciente  
- Motivo de consulta  
- Síntomas iniciales  
- Síntomas revelables  
- Antecedentes  
- Personalidad  
- Expectativas  
- Información restringida  

---

## 11. Gestión de sesiones

Configuración:

- Tiempo configurable  
- Con o sin límite  
- Bloqueo al finalizar  
- Registro completo  

El estudiante puede concluir la atención enviando su diagnóstico.

---

## 12. Auditoría y trazabilidad

El sistema almacena:

- Transcripción completa  
- Diagnóstico final  
- Duración  
- Número de turnos  
- Datos revelados  
- Intentos de manipulación  
- Uso del LLM  

---

## 13. Seguridad

### Backend

- Detección de prompt injection  
- Filtrado de inputs  
- Control de sesiones  
- Límite de uso  

### LLM

Reglas:

- Mantener rol de paciente  
- No entregar diagnósticos  
- No revelar información interna  

---

## 14. Estrategia de contexto

El LLM recibe:

- Prompt base  
- Perfil del paciente  
- Datos habilitados  
- Últimos mensajes  

Nunca recibe el caso completo.

---

## 15. Control de costos

Se implementan:

- Límite de mensajes  
- Límite de tokens  
- Timeout  
- Rate limiting  

---

## 16. Cuadernillo clínico

El estudiante dispone de un espacio privado para:

- Tomar notas  
- Registrar hipótesis  
- Organizar información  

Este contenido:

- No se envía al LLM  
- Se guarda junto a la sesión  
- Es visible para el profesor  

---

## 17. Flujo de diagnóstico

1. Estudiante propone diagnóstico  
2. Paciente responde emocionalmente  
3. Aparece opción de enviar diagnóstico  
4. Se muestra confirmación  
5. Si confirma:
   - Se cierra la sesión  
   - Se registra la información  

---

## 18. Experiencia de usuario

Se busca:

- Simular entrevista clínica real  
- Evitar apariencia de chatbot  
- Mantener naturalidad  

Se incluyen:

- Estado del paciente  
- Indicadores de sesión  
- Interfaz diferenciada  

---

## 19. Soporte multiusuario y concurrencia

El sistema soporta múltiples usuarios simultáneos.

Cada sesión está asociada a:

- usuario_id  
- sesion_id  
- caso_id  
- actividad_id  

No existe estado compartido.

---

## 20. Modelo multi-curso

El sistema soporta múltiples cursos y actividades.

Estructura:

- curso  
- actividad  
- sesión  

Cada estudiante tiene su propia sesión independiente.

---

## 21. Aislamiento de datos

Reglas:

- Todas las consultas filtran por sesión  
- No hay mezcla entre usuarios  
- Acceso restringido por rol  

---

## 22. Persistencia y recuperación

Si el usuario se desconecta:

- La sesión permanece  
- El historial se recupera  

---

## 23. Multiplataforma

El sistema funciona en:

- PC  
- Tablet  
- Teléfono  

Se implementa como web responsive.

---

## 24. Arquitectura de despliegue

MVP:

- Nginx  
- Angular  
- Spring Boot  
- PostgreSQL  

Escalado:

- Load balancer  
- múltiples instancias backend  
- Redis opcional  

---

## 25. Stateless backend

El backend no mantiene estado en memoria.

Toda la información se reconstruye desde base de datos.

---

## 26. Control de acceso

Roles:

- Estudiante  
- Profesor  

Permisos controlados por backend.

---

## 27. Riesgos y mitigación

Riesgos:

- Saturación del LLM  
- Latencia  
- Prompt injection  

Mitigación:

- límites  
- validación  
- control de contexto  

---

## 28. Conclusión

El sistema transforma el uso informal de LLM en una plataforma clínica estructurada que:

- controla la simulación  
- garantiza trazabilidad  
- mejora seguridad  
- mantiene inmersión  

El valor no está en usar un LLM, sino en controlar la simulación clínica.
