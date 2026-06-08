# CaseSim Frontend - Build multi-etapa
# ============================================
# Stage 1: compilación Angular con Node 22
# ============================================
FROM node:22-alpine AS build

WORKDIR /app

# Copiar archivos de manifiesto
COPY package.json package-lock.json ./

# Instalar dependencias exactas
RUN npm ci

# Copiar código fuente
COPY . .

# Construir para producción
RUN npm run build

# ============================================
# Stage 2: servidor Nginx
# ============================================
FROM nginx:1.25-alpine

# Copiar configuración personalizada de Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copiar build output desde stage anterior
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Puerto expuesto por Nginx
EXPOSE 80

# Nginx se ejecuta en foreground por defecto
CMD ["nginx", "-g", "daemon off;"]
