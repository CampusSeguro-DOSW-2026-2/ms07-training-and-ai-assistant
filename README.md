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
| POST | `/api/v1/training/evaluation-drafts` | Pedir a la IA un borrador de evaluación (202) | `ms07:content:manage` |
| GET | `/api/v1/training/evaluation-drafts/{id}` | Consultar el estado del borrador | `ms07:content:manage` |
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

`POST /api/v1/tutor/questions` (permiso `ms07:tutor:use`). El tutor responde sobre prevención y actuación ante cualquier emergencia o evento del campus que esté en los contenidos, no solo sismos: incendios, fugas o derrames de sustancias, evacuación, personas lesionadas y primeros auxilios básicos, puntos de encuentro, simulacros y prevención en salones, laboratorios, auditorios y oficinas. Busca los contenidos activos relacionados con la pregunta y responde solo con ellos. Si no encuentra ninguno, o el modelo indica que no alcanzan, responde con `sufficient: false` y remite al protocolo oficial de emergencias de la ECI sin generar texto.

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
| 429 `DEMASIADAS_SOLICITUDES` | El usuario superó el límite de preguntas por minuto o por día |
| 503 `DEPENDENCIA_NO_DISPONIBLE` | El modelo o la base de datos no responden a tiempo |

Cada consulta respondida o fuera de alcance queda en `audit_event` con el usuario, la fecha y hora y el resultado (`RESPONDIDA` o `FUERA_DE_ALCANCE`). No se guarda el texto de la pregunta ni de la respuesta (RF-15, regla 5). Si ese registro falla, el usuario recibe igual la respuesta y el error queda solo en el log.

### Cómo busca el tutor

Antes de llamar al modelo, el tutor busca los contenidos activos que tienen que ver con la pregunta (`ContentRetriever`). Combina dos búsquedas.

**1. Por palabras.** La pregunta y los contenidos se comparan sin tildes, sin mayúsculas y sin palabras vacías («qué», «hay», «por favor»…):

- **Raíces en español** (`SpanishStemmer`): quita plurales (`-s`, `-es`, `-ces`→`-z`), diminutivos y terminaciones verbales frecuentes (`-ar`, `-er`, `-ir`, `-ando`, `-iendo`, `-ado`, `-ido`, `-ción`). Así «fuga», «fugas» y «fugó» son la misma palabra, igual que «evacuar», «evacuación» y «evacuando».
- **Sinónimos por tema** en [`src/main/resources/tutor/synonyms.txt`](src/main/resources/tutor/synonyms.txt): un grupo por línea, legible y fácil de ampliar, que se carga al arrancar. Cubre sismo, incendio, fuga y derrame, sustancias, lesiones, evacuación, punto de encuentro, ascensor, energía, inundación, amenazas, brigadistas, alarmas, extintores y los espacios. Las frases de varias palabras cuentan como una sola: «se fue la luz» es «energía», «olor a gas» es «fuga», «zona segura» es «punto de encuentro».
- **Errores de tipeo:** una palabra que no está en los contenidos se lee como la más parecida de los contenidos o de los sinónimos:
  - en palabras de 4 letras se permite 1 error, solo contra palabras que empiezan por la misma letra («fuja» es «fuga»);
  - de 5 a 7 letras, 1 error («sizmo», «incendo», «evaquar»);
  - de 8 o más, 2 errores.

  Nunca se corrigen palabras de 3 letras ni palabras vacías.
- **Peso:** la palabra vale más en el título (3) que en el texto (1), y vale el doble si aparece en pocos contenidos y el triple si aparece en muy pocos. Una sola palabra común en el texto («celular», «parte») no basta: hace falta que esté en el título, que sea una palabra de los temas (las del archivo de sinónimos) o que coincidan al menos dos palabras.

**2. Por significado (híbrida).** Si el proveedor tiene embeddings, cada contenido activo tiene su vector, guardado en `content_embedding` como JSON (sin exigir `pgvector`):

