# PRD – Calculadora de ITEC

## Incremento 1: Motor de cálculo de distancias y combustible (consola)

**Equipo ZON:** Mateo Del Lungo · Tomas Aparicio · Lucila Olguin **Versión:** 0.1 (borrador) · **Fecha:** 22/11/2026 · **Estado:** En revisión por el equipo

---

## 0. Cómo leer este documento

Este PRD define **qué** debe hacer el sistema y **por qué**. No define la arquitectura interna, el modelo de datos ni el diseño de pantallas: eso corresponde al diseño técnico (LLD) y a los ADR.

- Cada requerimiento tiene un ID estable con formato **RF-ÁREA-NN** (funcional) o **RNF-NN** (no funcional), para trazar hacia backlog, código y pruebas.
- Este es un **documento vivo**. Se arranca con el Incremento 1 y se extiende con cada incremento nuevo. Los puntos sin resolver están marcados como **\[ABIERTO\]** y se consolidan en la Sección 12.
- Las decisiones técnicas ya tomadas (ADR 0001 y 0002) se listan como restricciones en la Sección 9, no se rediscuten acá.

### Control de versiones

| Versión | Fecha | Autor | Cambio |
| --- | --- | --- | --- |
| 0.1 | 22/11/2026 | Equipo ZON | Borrador inicial del Incremento 1 |

---

## 1. Visión del producto

**Calculadora de ITEC** es un sistema que toma datos de encuestas (planillas `.csv` o `.xlsx`) con origen y destino de desplazamientos, calcula la **distancia recorrida en kilómetros** con un motor de mapas propio y, a partir de esa distancia, estima el **gasto de combustible**.

### Problema que resuelve

Hoy los datos de desplazamiento llegan en planillas heterogéneas, con lugares escritos en texto libre. Calcular las distancias y el consumo de combustible a mano es lento, inconsistente y no escala. Se necesita un procesamiento automático, repetible y auditable.

### Principio de diseño del producto

El **motor de cálculo es el producto**; las interfaces (consola, API, escritorio, web) son capas que lo consumen. Por eso el Incremento 1 entrega únicamente el motor, desacoplado de cualquier interfaz.

---

## 2. Objetivos y criterios de éxito del Incremento 1

| ID | Objetivo | Medición | Target |
| --- | --- | --- | --- |
| OBJ-01 | Procesar una encuesta completa de punta a punta sin intervención manual | Archivo de entrada → archivo de reporte generado | 100% de las filas válidas procesadas |
| OBJ-02 | Distancias calculadas con motor propio, sin depender de APIs de terceros con cuota | Cero llamadas a servicios externos pagos o con rate limit | 0 dependencias externas de mapas |
| OBJ-03 | Paridad entre formatos | Mismo dataset en `.csv` y `.xlsx` | Resultados idénticos |
| OBJ-04 | Trazabilidad de errores | Filas no procesables reportadas con motivo | 100% de las filas fallidas con causa explícita |
| OBJ-05 | Motor reutilizable por futuras interfaces | El núcleo no importa nada de consola/UI | El CLI es una capa fina sobre el motor |
| OBJ-06 | Rendimiento en volumen | Procesar un archivo de 500.000 filas (carga de lectura/escritura) sin degradación de memoria | Sin `OutOfMemoryError` |

> **\[ABIERTO\]** Los targets de OBJ-06 (volumen real esperado de encuestas) y tiempo máximo de procesamiento por fila se ajustan cuando tengamos un dataset real.

---

## 3. Alcance y fasificación

| Incremento | Contenido |
| --- | --- |
| **Incremento 1 (este documento)** | Motor de cálculo en Java con interfaz de consola (CLI): lectura de planillas, geocodificación, cálculo de distancia, cálculo de combustible, generación de reporte |
| **Incremento 2** | Exposición del motor como servicio API (REST) y/o aplicación de escritorio, reutilizando el motor sin modificarlo |
| **Incremento 3+** | Interfaz final (web/escritorio), generación de plantillas de encuesta, mejoras de precisión y funcionalidades a definir |

