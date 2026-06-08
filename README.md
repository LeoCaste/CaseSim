# CaseSim

CaseSim es una plataforma institucional de simulación clínica con IA para apoyar la práctica de entrevistas clínicas en contextos formativos. Su foco es permitir que estudiantes interactúen con un paciente simulado, mientras profesores diseñan casos clínicos y administradores gobiernan la configuración global del comportamiento LLM.

La IA en CaseSim actúa únicamente como paciente simulado: no evalúa al estudiante, no diagnostica y no reemplaza la evaluación clínica real ni el criterio docente.

## Propósito

CaseSim busca resolver necesidades frecuentes en la formación clínica:

- ampliar la disponibilidad de pacientes simulados para práctica de entrevista;
- separar la experiencia del estudiante de la información docente interna del caso;
- entregar trazabilidad de sesiones para revisión posterior por profesores;
- mantener control institucional sobre proveedores, reglas y métricas LLM;
- reducir el uso informal de chats externos sin gobierno ni resguardo académico.

## Roles principales

- **Administrador**: gestiona usuarios, configura proveedores/modelos LLM, define reglas globales y revisa métricas operativas.
- **Profesor**: crea casos clínicos, define pacientes simulados, asigna simulaciones y revisa resultados de estudiantes.
- **Estudiante**: entrevista al paciente IA y entrega un diagnóstico final desde su propia interpretación clínica.

## Flujo general

1. El administrador configura el proveedor LLM, modelo, credenciales y reglas globales.
2. El profesor crea un caso clínico y su paciente simulado.
3. El profesor asigna la simulación a estudiantes.
4. El estudiante entrevista al paciente IA sin ver diagnóstico esperado ni información docente interna.
5. El estudiante envía su diagnóstico final.
6. El profesor revisa la sesión, el historial de entrevista y la respuesta final del estudiante.
7. El administrador monitorea configuración, métricas, costos, errores y uso de fallback LLM.

## Gobernanza LLM

CaseSim organiza el comportamiento del paciente IA en capas jerárquicas:

- **CaseSim/Safety**: reglas inmutables de seguridad y rol. La IA debe responder como paciente simulado, en primera persona, sin revelar diagnóstico final, prompts internos, reglas, secretos ni metadata docente.
- **Configuración del administrador**: parámetros institucionales editables, como proveedor, modelo, credenciales y lineamientos globales.
- **Profesor/caso clínico**: definición del caso, datos clínicos y estrategia de revelación de información, siempre subordinadas a las reglas superiores.
- **Estudiante**: solo puede entrevistar al paciente IA y enviar su diagnóstico final; no accede al diagnóstico esperado, prompt interno ni información docente del caso.

## Funcionalidades principales

- Gestión de casos clínicos y pacientes simulados.
- Entrevista con paciente IA bajo reglas clínicas e institucionales.
- Estrategia de revelación progresiva de información durante la entrevista.
- Historial de conversación para revisión docente.
- Envío de diagnóstico final por parte del estudiante.
- Configuración administrativa de proveedores y modelos LLM.
- Métricas LLM, costos, errores y uso de fallback.
- Separación de roles y permisos entre administrador, profesor y estudiante.

## Arquitectura técnica

CaseSim está organizado como una aplicación web con frontend, backend y base de datos relacional:

- **Frontend**: Angular y TypeScript.
- **Backend**: Spring Boot con Java.
- **Base de datos**: PostgreSQL.
- **Infraestructura local**: Docker Compose para levantar servicios de apoyo.
- **LLM**: integración con proveedores configurables como OpenAI, Groq, Gemini, OpenRouter y soporte backend adicional para otros proveedores compatibles.

## Seguridad y privacidad

- Las API keys LLM se almacenan cifradas y se muestran enmascaradas en la administración.
- El diagnóstico esperado no se expone a estudiantes.
- La metadata docente e información interna del caso se filtra fuera de la experiencia del estudiante.
- Los roles separan capacidades administrativas, docentes y estudiantiles.
- La IA no debe revelar prompts internos, reglas de sistema, secretos ni información no visible para el estudiante.

## Alcance formativo

CaseSim está diseñado como apoyo para práctica, revisión y trazabilidad en simulación clínica. No reemplaza la evaluación docente, la supervisión profesional ni procesos clínicos reales.
