# Sistema de Consulta SRI y ANT (C4 + React + Java + Upstash Redis)

Proyecto de **Ingeniería de Software — ISWZ2202 (Diseño y Arquitectura de Software)**.

## Arquitectura (Modelo C4 en IcePanel)
- **Diagrama C4 en IcePanel:** https://s.icepanel.io/GXimZuFIGRA1Sh/YFo1
- **Contenedores:**
  - `frontend/` — **Aplicación Web (React + TypeScript + Vite)** con componentes `Formulario` y `VisualizadorConsulta`.
  - `backend/` — **API / Backend (Java 17+ / Spring Boot)** con `ConsultaController`, `SriContribuyenteClient`, `SriVehiculoClient` y `AntLicenciaCacheService`.
  - **Caché Cloud** — **Upstash Redis** (`lib/redis.ts` + cliente REST `UpstashRedisCacheClient`) usando el patrón **Cache-Aside + Stale-While-Error Fallback**.

## Patrón de Resiliencia para la ANT
La web de la ANT (`clp_grid_citaciones.jsp`) presenta alta latencia y baja disponibilidad (ej. errores `502 Bad Gateway`). El backend aplica:
1. **Cache-Aside:** antes de llamar a la ANT consulta `ant:licencia:{cedula}` en Upstash Redis Cloud. Si existe un registro vigente (< 1 hora), responde directamente desde caché.
2. **Almacenamiento selectivo:** solo cuando la ANT devuelve una respuesta válida con el saldo de puntos se guarda en Upstash Redis Cloud (TTL 7 días).
3. **Stale-While-Error Fallback:** si el portal ANT falla o excede el timeout (6 s), el servicio recupera el último registro válido guardado en Upstash Redis y lo devuelve indicando `fuenteDatos = CACHE_CLOUD_FALLBACK`.

## Ejecución Local

### 1. Variables de entorno
Copia `.env.example` a `.env` en la raíz del proyecto y completa tus credenciales de Upstash Redis:
```bash
UPSTASH_REDIS_REST_URL=https://tu-instancia.upstash.io
UPSTASH_REDIS_REST_TOKEN=tu-token
```

### 2. Backend (Java + Spring Boot)
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
API disponible en `http://localhost:8081`.

### 3. Frontend (React + Vite)
```powershell
cd frontend
npm install
npm run dev
```
Aplicación disponible en `http://localhost:5173`.

## Pruebas
```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run build
```