### Dentro del alcance del Incremento 1

- Ingesta de planillas `.csv` y `.xlsx` con columnas dinámicas.
- Geocodificación de lugares a coordenadas.
- Cálculo de distancia en línea recta y por ruta.
- Cálculo de litros consumidos y costo de combustible.
- Reporte de salida en `.csv` o `.xlsx`.
- Interfaz de consola para ejecutar el motor.

### Fuera de alcance del Incremento 1

Ver Sección 10.

---

## 4. Actores

| Actor | Descripción | Interacción en el Incremento 1 |
| --- | --- | --- |
| **Operador** | Miembro del equipo o usuario técnico que corre el sistema | Ejecuta el CLI con un archivo de entrada y parámetros, y recibe el reporte |
| **Sistema consumidor (futuro)** | API, app desktop u otro cliente | No existe aún; el motor debe estar preparado para ser invocado por él (Incremento 2) |

> **\[ABIERTO\]** Confirmar si hay otros actores (por ejemplo, quien carga las encuestas vs. quien interpreta el reporte).

---

## 5. Flujo general del sistema

1. **Ingesta:** se lee la planilla de encuesta (`.csv` o `.xlsx`).
2. **Normalización:** se identifican las columnas relevantes (origen, destino, vehículo, etc.).
3. **Geocodificación:** cada lugar de texto se convierte en coordenadas.
4. **Cálculo de distancia:** se obtiene la distancia en km (línea recta o por ruta).
5. **Cálculo de combustible:** a partir de los km y los parámetros del vehículo se calculan litros y costo.
6. **Reporte:** se escribe una planilla de resultados con los datos originales, las distancias y los consumos, más el detalle de filas con error.

---

## 6. Requerimientos funcionales

### 6.1 Ingesta de datos (ING)

- **RF-ING-01:** El sistema debe leer planillas en formato `.csv` y `.xlsx`, determinando el formato por la extensión del archivo.
- **RF-ING-02:** El sistema debe soportar **estructuras de columnas dinámicas**: no puede depender de una clase o esquema fijo compilado. La estructura se descubre a partir de la cabecera.
- **RF-ING-03:** El Operador debe poder indicar qué columnas contienen origen, destino y los demás datos necesarios para el cálculo, mediante un archivo de configuración o parámetros. *Motivo: las encuestas pueden venir de distintas plataformas con nombres de columna distintos.*
- **RF-ING-04:** El sistema debe procesar el archivo en **streaming** (fila a fila), sin cargarlo completo en memoria.
- **RF-ING-05:** El sistema debe tolerar filas con datos faltantes o inválidos: la fila se marca con error y el procesamiento continúa con el resto. *Una fila mala nunca debe frenar el lote.*
- **RF-ING-06:** El sistema debe manejar correctamente codificación de caracteres (UTF-8, acentos y eñes) y los delimitadores habituales del CSV (`,` y `;`).
- **RF-ING-07:** El sistema debe poder leer tanto desde una ruta de archivo como desde un flujo de entrada (`InputStream`), para no acoplarse al entorno de ejecución.

### 6.2 Geocodificación (GEO)

- **RF-GEO-01:** El sistema debe convertir un lugar dado como texto (dirección, localidad, punto de interés) en coordenadas geográficas (latitud y longitud).
- **RF-GEO-02:** Cuando un texto produzca múltiples resultados, el sistema debe aplicar una regla de selección determinística y registrar qué resultado eligió. **\[ABIERTO\]** definir la regla (primer resultado, mayor confianza, sesgo por zona geográfica).
- **RF-GEO-03:** Cuando un lugar no pueda geocodificarse, la fila se marca con error `LUGAR_NO_ENCONTRADO` indicando cuál de los dos puntos falló.
- **RF-GEO-04:** El sistema debe poder sesgar la búsqueda hacia una región por defecto configurable (ej. Córdoba, Argentina) para reducir ambigüedades.
- **RF-GEO-05:** El sistema debe evitar geocodificar repetidamente el mismo texto en una misma ejecución (reutilizar resultados ya obtenidos).

