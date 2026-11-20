# ms07-training-and-ai-assistant
MS07 - Preventive learning content, assessments, and AI assistant service.

## Requerimientos funcionales

**RF-08 · Gestionar contenidos de capacitación**
El sistema permitirá categorizar contenidos preventivos (antes/durante/después, evacuación, laboratorios, salones, oficinas).

**RF-09 · Consultar contenidos por categoría**
El usuario podrá filtrar y consultar contenidos según categoría.

**RF-10 · Registrar seguimiento de progreso**
El sistema registrará qué contenidos ha consultado cada usuario.

**RF-11 · Gestionar evaluaciones cortas**
El sistema permitirá crear y responder evaluaciones asociadas a cada categoría, registrando resultado, tema y fecha.

**RF-12 · Exponer información vía API**
Los contenidos y evaluaciones deberán ser consumibles desde la API documentada de MS07, sin depender de otros microservicios.

**RF-13 · Gestionar consultas al asistente IA**
El sistema permitirá a los usuarios realizar consultas al Tutor CampusSeguro, que responderá exclusivamente con base en información preventiva institucional cargada en el sistema. Si no existe información suficiente para responder, el asistente lo indicará y remitirá al protocolo oficial, sin generar respuestas fuera de su alcance. Cada consulta quedará registrada con metadatos mínimos (fecha, usuario) para fines de auditoría, sin exponer información sensible.
## Historias de usuario

| Historia | RF asociados | Puntos |
|---|---|---|
| HU-4 · Consulta de contenidos categorizados | RF-08, RF-09, RF-10, RF-12 | 3 |
| HU-5 · Evaluaciones cortas por categoría | RF-11, RF-12 | 3 |
| HU-6 · Consultar al tutor CampusSeguro (Asistente IA)| RF-13 | 5 |

## Modelo de dominio

```
Categoria (1) ──< (N) Contenido
Categoria (1) ──< (N) Evaluacion (1) ──< (N) Pregunta
Usuario (1) ──< (N) ProgresoContenido >── (1) Contenido
Usuario (1) ──< (N) ResultadoEvaluacion >── (1) Evaluacion
```

- **Categoria**: id, nombre (ANTES/DURANTE/DESPUES/EVACUACION/LABORATORIOS/SALONES/OFICINAS)
- **Contenido**: id, categoriaId, título, cuerpo, tipo (TEXTO/VIDEO/INFOGRAFIA)
- **ProgresoContenido**: id, usuarioId, contenidoId, fechaConsulta
- **Evaluacion**: id, categoriaId, título
- **Pregunta**: id, evaluacionId, enunciado, opciones, respuestaCorrecta
- **ResultadoEvaluacion**: id, usuarioId, evaluacionId, puntaje, fecha

**Regla de dominio:** `ProgresoContenido` se crea (no se actualiza) cada vez que un usuario abre un contenido — permite historial, no solo el último acceso.

## Contratos API

Base URL local: `http://localhost:8087`.

Todas las rutas protegidas reciben los encabezados de identidad y autorización propagados por el API Gateway: `X-User-Id`, `X-User-Role` y `X-User-Permission`. Los endpoints que reciben datos utilizan JSON.

