BEGIN;

-- =========================================================
-- 1) Institución demo (UUID fijo requerido)
-- =========================================================
INSERT INTO institucion (id, nombre, tipo, activo)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CaseSim Demo',
    'universidad',
    TRUE
)
ON CONFLICT (id) DO UPDATE
SET nombre = EXCLUDED.nombre,
    tipo = EXCLUDED.tipo,
    activo = EXCLUDED.activo;

-- =========================================================
-- 2) Roles (idempotente por UNIQUE(nombre))
-- =========================================================
INSERT INTO rol (nombre)
VALUES
    ('ESTUDIANTE'),
    ('PROFESOR'),
    ('ADMIN')
ON CONFLICT (nombre) DO NOTHING;

-- =========================================================
-- 3) Usuarios demo (UUIDs fijos requeridos)
-- =========================================================
INSERT INTO usuario (id, nombre, email, password_hash, activo)
VALUES
    (
        '00000000-0000-0000-0000-000000000101',
        'Profesor Demo',
        'profesor.demo@ufrontera.cl',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.5W5n1p5MNDoAuCEi0aKBslrHonghE2e',
        TRUE
    ),
    (
        '00000000-0000-0000-0000-000000000102',
        'Estudiante Demo 01',
        'estudiante.demo01@ufromail.cl',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.5W5n1p5MNDoAuCEi0aKBslrHonghE2e',
        TRUE
    ),
    (
        '00000000-0000-0000-0000-000000000103',
        'Administrador Demo',
        'admin.demo@ufrontera.cl',
        '$2b$10$Lb9r.5K9fJCLtr/JbDINyuIlFqJhNhxfAQtImAZf0nWrAK2RaDec6',
        TRUE
    )
ON CONFLICT (id) DO UPDATE
SET nombre = EXCLUDED.nombre,
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    activo = EXCLUDED.activo;

-- =========================================================
-- 4) usuario_rol (idempotente por PK compuesta)
-- =========================================================
INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT '00000000-0000-0000-0000-000000000101', r.id
FROM rol r
WHERE r.nombre = 'PROFESOR'
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT '00000000-0000-0000-0000-000000000102', r.id
FROM rol r
WHERE r.nombre = 'ESTUDIANTE'
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT '00000000-0000-0000-0000-000000000103', r.id
FROM rol r
WHERE r.nombre = 'ADMIN'
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

-- =========================================================
-- 5) Curso (UUID fijo requerido)
-- =========================================================
INSERT INTO curso (id, institucion_id, nombre, codigo, periodo, activo)
VALUES (
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000001',
    'Medicina Interna - Demo',
    'MED-INT-DEMO',
    '2026-1',
    TRUE
)
ON CONFLICT (id) DO UPDATE
SET institucion_id = EXCLUDED.institucion_id,
    nombre = EXCLUDED.nombre,
    codigo = EXCLUDED.codigo,
    periodo = EXCLUDED.periodo,
    activo = EXCLUDED.activo;

-- =========================================================
-- 6) curso_usuario (idempotente por UNIQUE(curso_id, usuario_id))
-- =========================================================
INSERT INTO curso_usuario (id, curso_id, usuario_id, rol_en_curso)
SELECT
    '00000000-0000-0000-0000-000000000211',
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000101',
    'PROFESOR'
WHERE NOT EXISTS (
    SELECT 1
    FROM curso_usuario cu
    WHERE cu.curso_id = '00000000-0000-0000-0000-000000000201'
      AND cu.usuario_id = '00000000-0000-0000-0000-000000000101'
);

INSERT INTO curso_usuario (id, curso_id, usuario_id, rol_en_curso)
SELECT
    '00000000-0000-0000-0000-000000000212',
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000102',
    'ESTUDIANTE'
WHERE NOT EXISTS (
    SELECT 1
    FROM curso_usuario cu
    WHERE cu.curso_id = '00000000-0000-0000-0000-000000000201'
      AND cu.usuario_id = '00000000-0000-0000-0000-000000000102'
);