### 6.3 Cálculo de distancia (DIS)

- **RF-DIS-01:** El sistema debe calcular la distancia en **línea recta** entre dos coordenadas (fórmula de Haversine), en kilómetros.
- **RF-DIS-02:** El sistema debe calcular la distancia **por ruta real** (calles) entre dos coordenadas, en kilómetros, considerando el tipo de vehículo/perfil de desplazamiento.
- **RF-DIS-03:** El Operador debe poder elegir el modo de cálculo (línea recta, por ruta, o ambos). **\[ABIERTO\]** definir el modo por defecto.
- **RF-DIS-04:** Si el cálculo por ruta falla (ej. no hay ruta entre dos puntos), el sistema debe registrar el error en esa fila y, opcionalmente según configuración, informar la distancia en línea recta como referencia, **identificándola claramente como tal**. *Motivo: nunca mezclar silenciosamente un valor aproximado con uno preciso.*
- **RF-DIS-05:** La distancia debe expresarse en kilómetros con una precisión configurable (por defecto, 2 decimales).
- **RF-DIS-06:** El sistema debe contemplar si el trayecto es solo de ida o de **ida y vuelta**, y reflejarlo en el cálculo. **\[ABIERTO\]** confirmar si esto viene en la encuesta o es un parámetro global.

### 6.4 Cálculo de combustible (COM)

- **RF-COM-01:** El sistema debe calcular los **litros consumidos** a partir de la distancia y del consumo del vehículo.
  - `litros = km × (consumo en l/100 km) / 100`
- **RF-COM-02:** El sistema debe calcular el **costo de combustible**.
  - `costo = litros × precio por litro`
- **RF-COM-03:** El consumo del vehículo y el precio por litro deben ser **parámetros configurables** (no constantes de código), con posibilidad de definirlos por tipo de combustible (nafta, gasoil, GNC) o por vehículo.
- **RF-COM-04:** Si la encuesta provee datos del vehículo o del combustible, el sistema debe usarlos; si no, debe aplicar los valores por defecto configurados. **\[ABIERTO\]** definir qué datos trae la encuesta.
- **RF-COM-05:** Para modos de desplazamiento sin combustible (a pie, bicicleta, transporte eléctrico), el sistema debe informar distancia pero dejar consumo y costo en cero o vacío, según definición. **\[ABIERTO\]**
- **RF-COM-06:** El sistema debe registrar en el reporte qué parámetros de consumo y precio se usaron en cada cálculo. *Motivo: el costo depende del precio vigente; sin este registro el resultado no es auditable.*
- **RF-COM-07:** La moneda y la unidad de precio deben ser explícitas y configurables. **\[ABIERTO\]** confirmar moneda (ARS u otra) y fuente del precio por litro.

### 6.5 Reporte de salida (REP)

- **RF-REP-01:** El sistema debe generar una planilla de resultados en `.csv` o `.xlsx`, a elección del Operador.
- **RF-REP-02:** Cada fila del reporte debe incluir los datos de entrada relevantes, las coordenadas obtenidas, la distancia en km (con el modo usado), los litros, el costo y el estado de la fila (`OK` o código de error).
- **RF-REP-03:** El sistema debe generar un **resumen agregado** al final de la ejecución: total de filas, procesadas OK, con error, km totales, litros totales y costo total.
- **RF-REP-04:** Las filas con error deben quedar identificadas en el reporte con su motivo, sin perderse ni omitirse.
- **RF-REP-05:** El sistema debe poder escribir hacia una ruta de archivo o hacia un flujo de salida (`OutputStream`).
- **RF-REP-06:** El formato de salida debe ser independiente del de entrada (se puede leer `.xlsx` y escribir `.csv`, y viceversa).