- **Cuándo se calcula:** al arrancar MS07, los que falten o estén desactualizados; y después de crear, editar o reactivar un contenido, en segundo plano y después del commit.
- **Con cada pregunta:** se calcula el embedding de la pregunta **ya redactada**, sin datos personales, y su similitud coseno con cada contenido.
- **Puntaje:** la similitud se normaliza entre 0,25 (temas distintos) y 0,60 (mismo tema), y se combina así: `0,6 × significado + 0,4 × palabras`.
- **Qué contenidos se usan:** los que las palabras ya encontraban, más los que alcanzan `TUTOR_SEARCH_MIN_SCORE` (0,35 por defecto). El significado encuentra más contenidos, pero nunca esconde uno que las palabras encontraron.
- **Si falla:** si los embeddings fallan, tardan más que `LLM_EMBEDDING_TIMEOUT` o no hay clave, la búsqueda sigue solo con palabras, sin error para el usuario.

Los valores salen de una calibración con `text-embedding-3-small` y los contenidos iniciales. Las preguntas del tema dieron una similitud máxima de 0,37 a 0,70; las de otros temas, de 0,15 a 0,36. Por eso un contenido solo entra por significado desde una similitud de 0,45 aproximadamente.

**Tiempo:** comparar la pregunta con todos los contenidos tarda pocos milisegundos (los vectores están en memoria). Lo que más pesa es el embedding de la pregunta, unos cientos de milisegundos. El log de nivel DEBUG muestra solo números (tiempos y puntaje), nunca la pregunta.

| Variable | Por defecto | Uso |
|---|---|---|
| `LLM_EMBEDDING_URL` | `https://api.openai.com/v1/embeddings` | Endpoint de embeddings. En Azure, el del deployment de embeddings. Vacío: solo palabras |
| `LLM_EMBEDDING_MODEL` | `text-embedding-3-small` | Modelo de embeddings (en Azure lo define el deployment) |
| `LLM_EMBEDDING_TIMEOUT` | `2000ms` | Tiempo máximo para el embedding de una pregunta |
| `TUTOR_SEARCH_MIN_SCORE` | `0.35` | Puntaje combinado mínimo para usar un contenido encontrado solo por significado |

Las pruebas usan un modelo de embeddings falso y determinista:

- `TutorSearchQualityTest` tiene 37 preguntas reales mal escritas o con sinónimos, con el contenido que deben encontrar, y 15 preguntas de otros temas que no deben encontrar nada.
- `TutorSemanticSearchTest` prueba un contenido encontrado solo por significado, el respaldo cuando fallan los embeddings y que el embedding recibe la pregunta ya redactada.

Con los contenidos iniciales, «¿Qué hago si se riega un químico en el lab?» encuentra «Qué hacer ante un derrame o una fuga de sustancias», «hay humo en el pasillo» encuentra «Mantén libres las salidas y los pasillos del salón» (y el contenido de incendios, si se crea uno) y «una compañera se desmayó» encuentra «Primeras acciones después de un sismo». Una pregunta fuera de tema, como «receta de pasta», sigue sin encontrar nada y no llama al modelo.

### Seguridad del tutor

**Datos personales.** Antes de enviar la pregunta al modelo, `PersonalDataRedactor` reemplaza los datos personales por una marca. La búsqueda de contenidos usa la pregunta original porque no sale de MS07; lo que llega al proveedor de IA va siempre sin esos datos.

| Dato | Ejemplo | Queda como |
|---|---|---|
| Correo | `ana.gomez@gmail.com` | `[correo]` |
| Teléfono (celular `3xx xxx xxxx`, fijo `60x xxx xxxx` o de 7 dígitos después de «teléfono», «tel.», «fijo» o `+57`; con espacios, puntos o guiones) | `+57 300 123 4567` | `[teléfono]` |
| Documento (cédula o tarjeta de identidad de 6 a 10 dígitos, con o sin puntos, o después de «cédula», «C.C.», «T.I.», «documento») | `mi cédula es 1.234.567` | `mi cédula es [documento]` |
| Tarjeta (13 a 19 dígitos que pasan la validación de Luhn) | `4111 1111 1111 1111` | `[tarjeta]` |
| Enlace | `https://…`, `www.…` | `[enlace]` |
| Nombre dicho de forma explícita | `me llamo Ana María`, `soy Ana Gómez` | `me llamo [nombre]`, `soy [nombre]` |

Los números cortos del campus no se tocan: «piso 3», «bloque 2», «salón 205» o «1990».