-- =========================================================
-- 7) caso_clinico (UUID fijo requerido)
-- =========================================================
INSERT INTO caso_clinico (
    id, titulo, descripcion, paciente_nombre, paciente_edad, paciente_sexo,
    motivo_consulta, frase_sin_informacion, activo, creado_por
)
VALUES (
    '00000000-0000-0000-0000-000000000301',
    'Tos seca y cansancio progresivo en adulta joven',
    'Caso clínico demo para entrevista inicial y razonamiento diagnóstico.',
    'Catalina Paz Soto',
    22,
    'Femenino',
    'Vengo porque tengo una tos seca que no se me pasa y me siento muy agotada.',
    'No tengo información asociada a eso.',
    TRUE,
    '00000000-0000-0000-0000-000000000101'
)
ON CONFLICT (id) DO UPDATE
SET titulo = EXCLUDED.titulo,
    descripcion = EXCLUDED.descripcion,
    paciente_nombre = EXCLUDED.paciente_nombre,
    paciente_edad = EXCLUDED.paciente_edad,
    paciente_sexo = EXCLUDED.paciente_sexo,
    motivo_consulta = EXCLUDED.motivo_consulta,
    frase_sin_informacion = EXCLUDED.frase_sin_informacion,
    activo = EXCLUDED.activo,
    creado_por = EXCLUDED.creado_por;

-- =========================================================
-- 8) caso_hecho_clinico (mínimos requeridos + nivel_revelacion)
-- =========================================================
INSERT INTO caso_hecho_clinico
(id, caso_id, categoria, nombre, contenido_paciente, nivel_revelacion, triggers, es_sensible, orden)
VALUES
(
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000301',
    'ENFERMEDAD_ACTUAL',
    'inicio_sintomas',
    'Empecé hace unas tres semanas, primero con tos suave y luego me fui sintiendo cada vez más cansada.',
    1,
    '{"keywords":["inicio","cuándo comenzó","evolución"]}'::jsonb,
    FALSE,
    1
),
(
    '00000000-0000-0000-0000-000000000502',
    '00000000-0000-0000-0000-000000000301',
    'ENFERMEDAD_ACTUAL',
    'tos',
    'La tos es seca, sin flema, y me da varias veces al día.',
    1,
    '{"keywords":["tos","características de la tos"]}'::jsonb,
    FALSE,
    2
),
(
    '00000000-0000-0000-0000-000000000503',
    '00000000-0000-0000-0000-000000000301',
    'ENFERMEDAD_ACTUAL',
    'disnea',
    'Me falta un poco el aire cuando subo escaleras o camino rápido.',
    2,
    '{"keywords":["disnea","falta de aire","respiración"]}'::jsonb,
    FALSE,
    3
),
(
    '00000000-0000-0000-0000-000000000504',
    '00000000-0000-0000-0000-000000000301',
    'EXAMEN_DIRIGIDO',
    'adenopatias',
    'He notado ganglios en el cuello, como bolitas sensibles al tocarlas.',
    3,
    '{"keywords":["ganglios","adenopatías","cuello"]}'::jsonb,
    FALSE,
    4
),
(
    '00000000-0000-0000-0000-000000000505',
    '00000000-0000-0000-0000-000000000301',
    'EPIDEMIOLOGIA',
    'contacto_epidemiologico',
    'Hace poco compartí varias horas en espacios cerrados con una compañera que estuvo enferma.',
    3,
    '{"keywords":["contacto","epidemiológico","exposición"]}'::jsonb,
    FALSE,
    5
),
(
    '00000000-0000-0000-0000-000000000506',
    '00000000-0000-0000-0000-000000000301',
    'ANTECEDENTES',
    'antecedentes',
    'No tengo enfermedades crónicas ni cirugías previas importantes.',
    2,
    '{"keywords":["antecedentes","enfermedades previas"]}'::jsonb,
    FALSE,
    6
),
(
    '00000000-0000-0000-0000-000000000507',
    '00000000-0000-0000-0000-000000000301',
    'ANTECEDENTES',
    'alergias',
    'No conozco alergias a medicamentos ni a alimentos.',
    2,
    '{"keywords":["alergias","reacciones"]}'::jsonb,
    FALSE,
    7
),
(
    '00000000-0000-0000-0000-000000000508',
    '00000000-0000-0000-0000-000000000301',
    'ANTECEDENTES',
    'medicamentos',
    'Solo he tomado paracetamol ocasional para el malestar general.',
    2,
    '{"keywords":["medicamentos","tratamiento actual"]}'::jsonb,
    FALSE,
    8
)
ON CONFLICT (id) DO UPDATE
SET caso_id = EXCLUDED.caso_id,
    categoria = EXCLUDED.categoria,
    nombre = EXCLUDED.nombre,
    contenido_paciente = EXCLUDED.contenido_paciente,
    nivel_revelacion = EXCLUDED.nivel_revelacion,
    triggers = EXCLUDED.triggers,
    es_sensible = EXCLUDED.es_sensible,
    orden = EXCLUDED.orden;