### 6.6 Interfaz de consola (CLI)

- **RF-CLI-01:** El sistema debe ejecutarse por línea de comandos indicando como mínimo: archivo de entrada, archivo de salida y configuración.
- **RF-CLI-02:** El CLI debe mostrar el progreso de la ejecución y un resumen final.
- **RF-CLI-03:** El CLI debe devolver códigos de salida diferenciados (éxito, éxito con filas con error, error fatal) para poder usarse en scripts.
- **RF-CLI-04:** El CLI debe ser una capa **fina**: toda la lógica de cálculo vive en el motor. *Motivo: en el Incremento 2 la API o la app de escritorio consumirán el mismo motor sin duplicar lógica.*
- **RF-CLI-05:** El CLI debe incluir ayuda (`--help`) con la descripción de parámetros.

### 6.7 Desacople del motor (MOT)

- **RF-MOT-01:** El motor debe exponer una **interfaz programática única** de entrada (archivo/flujo + configuración) y de salida (reporte + resumen), sin dependencia de consola, HTTP ni UI.
- **RF-MOT-02:** El motor no debe leer entrada estándar ni imprimir en pantalla; informa su estado mediante eventos o callbacks que la interfaz decide cómo mostrar.
- **RF-MOT-03:** El motor debe poder invocarse varias veces en un mismo proceso (requisito previo para el modo servicio del Incremento 2).

---

## 7. Requerimientos no funcionales

- **RNF-01 – Costo operativo cero:** el cálculo de distancias no debe depender de APIs pagas ni con límite de peticiones.
- **RNF-02 – Memoria:** procesamiento en streaming; el consumo de memoria no debe crecer proporcionalmente al tamaño del archivo.
- **RNF-03 – Rendimiento:** debe poder procesar archivos de al menos 500.000 filas de lectura/escritura sin degradación de memoria. El tiempo máximo por fila para geocodificación y ruta queda **\[ABIERTO\]** hasta medir en la prueba de concepto.
- **RNF-04 – Determinismo y reproducibilidad:** la misma entrada con la misma configuración y los mismos datos de mapa debe producir el mismo resultado.
- **RNF-05 – Portabilidad:** debe ejecutarse sin acoplarse a un sistema operativo específico, en modo offline cuando los servicios de mapas sean locales.
- **RNF-06 – Robustez:** ninguna fila inválida debe detener el procesamiento del lote; los errores se reportan, no se ocultan.
- **RNF-07 – Mantenibilidad / extensibilidad:** el formato de archivo, el proveedor de geocodificación y el proveedor de rutas deben poder cambiarse sin modificar la lógica de cálculo.
- **RNF-08 – Privacidad:** los datos de la encuesta (lugares, posibles datos personales) se procesan localmente y no se envían a terceros. **\[ABIERTO\]** confirmar si las encuestas contienen datos personales.
- **RNF-09 – Configuración externa:** parámetros (consumo, precios, modo de cálculo, columnas, región por defecto) fuera del código.
- **RNF-10 – Observabilidad:** registro (log) de la ejecución con nivel configurable, sin ensuciar la salida del reporte.
- **RNF-11 – Idioma:** interfaz y mensajes en español.

---

## 8. Modelo conceptual de datos (alto nivel)

Solo se nombran los conceptos del dominio; el modelo detallado va al diseño técnico.

| Concepto | Descripción |
| --- | --- |
| **Encuesta (planilla de entrada)** | Archivo `.csv` / `.xlsx` con una respuesta por fila |
| **Respuesta / Trayecto** | Fila con origen, destino y datos asociados |
| **Lugar** | Texto de ubicación + coordenadas resueltas |
| **Distancia** | Valor en km + modo de cálculo (recta / ruta) |
| **Perfil de consumo** | Consumo (l/100 km) y tipo de combustible |
| **Precio de combustible** | Precio por litro, moneda y fecha de referencia |
| **Resultado de fila** | Distancia, litros, costo y estado (OK / código de error) |
| **Reporte** | Planilla de salida + resumen agregado |