**Nada identifica a quien pregunta.** La petición al proveedor solo lleva el modelo, los dos mensajes, la temperatura y el máximo de tokens: ni el campo `user`, ni el id del usuario, ni las cabeceras `X-User-*`, ni el `X-Correlation-ID`. A OpenAI se le envía además `"store": false` para que no guarde la conversación; a Azure no, porque no todas sus versiones de API aceptan ese campo.

**Instrucciones que no se pueden cambiar desde la pregunta.** El mensaje de sistema trae las reglas y después cada contenido entre `<contenido id="…" titulo="…">` y `</contenido>`. La pregunta va en el mensaje de usuario como `Pregunta del usuario: «…»`. Las reglas dicen que los contenidos y la pregunta son datos y nunca instrucciones, y prohíben revelar las instrucciones, cambiar de rol o generar código, enlaces o datos que no estén en los contenidos. Ni un contenido ni la pregunta pueden cerrar sus delimitadores: los `<` y `>` se envían como `‹` y `›`, y las comillas `«` `»` de la pregunta como `"`.

**Respuesta revisada antes de mostrarla.**

- Se quitan las etiquetas HTML, las marcas de Markdown (`**`, `#`, bloques de código) y los enlaces que no aparecen en los contenidos usados.
- Se recorta a `TUTOR_MAX_ANSWER_CHARS` caracteres (1200 por defecto) sin partir una palabra, y termina en «…».
- Si la respuesta repite las reglas del sistema (seis palabras seguidas de ellas que no estén en los contenidos) o sus delimitadores, se trata como `SIN_INFORMACION`: el usuario recibe `sufficient: false` y la remisión al protocolo oficial.

**Límite de uso.** Cada usuario (`X-User-Id`) puede hacer `TUTOR_RATE_PER_MINUTE` preguntas por minuto (10 por defecto) y `TUTOR_RATE_PER_DAY` por día (100 por defecto), con ventanas deslizantes en memoria. Al superarlo MS07 responde 429 `DEMASIADAS_SOLICITUDES` con un mensaje en español; esa pregunta no llega al modelo ni queda registrada como respondida. El conteo es por instancia y se reinicia si MS07 se reinicia.

**Logs.** Ningún log contiene la pregunta, la respuesta, el prompt ni la clave: solo el proveedor, el código HTTP o el tipo de error y el resultado. Una prueba revisa la salida de los logs para confirmarlo.

| Variable | Por defecto | Uso |
|---|---|---|
| `TUTOR_RATE_PER_MINUTE` | `10` | Preguntas por usuario por minuto |
| `TUTOR_RATE_PER_DAY` | `100` | Preguntas por usuario por día |
| `TUTOR_MAX_ANSWER_CHARS` | `1200` | Largo máximo de la respuesta que se muestra |

En la VM no hace falta definirlas si sirven los valores por defecto; para cambiarlas se agregan como líneas `CLAVE=valor` en `MS07_EXTRA_ENV`.

## Borradores de evaluaciones con IA

El ADMIN puede pedirle a la IA un **borrador** de evaluación hecho solo con los contenidos activos de una categoría (RF-14). La IA nunca guarda nada: el ADMIN revisa y ajusta el borrador y lo guarda con `POST /api/v1/training/evaluations`, como cualquier evaluación.

**Por qué es asíncrono.** Escribir de 3 a 10 preguntas puede tardar más que el tiempo que el API Gateway espera una respuesta. Por eso el `POST` solo inicia el trabajo y responde enseguida con 202; el front consulta el `GET` cada pocos segundos hasta que el borrador queda `LISTO` o `FALLIDO`.

```bash
curl -i -X POST http://localhost:8087/api/v1/training/evaluation-drafts \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <UUID_DEL_ADMIN>" -H "X-User-Role: ADMIN" -H "X-User-Permission: ms07:content:manage" \
  -d '{"categoryId":"00000000-0000-0000-0000-000000000005","questions":5,"topic":"Derrames"}'
# 202 {"id":"…","status":"EN_PROCESO"}

curl -i http://localhost:8087/api/v1/training/evaluation-drafts/<id> \
  -H "X-User-Id: <UUID_DEL_ADMIN>" -H "X-User-Role: ADMIN" -H "X-User-Permission: ms07:content:manage"
# 200 {"id":"…","status":"LISTO","draft":{"title":"…","topic":"…","categoryId":"…","questions":[{"statement":"…","options":[{"text":"…","correct":true}, …]}]}}
```