-- =========================================================
-- 9) caso_personalidad
-- =========================================================
INSERT INTO caso_personalidad (id, caso_id, rasgo, descripcion)
VALUES
(
    '00000000-0000-0000-0000-000000000601',
    '00000000-0000-0000-0000-000000000301',
    'organizada',
    'Responde de forma ordenada y cronológica cuando se le pregunta.'
),
(
    '00000000-0000-0000-0000-000000000602',
    '00000000-0000-0000-0000-000000000301',
    'preocupada',
    'Se muestra inquieta por la persistencia de los síntomas y su impacto.'
),
(
    '00000000-0000-0000-0000-000000000603',
    '00000000-0000-0000-0000-000000000301',
    'colaboradora',
    'Está dispuesta a responder preguntas y seguir indicaciones médicas.'
),
(
    '00000000-0000-0000-0000-000000000604',
    '00000000-0000-0000-0000-000000000301',
    'molesta por cansancio',
    'Le frustra sentirse agotada y con baja energía en el día a día.'
),
(
    '00000000-0000-0000-0000-000000000605',
    '00000000-0000-0000-0000-000000000301',
    'quiere volver a rutina',
    'Su principal objetivo es recuperar su funcionamiento habitual pronto.'
)
ON CONFLICT (id) DO UPDATE
SET caso_id = EXCLUDED.caso_id,
    rasgo = EXCLUDED.rasgo,
    descripcion = EXCLUDED.descripcion;

-- =========================================================
-- 10) actividad_simulacion (UUID fijo requerido)
-- =========================================================
INSERT INTO actividad_simulacion (
    id, curso_id, caso_id, titulo, descripcion, modo, usa_tiempo, tiempo_limite_minutos, activa, creada_por
)
VALUES
(
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000301',
    'Actividad Demo - Entrevista clínica inicial',
    'Simulación formativa para anamnesis y priorización diagnóstica.',
    'FORMATIVO',
    FALSE,
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000101'
),
(
    '00000000-0000-0000-0000-000000000402',
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000301',
    'Actividad prueba flujo real',
    'Actividad secundaria para validar flujo real de sesión en backend.',
    'FORMATIVO',
    FALSE,
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000101'
)
ON CONFLICT (id) DO UPDATE
SET curso_id = EXCLUDED.curso_id,
    caso_id = EXCLUDED.caso_id,
    titulo = EXCLUDED.titulo,
    descripcion = EXCLUDED.descripcion,
    modo = EXCLUDED.modo,
    usa_tiempo = EXCLUDED.usa_tiempo,
    tiempo_limite_minutos = EXCLUDED.tiempo_limite_minutos,
    activa = EXCLUDED.activa,
    creada_por = EXCLUDED.creada_por;

COMMIT;