---

## 9. Restricciones y decisiones heredadas

Decisiones ya aceptadas por el equipo (fecha 22/10/2026) que condicionan este incremento:

| Decisión | Resumen | Impacto en este PRD |
| --- | --- | --- |
| **ADR 0001** – Photon + OSRM self-hosted | Geocodificación con Photon y cálculo de rutas con OSRM, ambos autoalojados, sobre datos de OpenStreetMap. Cálculo en línea recta vía Haversine | Habilita RF-GEO y RF-DIS; sustenta RNF-01. El equipo asume la administración de los servicios y la actualización periódica de los mapas |
| **ADR 0002** – FastExcel + FastCSV | Lectura/escritura de `.xlsx` y `.csv` en streaming, sin DTOs fijos, con una interfaz común (`SpreadsheetEngine`) | Habilita RF-ING y RF-REP; sustenta RNF-02 y RNF-03 |
| **Lenguaje** | Java | Aplica a todo el motor |

---

## 10. Fuera de alcance del Incremento 1

Para que nadie asuma que algo entra «porque el documento no dice que no»:

| Fuera de alcance | Destino |
| --- | --- |
| API REST | Incremento 2 |
| Aplicación de escritorio | Incremento 2 |
| Interfaz web | Incremento 3+ |
| Generación de la planilla plantilla de encuesta (el ADR 0002 contempla este paso) | A definir |
| Autocompletado predictivo de lugares | A definir |
| Soporte de `.xls` (Excel 97-2003) | Descartado (ADR 0002) |
| Autenticación, usuarios y roles | A definir con la API |
| Persistencia en base de datos / historial de ejecuciones | A definir |
| Costos adicionales (peajes, estacionamiento, desgaste del vehículo) | A definir |
| Actualización automática de mapas OSM y precios de combustible | A definir |
| Cálculo de emisiones de CO₂ | A definir |

---

## 11. Criterios de aceptación del Incremento 1 (Definition of Done)

1. El CLI procesa un `.csv` de prueba y genera un reporte con distancia, litros y costo por fila (RF-ING-01, RF-REP-01, RF-CLI-01).
2. El CLI procesa el mismo dataset en `.xlsx` y el resultado es **idéntico** al del `.csv` (OBJ-03).
3. Las filas con lugar no encontrado, datos faltantes o ruta inexistente aparecen en el reporte con su motivo y no interrumpen el lote (RF-ING-05, RF-GEO-03, RF-REP-04).
4. Verificación manual de una muestra de distancias contra una fuente de referencia: desvío aceptable **\[ABIERTO: definir tolerancia\]**.
5. El cálculo de combustible coincide con un caso calculado a mano (RF-COM-01, RF-COM-02).
6. El modo línea recta y el modo por ruta devuelven resultados coherentes (ruta ≥ línea recta) (RF-DIS-01/02).
7. El resumen final muestra totales correctos (RF-REP-03).
8. Prueba de carga con un archivo de 500.000 filas sin degradación de memoria (RNF-02/03).
9. El núcleo del motor no tiene dependencias hacia el CLI; se demuestra invocándolo desde una prueba automatizada (RF-MOT-01/02).
10. Dependencias verificadas: solo las definidas en los ADR para planillas (`fastexcel`, `fastexcel-reader`, `fastcsv`).
11. Documentación de uso del CLI y de la configuración disponible en el repositorio.

---

## 12. Supuestos y preguntas abiertas

### Supuestos

- Los servicios Photon y OSRM estarán disponibles localmente (contenedores) durante la ejecución.
- Las planillas de encuesta traen una fila por trayecto y los lugares como texto.
- El equipo es el único usuario del CLI en este incremento.

### Preguntas abiertas

