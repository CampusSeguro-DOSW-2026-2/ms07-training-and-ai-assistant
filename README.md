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

| Método | Endpoint | HU / RF |
|---|---|---|
| GET | `/api/v1/contents?category=` | HU-3a / RF-09 |
| GET | `/api/v1/contents/{id}` | HU-3a / RF-09 |
| POST | `/api/v1/contents/{id}/progress` | HU-3a / RF-10 |
| GET | `/api/v1/users/{userId}/progress` | HU-3a / RF-10 |
| GET | `/api/v1/evaluations?category=` | HU-3b / RF-11 |
| POST | `/api/v1/evaluations/{id}/submit` | HU-3b / RF-11 |
| GET | `/api/v1/users/{userId}/evaluation-results` | HU-3b / RF-11 |


