CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE institucion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre VARCHAR(200) NOT NULL,
    tipo VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rol (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuario_rol (
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    rol_id UUID NOT NULL REFERENCES rol(id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE curso (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    institucion_id UUID REFERENCES institucion(id) ON DELETE SET NULL,
    nombre VARCHAR(150) NOT NULL,
    codigo VARCHAR(50),
    periodo VARCHAR(50),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE curso_usuario (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    curso_id UUID NOT NULL REFERENCES curso(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    rol_en_curso VARCHAR(50) NOT NULL CHECK (rol_en_curso IN ('ESTUDIANTE', 'PROFESOR')),
    UNIQUE (curso_id, usuario_id)
);

CREATE TABLE caso_clinico (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    paciente_nombre VARCHAR(120),
    paciente_edad INT,
    paciente_sexo VARCHAR(30),
    motivo_consulta TEXT NOT NULL,
    frase_sin_informacion TEXT NOT NULL DEFAULT 'No tengo información asociada a eso.',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'READY'
        CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED')),
    creado_por UUID REFERENCES usuario(id),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Compatibilidad no destructiva para entornos existentes: active=true -> READY, active=false -> ARCHIVED.
ALTER TABLE caso_clinico
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'READY';

UPDATE caso_clinico
SET status = CASE WHEN activo THEN 'READY' ELSE 'ARCHIVED' END
WHERE status IS NULL OR status NOT IN ('DRAFT', 'READY', 'ARCHIVED') OR (activo = FALSE AND status = 'READY');

ALTER TABLE caso_clinico DROP CONSTRAINT IF EXISTS caso_clinico_status_check;
ALTER TABLE caso_clinico ADD CONSTRAINT caso_clinico_status_check
    CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED'));

ALTER TABLE caso_clinico
    ADD COLUMN IF NOT EXISTS duracion_estimada_minutos INT;

CREATE TABLE caso_hecho_clinico (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    caso_id UUID NOT NULL REFERENCES caso_clinico(id) ON DELETE CASCADE,
    categoria VARCHAR(80) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    contenido_paciente TEXT NOT NULL,
    nivel_revelacion INT NOT NULL CHECK (nivel_revelacion BETWEEN 1 AND 4),
    triggers JSONB,
    es_sensible BOOLEAN NOT NULL DEFAULT FALSE,
    orden INT DEFAULT 0
);

CREATE TABLE caso_personalidad (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    caso_id UUID NOT NULL REFERENCES caso_clinico(id) ON DELETE CASCADE,
    rasgo VARCHAR(120) NOT NULL,
    descripcion TEXT NOT NULL
);

CREATE TABLE actividad_simulacion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    curso_id UUID REFERENCES curso(id) ON DELETE CASCADE,
    caso_id UUID NOT NULL REFERENCES caso_clinico(id),
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    modo VARCHAR(50) NOT NULL DEFAULT 'FORMATIVO'
        CHECK (modo IN ('FORMATIVO', 'SUMATIVO')),
    usa_tiempo BOOLEAN NOT NULL DEFAULT FALSE,
    tiempo_limite_minutos INT,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creada_por UUID REFERENCES usuario(id),
    creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sesion_simulacion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actividad_id UUID NOT NULL REFERENCES actividad_simulacion(id) ON DELETE CASCADE,
    estudiante_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'FINALIZADA', 'EXPIRADA')),
    iniciada_en TIMESTAMP,
    finalizada_en TIMESTAMP,
    diagnostico_final TEXT,
    razonamiento_final TEXT,
    turno_diagnostico INT,
    creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (actividad_id, estudiante_id)
);

CREATE TABLE mensaje_chat (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sesion_id UUID NOT NULL REFERENCES sesion_simulacion(id) ON DELETE CASCADE,
    rol VARCHAR(30) NOT NULL CHECK (rol IN ('SYSTEM', 'USER', 'ASSISTANT')),
    contenido TEXT NOT NULL,
    numero_turno INT NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cuadernillo_clinico (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sesion_id UUID NOT NULL REFERENCES sesion_simulacion(id) ON DELETE CASCADE,
    contenido TEXT,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sesion_id)
);

CREATE TABLE sesion_hecho_revelado (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sesion_id UUID NOT NULL REFERENCES sesion_simulacion(id) ON DELETE CASCADE,
    hecho_id UUID NOT NULL REFERENCES caso_hecho_clinico(id) ON DELETE CASCADE,
    mensaje_id UUID REFERENCES mensaje_chat(id),
    revelado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sesion_id, hecho_id)
);

CREATE TABLE uso_llm (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sesion_id UUID REFERENCES sesion_simulacion(id) ON DELETE CASCADE,
    proveedor VARCHAR(80) NOT NULL,
    modelo VARCHAR(100) NOT NULL,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    latencia_ms INT,
    fallback_usado BOOLEAN NOT NULL DEFAULT FALSE,
    error_detalle TEXT,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE llm_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    base_url TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    api_key_secret TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    system_prompt TEXT NOT NULL DEFAULT '',
    patient_behavior_rules TEXT NOT NULL DEFAULT '',
    no_info_response VARCHAR(500) NOT NULL DEFAULT 'No tengo información asociada a eso.',
    reveal_strategy VARCHAR(20) NOT NULL DEFAULT 'PROGRESSIVE'
        CHECK (reveal_strategy IN ('PROGRESSIVE', 'DIRECT', 'RESTRICTIVE')),
    max_history_messages INT NOT NULL DEFAULT 6 CHECK (max_history_messages >= 1),
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.4 CHECK (temperature >= 0.0 AND temperature <= 2.0),
    max_tokens INT NOT NULL DEFAULT 350 CHECK (max_tokens >= 64 AND max_tokens <= 1024),
    enabled_safety_filter BOOLEAN NOT NULL DEFAULT TRUE
);

-- Compatibilidad mínima para entornos existentes (pre LLM-2)
ALTER TABLE llm_config
    ADD COLUMN IF NOT EXISTS system_prompt TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS patient_behavior_rules TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS no_info_response VARCHAR(500) NOT NULL DEFAULT 'No tengo información asociada a eso.',
    ADD COLUMN IF NOT EXISTS reveal_strategy VARCHAR(20) NOT NULL DEFAULT 'PROGRESSIVE',
    ADD COLUMN IF NOT EXISTS max_history_messages INT NOT NULL DEFAULT 6,
    ADD COLUMN IF NOT EXISTS temperature DOUBLE PRECISION NOT NULL DEFAULT 0.4,
    ADD COLUMN IF NOT EXISTS max_tokens INT NOT NULL DEFAULT 350,
    ADD COLUMN IF NOT EXISTS enabled_safety_filter BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE llm_config DROP CONSTRAINT IF EXISTS llm_config_reveal_strategy_check;
ALTER TABLE llm_config ADD CONSTRAINT llm_config_reveal_strategy_check
    CHECK (reveal_strategy IN ('PROGRESSIVE', 'DIRECT', 'RESTRICTIVE'));

ALTER TABLE llm_config DROP CONSTRAINT IF EXISTS llm_config_max_history_messages_check;
ALTER TABLE llm_config ADD CONSTRAINT llm_config_max_history_messages_check
    CHECK (max_history_messages >= 1);

ALTER TABLE llm_config DROP CONSTRAINT IF EXISTS llm_config_temperature_check;
ALTER TABLE llm_config ADD CONSTRAINT llm_config_temperature_check
    CHECK (temperature >= 0.0 AND temperature <= 2.0);

ALTER TABLE llm_config DROP CONSTRAINT IF EXISTS llm_config_max_tokens_check;
ALTER TABLE llm_config ADD CONSTRAINT llm_config_max_tokens_check
    CHECK (max_tokens >= 64 AND max_tokens <= 1024);

CREATE TABLE evento_seguridad (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sesion_id UUID REFERENCES sesion_simulacion(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    tipo VARCHAR(100) NOT NULL,
    descripcion TEXT,
    severidad VARCHAR(30) DEFAULT 'BAJA'
        CHECK (severidad IN ('BAJA', 'MEDIA', 'ALTA')),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_setup_state (
    id BIGINT PRIMARY KEY,
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    initialized_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_token (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usuario_email ON usuario(email);
CREATE INDEX idx_curso_usuario_usuario ON curso_usuario(usuario_id);
CREATE INDEX idx_actividad_curso ON actividad_simulacion(curso_id);
CREATE INDEX idx_sesion_actividad ON sesion_simulacion(actividad_id);
CREATE INDEX idx_sesion_estudiante ON sesion_simulacion(estudiante_id);
CREATE INDEX idx_mensaje_sesion ON mensaje_chat(sesion_id);
CREATE INDEX idx_hecho_caso ON caso_hecho_clinico(caso_id);
CREATE INDEX idx_revelado_sesion ON sesion_hecho_revelado(sesion_id);
CREATE INDEX idx_uso_llm_sesion ON uso_llm(sesion_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_token_hash ON password_reset_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_password_reset_token_user ON password_reset_token(user_id);

INSERT INTO rol (nombre)
VALUES 
('ESTUDIANTE'),
('PROFESOR'),
('ADMIN')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO platform_setup_state (id, initialized)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;