| Método | Endpoint | Descripción | Permiso |
|---|---|---|---|
| GET | `/api/v1/training/categories` | Listar categorías (`includeInactive`, `page`, `size`) | `ms07:content:read` |
| POST | `/api/v1/training/categories` | Crear categoría | `ms07:content:manage` |
| PUT | `/api/v1/training/categories/{id}` | Actualizar categoría | `ms07:content:manage` |
| PATCH | `/api/v1/training/categories/{id}/deactivate` | Desactivar categoría | `ms07:content:manage` |
| PATCH | `/api/v1/training/categories/{id}/reactivate` | Reactivar categoría | `ms07:content:manage` |
| GET | `/api/v1/training/contents` | Listar contenidos (`categoryId`, `includeInactive`, `page`, `size`) | `ms07:content:read` |
| GET | `/api/v1/training/contents/{id}` | Consultar contenido | `ms07:content:read` |
| POST | `/api/v1/training/contents` | Crear contenido | `ms07:content:manage` |
| PUT | `/api/v1/training/contents/{id}` | Actualizar contenido | `ms07:content:manage` |
| PATCH | `/api/v1/training/contents/{id}/deactivate` | Desactivar contenido | `ms07:content:manage` |
| PATCH | `/api/v1/training/contents/{id}/reactivate` | Reactivar contenido | `ms07:content:manage` |
| POST | `/api/v1/training/contents/{id}/views` | Registrar visualización de contenido | `ms07:content:read` |
| GET | `/api/v1/training/progress/me` | Consultar progreso del usuario autenticado | `ms07:quiz:take` |
| GET | `/api/v1/training/evaluations` | Listar evaluaciones (`categoryId`, `includeInactive`, `page`, `size`) | `ms07:quiz:take` |
| GET | `/api/v1/training/evaluations/{id}` | Consultar evaluación | `ms07:quiz:take` |
| POST | `/api/v1/training/evaluations` | Crear evaluación | `ms07:content:manage` |
| PUT | `/api/v1/training/evaluations/{id}` | Actualizar evaluación | `ms07:content:manage` |
| POST | `/api/v1/training/evaluations/{id}/attempts` | Presentar intento de evaluación | `ms07:quiz:take` |
| PATCH | `/api/v1/training/evaluations/{id}/deactivate` | Desactivar evaluación | `ms07:content:manage` |
| PATCH | `/api/v1/training/evaluations/{id}/reactivate` | Reactivar evaluación | `ms07:content:manage` |
| POST | `/api/v1/tutor/questions` | Consultar al tutor IA | `ms07:tutor:use` |

La especificación OpenAPI se encuentra en [`swagger.yml`](swagger.yml).

## Probar en local

MS07 recibe la identidad y los permisos propagados por el API Gateway.
La autenticación del token se realiza en Gateway; MS07 no valida JWT.

Cabeceras recibidas:

| Cabecera | Contenido |
|---|---|
| `X-User-Id` | UUID del usuario |
| `X-User-Role` | Rol(es) propagados por Gateway |
| `X-User-Permission` | Permisos efectivos propagados por Gateway |
| `X-Correlation-ID` | Identificador de correlación de la solicitud |

**La autorización en MS07 se realiza mediante `X-User-Permission`.**
MS07 no deriva permisos a partir de roles ni mantiene un mapa local de roles.
Los permisos son administrados por MS01 y propagados por Gateway.

| Permiso | Acceso |
|---|---|
| `ms07:content:read` | Consultar categorías y contenidos; registrar visualizaciones |
| `ms07:content:manage` | Crear, actualizar, desactivar y reactivar categorías, contenidos y evaluaciones |
| `ms07:quiz:take` | Consultar evaluaciones, registrar intentos y consultar progreso |
| `ms07:tutor:use` | Consultar al tutor CampusSeguro |

Si falta la identidad requerida, MS07 responde 401 `NO_AUTENTICADO`.
Si el usuario no tiene el permiso requerido, responde 403 `PROHIBIDO`.

Ejemplo de consulta de categorías:

```bash
curl -i http://localhost:8087/api/v1/training/categories \
  -H "X-User-Id: <UUID_DEL_USUARIO>" \
  -H "X-User-Role: ESTUDIANTE" \
  -H "X-User-Permission: ms07:content:read" \
  -H "X-Correlation-ID: <ID_DE_CORRELACION>"
```

Ejemplo de consulta al tutor:

```bash
curl -i -X POST http://localhost:8087/api/v1/tutor/questions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <UUID_DEL_USUARIO>" \
  -H "X-User-Role: ESTUDIANTE" \
  -H "X-User-Permission: ms07:tutor:use" \
  -H "X-Correlation-ID: <ID_DE_CORRELACION>" \
  -d '{"question":"¿Qué hago si hay un sismo mientras estoy en clase?"}'
```