`questions` va de 3 a 10 (5 si no se envía) y `topic` es opcional. `draft` tiene exactamente la forma del cuerpo de `POST /evaluations`; si el estado es `FALLIDO`, `message` explica en español qué pasó.

| Respuesta | Cuándo |
|---|---|
| 202 | El borrador se está generando (`EN_PROCESO`) |
| 200 | Estado del borrador: `EN_PROCESO`, `LISTO` con `draft` o `FALLIDO` con `message` |
| 400 `VALIDACION` | Falta `categoryId`, `questions` fuera de 3 a 10 o `topic` de más de 150 caracteres |
| 401 / 403 | Faltan las cabeceras de identidad o el permiso `ms07:content:manage` |
| 404 `NO_ENCONTRADO` | La categoría no existe; o el borrador no existe, venció o lo pidió otro usuario |
| 422 `REGLA_NEGOCIO` | La categoría está inactiva o no tiene contenidos activos |
| 429 `DEMASIADAS_SOLICITUDES` | El ADMIN ya pidió 5 borradores en la última hora |
| 503 `DEPENDENCIA_NO_DISPONIBLE` | Ya hay demasiados borradores en cola; intente en unos minutos |

**Cómo funciona.**

1. MS07 valida la categoría, lee sus contenidos activos y responde 202. El trabajo sigue en un `ThreadPoolTaskExecutor` pequeño (2 hilos y 10 en cola); si está lleno responde 503.
2. El prompt usa solo esos contenidos, delimitados como datos con los mismos criterios contra inyección del tutor, y pide preguntas en español, claras, con una sola respuesta correcta y 3 o 4 opciones plausibles, sin «todas las anteriores», sin preguntas capciosas ni datos que no estén en los contenidos. Se pide la respuesta en modo JSON (`response_format: json_object`) con un tiempo (`LLM_GENERATION_TIMEOUT`) y un largo (`LLM_GENERATION_MAX_TOKENS`) propios; el tutor sigue con `LLM_TIMEOUT`.
3. La salida del modelo se valida antes de mostrarla: debe ser JSON válido; los textos se limpian (sin HTML, Markdown ni enlaces ajenos a los contenidos) y se recortan a los límites de la base (enunciado 500, opción 300, título y tema 150); cada pregunta debe tener al menos 2 opciones y exactamente una correcta, como exige `EvaluationService`; se descartan las preguntas inválidas o repetidas y las opciones del tipo «todas las anteriores». Si no queda ninguna, el borrador queda `FALLIDO`.
4. El borrador vive 30 minutos en memoria y solo lo ve quien lo pidió. Se pierde si MS07 se reinicia.
5. Cada ADMIN puede pedir 5 borradores por hora. Cada pedido queda en `audit_event` como `EVALUACION_GENERADA_IA`, con el usuario y la fecha y sin el contenido del borrador.

| Variable | Por defecto | Uso |
|---|---|---|
| `LLM_GENERATION_TIMEOUT` | `30s` | Tiempo máximo de espera del modelo al generar un borrador |
| `LLM_GENERATION_MAX_TOKENS` | `1800` | Largo máximo de la respuesta del modelo al generar un borrador |

## Despliegue en la nube (AWS)