| ID | Pregunta | Quién decide |
| --- | --- | --- |
| Q-01 | ¿Qué significa «ITEC» en el contexto del proyecto y quién es el cliente o usuario final? | Equipo ZON |
| Q-02 | ¿Qué columnas trae la encuesta? ¿El origen y destino son direcciones, localidades o puntos de interés? | Equipo ZON |
| Q-03 | ¿La encuesta indica el vehículo, el combustible o el consumo, o se aplican valores globales? | Equipo ZON |
| Q-04 | ¿Los trayectos son de ida, de ida y vuelta, o con frecuencia (veces por semana/mes)? ¿Se calcula el total periódico? | Equipo ZON |
| Q-05 | ¿Qué modo de distancia es el principal (ruta o línea recta) y cuál es la tolerancia de error aceptable? | Equipo ZON |
| Q-06 | ¿De dónde sale el precio por litro y cómo se actualiza? ¿En qué moneda? | Equipo ZON |
| Q-07 | ¿Qué hacer con trayectos en modos sin combustible (a pie, bici, transporte público)? | Equipo ZON |
| Q-08 | ¿Las encuestas contienen datos personales? ¿Hay requisitos de privacidad? | Equipo ZON |
| Q-09 | ¿Qué región geográfica cubren las encuestas (solo Córdoba o más amplio)? Afecta el tamaño del mapa OSM a cargar | Equipo ZON |
| Q-10 | ¿Qué volumen real de filas se espera por encuesta? | Equipo ZON |

---

## 13. Registro de riesgos

| ID | Riesgo | Impacto | Prob. | Mitigación |
| --- | --- | --- | --- | --- |
| RSK-01 | Ambigüedad en los textos de lugar produce geocodificaciones erróneas (ej. mismo nombre en distintas ciudades) | Alto | Alta | Sesgo regional (RF-GEO-04), registro del resultado elegido (RF-GEO-02), reporte de confianza |
| RSK-02 | Calidad y vigencia de los datos de OpenStreetMap en la zona (calles nuevas o faltantes) | Medio | Media | Verificación con muestra; actualización periódica de mapas (ADR 0001) |
| RSK-03 | Carga operativa de mantener Photon y OSRM (infraestructura, memoria, espacio en disco) | Medio | Media | Prueba de concepto temprana; documentar el levantamiento de los servicios |
| RSK-04 | Precio y consumo desactualizados generan costos poco representativos | Medio | Alta | Parámetros externos, versionados y registrados en cada reporte (RF-COM-06) |
| RSK-05 | Estructuras de encuesta muy variables rompen el mapeo de columnas | Medio | Media | Configuración de columnas (RF-ING-03), validación previa de cabecera |
| RSK-06 | Acoplar lógica de negocio al CLI dificulta el Incremento 2 | Alto | Media | RF-CLI-04 y RF-MOT-01/02; prueba automatizada contra el motor |
| RSK-07 | Archivos grandes agotan memoria | Alto | Baja | Streaming obligatorio (RF-ING-04) y prueba de carga (DoD 8) |

---

## 14. Glosario

- **Geocodificación:** conversión de un texto de lugar en coordenadas (latitud, longitud).
- **Haversine:** fórmula para calcular la distancia en línea recta entre dos puntos de la superficie terrestre.
- **Ruta (routing):** distancia recorrida por la red de calles real entre dos puntos.
- **Photon:** servicio de geocodificación basado en OpenStreetMap (ADR 0001).
- **OSRM:** motor de cálculo de rutas basado en OpenStreetMap (ADR 0001).
- **Streaming:** procesamiento fila a fila sin cargar el archivo completo en memoria.
- **CSV / XLSX:** formatos de planilla de texto plano y de Excel, respectivamente.
- **CLI:** interfaz de línea de comandos.
- **Motor:** núcleo de cálculo del sistema, independiente de la interfaz.
- **l/100 km:** litros consumidos cada 100 kilómetros.