La especificación OpenAPI está disponible en `swagger.yml`.
Cada operación documenta el permiso requerido mediante la extensión `x-permiso`.

## Tutor: proveedor de IA

El tutor (RF-15) llama al modelo de lenguaje a través de adaptadores, siguiendo los patrones del repositorio [aaintegration](https://github.com/ginnko2019/aaintegration):

| Patrón | Clase |
|---|---|
| Adapter | `AiAdapter`, con `OpenAiAdapter` y `AzureOpenAiAdapter` (ambos sobre `ChatCompletionsAdapter`) |
| Componentes independientes | `AiAdapters` recibe todos los adaptadores y usa el de `LLM_PROVIDER`; agregar un proveedor no cambia el tutor |

| Variable | Uso |
|---|---|
| `LLM_PROVIDER` | `openai` (por defecto) o `azure` |
| `LLM_URL` | Endpoint de chat completions. OpenAI: `https://api.openai.com/v1/chat/completions`. Azure: `https://<recurso>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=<versión>` |
| `LLM_API_KEY` | Clave del proveedor. Solo en `.env` o en el entorno de la VM, nunca en el repositorio |
| `LLM_MODEL` | Modelo de OpenAI (por defecto `gpt-4o-mini`); en Azure lo define el deployment |
| `LLM_TIMEOUT` | Tiempo máximo de espera (por defecto `2500ms`, por debajo de los 3000 ms del Gateway) |
| `LLM_MAX_TOKENS`, `LLM_TEMPERATURE` | Largo máximo de la respuesta (250) y temperatura (0.2). Respuestas cortas para que el tutor responda dentro del tiempo del Gateway |

Si falta la clave, el proveedor responde con error o se supera el tiempo, MS07 responde 503 `DEPENDENCIA_NO_DISPONIBLE` y no muestra ninguna respuesta generada. Los logs solo registran el código HTTP del proveedor, nunca la clave ni el cuerpo.

### Preguntar al tutor

`POST /api/v1/tutor/questions` (permiso `ms07:tutor:use`). El tutor busca los contenidos activos relacionados con la pregunta y responde solo con ellos. Si no encuentra ninguno, o el modelo indica que no alcanzan, responde con `sufficient: false` y remite al protocolo oficial de emergencias de la ECI sin generar texto.

```bash
curl -i -X POST http://localhost:8087/api/v1/tutor/questions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19" \
  -H "X-User-Role: ESTUDIANTE" \
  -d '{"question":"¿Qué hago si hay un sismo mientras estoy en clase?"}'
```

```json
{
  "answer": "Agáchate, cúbrete y sujétate…",
  "sufficient": true,
  "sources": [{ "id": "…", "title": "Qué hacer durante un sismo" }]
}
```

| Respuesta | Cuándo |
|---|---|
| 200 | Respuesta del tutor, o `sufficient: false` si no hay información |
| 400 `VALIDACION` | Pregunta vacía o de más de 500 caracteres |
| 401 / 403 | Faltan las cabeceras de identidad o el permiso `ms07:tutor:use` |
| 503 `DEPENDENCIA_NO_DISPONIBLE` | El modelo o la base de datos no responden a tiempo |

Cada consulta respondida o fuera de alcance queda en `audit_event` con el usuario, la fecha y hora y el resultado (`RESPONDIDA` o `FUERA_DE_ALCANCE`). No se guarda el texto de la pregunta ni de la respuesta (RF-15, regla 5). Si ese registro falla, el usuario recibe igual la respuesta y el error queda solo en el log.

La búsqueda de contenidos tolera cómo escribe la gente: cuenta los sinónimos como la misma palabra («terremoto», «temblor» o «tiembla» son «sismo»; «aula» o «clase» son «salón») y lee una palabra que no está en los contenidos como la más parecida, con un error de tipeo en palabras de 5 a 7 letras y hasta dos en las más largas («sizmo» es «sismo», «laboratrio» es «laboratorio»). Las palabras que aparecen en pocos contenidos pesan más que las que aparecen en casi todos. Los sinónimos están en `TutorTerms`.