MS07 corre en una instancia EC2 de AWS Academy, detrás del API Gateway, siguiendo la [conexión al API Gateway](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/integration/gateway-connection.md) y el laboratorio [CI/CD multinube](https://github.com/DOSW-2026-2/CI-CD-MULTICLOUD). En la VM hay dos stacks, `ms07-test` y `ms07-prod`, cada uno con su PostgreSQL, y un Caddy de borde que solo acepta al GW por mTLS. Flyway crea el esquema al arrancar.

| Tramo | Cómo |
|---|---|
| Cliente → GW | HTTPS + JWT. El GW pasa a MS07 `X-User-Id`, `X-User-Role` y `X-User-Permission` |
| GW → MS07 | HTTPS con mTLS a la Elastic IP de la VM: `:8443` prueba, `:443` producción |
| MS07 → MS08 (auditoría) | Por el GW (`GATEWAY_URL`), con `X-Service-Key` |

### Pipelines

| Workflow | Cuándo | Qué hace |
|---|---|---|
| `ci.yml` | Push y PR | Compila y prueba (`mvn verify`, reporte JaCoCo). Construye la imagen; desde `develop` y `main` la publica en GHCR como `sha-xxxxxxx`. Cada push a `develop` despliega en prueba |
| `deploy.yml` | Lo llaman los otros dos | En el runner `ms07` de la VM ejecuta `infra/deploy.sh`: escribe el `.env`, descarga la imagen y verifica |
| `deploy-prod.yml` | Manual desde `main` | Promueve a producción un tag `sha-xxxxxxx` que ya funcionó en prueba. Un tag anterior es el rollback |

`infra/deploy.sh` verifica dentro de la VM, por la red `campus-edge`, que el health responde 200, que una ruta protegida sin encabezados responde 401 y que el borde rechaza conexiones sin el certificado del GW.

### Variables y secretos (GitHub › Settings › Environments)

`ms07-test` (rama `develop`) y `ms07-prod` (rama `main`, con revisor requerido):

| Nombre | Tipo | Valor |
|---|---|---|
| `GATEWAY_URL` | Variable | Prueba: `https://campusseguro-gw.northcentralus.cloudapp.azure.com:8443`. Producción: `https://campusseguro-gw.northcentralus.cloudapp.azure.com` |
| `GATEWAY_SERVICE_KEY` | Secreto | La clave de servicio de ese entorno que entrega el equipo del GW |
| `DB_PASSWORD` | Secreto | `openssl rand -base64 24`, distinta por entorno |
| `LLM_API_KEY` | Secreto | Clave del proveedor del modelo |
| `LLM_PROVIDER`, `LLM_URL`, `LLM_MODEL`, `LLM_TIMEOUT`, `LLM_MAX_TOKENS`, `LLM_TEMPERATURE` | Variables opcionales | Sin valor usan los de `infra/docker-compose.prod.yml` (`openai`, `gpt-4o-mini`, `8000ms`, 250, 0.2) |
| `DEPLOY_DIR`, `MS07_EXTRA_ENV` | Variables opcionales | Carpeta del despliegue en la VM (por defecto `~/campusseguro/ms07`) y líneas `CLAVE=valor` extra |

Los certificados, los `.env` y las claves nunca van al repositorio.

### Preparar la VM (una vez)

1. EC2 Ubuntu Server 24.04 x64, `t3.medium`, 30 GiB, con una Elastic IP asociada. Security Group: `22/tcp` solo desde su IP; `443/tcp` y `8443/tcp` solo desde `135.232.229.138/32` (el GW). Nada más.
2. Docker: `curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker "$USER"`, y volver a entrar por SSH.
3. Borde Caddy con los certificados del GW en `~/campusseguro/edge/certs/{test,prod}/`:

   ```bash
   mkdir -p ~/campusseguro/edge && cd ~/campusseguro/edge
   curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/Caddyfile.microservicio
   curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/docker-compose.microservicio.yml
   printf 'UPSTREAM_PROD=ms07-prod:8087\nUPSTREAM_TEST=ms07-test:8087\n' > .env
   chmod 600 certs/*/servidor.key
   docker compose -f docker-compose.microservicio.yml up -d
   ```

4. Runner: *Settings › Actions › Runners › New self-hosted runner* (Linux x64), con la etiqueta `ms07`, instalado como servicio (`sudo ./svc.sh install && sudo ./svc.sh start`).

### Operación en la VM

```bash
cd ~/campusseguro/ms07/test                       # o prod
docker compose ps
docker compose logs --tail=100 ms07
grep '^TAG=' .env                                 # versión desplegada, sin mostrar secretos
docker compose restart ms07                       # reinicia MS07 sin tocar la BD
docker run --rm --network campus-edge curlimages/curl:8.11.1 -s http://ms07-test:8087/actuator/health
```

Nunca use `docker compose down -v`: borra el volumen de la BD. Revertir la imagen no revierte las migraciones de Flyway.

AWS Academy detiene la instancia cuando termina la sesión del Learner Lab. Al volver a iniciarla, Docker levanta los contenedores (`restart: unless-stopped`) y el runner vuelve a conectarse. La Elastic IP no cambia, pero se cobra aunque la instancia esté detenida: al eliminar la VM, primero *Disassociate* y luego *Release*.
