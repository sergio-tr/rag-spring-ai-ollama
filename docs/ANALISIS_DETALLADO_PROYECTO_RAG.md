# Análisis Detallado del Proyecto RAG Spring AI Ollama

**Última actualización:** Febrero 2025

> Nota de mantenimiento 2026-05: este análisis describe una superficie histórica del prototipo. Para evidencia final del producto, usar rutas product-scoped (`/api/v5/...`), Chat por conversaciones/jobs y LAB benchmarks/exportaciones. El endpoint legacy `/query` solo es válido como comparación histórica si se etiqueta explícitamente como legacy.

## Descripción General del Proyecto

Este proyecto implementa un sistema RAG (Retrieval-Augmented Generation) basado en Spring Boot que utiliza Ollama como proveedor de modelos de lenguaje local. El sistema está diseñado para procesar y consultar documentos, especialmente actas de reuniones, utilizando técnicas avanzadas de procesamiento de lenguaje natural y búsqueda vectorial. La evaluación con herramientas y metadatos (v4) muestra Correctness ≥ 4 en todos los ítems del dataset.

## Estructura del Proyecto

### 📁 **src/main/java/com/uniovi/rag/**

#### 🎯 **controllers/** - Controladores de la API REST

##### **RagController.java** - Controlador Principal del Sistema RAG
- **Endpoint base**: `/api/v5`
- **Funcionalidades principales**:
  - `POST /documents`: Subida y procesamiento de documentos
  - `GET /query`: consulta legacy/histórica del sistema RAG; no usar como evidencia final de Chat productivo
  - `GET /evaluate`: Evaluación del rendimiento del sistema (configuración por defecto)
  - `POST /evaluate/custom`: Evaluación con configuración personalizada (body: `expansion`, `ner`, `tools`, `metadata`)
  - `GET /evaluate/all`: Evaluación de las 16 combinaciones de configuración
  - `DELETE /documents`: Vacía la base de datos de documentos
- **Dependencias**: DocumentService, QueryService, EvaluationService
- **Propósito**: Punto de entrada principal para operaciones del sistema RAG

#### ⚙️ **configuration/** - Configuración del Sistema

##### **RagConfiguration.java** - Configuración Principal del Sistema
- **Bean de configuración principal** que orquesta todos los componentes
- **Configuración de Ollama**:
  - Modelo de chat configurable
  - Modelo de embeddings configurable
  - Configuración de URL y parámetros
- **Configuración de vector store**: PgVectorStore con PostgreSQL
- **Configuración de servicios**:
  - DocumentService (Simple o Metadata según configuración)
  - EvaluationService
  - QueryExpander
  - QueryClassifier
  - QueryAnalyser
  - ContextRetriever
  - Tools (herramientas estándar o de metadatos)
  - AgenticToolsManager
- **Sistema de herramientas dinámico**: Selecciona entre herramientas estándar y de metadatos según configuración

##### **RagFeatureConfiguration.java** - Configuración de Características
- **Flags de activación** (prefix `rag.features` en application.properties):
  - `expansionEnabled`: Expansión de consultas
  - `nerEnabled`: Reconocimiento de entidades nombradas
  - `toolsEnabled`: Sistema de herramientas
  - `metadataEnabled`: Procesamiento de metadatos (herramientas Metadata*)
- **Método getConfiguration()** devuelve mapa con claves: expansion, ner, tools, metadata

##### **RagToolsConfiguration.java** - Configuración de Herramientas
- **Wrapper** para el mapa de herramientas disponibles
- **Método getTool(QueryType)** para obtener herramienta específica
- **Integración** con el sistema de clasificación de consultas

#### 🛡️ **services/guard/** - Guardas de Consulta
- **DateExistenceGuard**: Comprueba si existe acta para la fecha de la consulta antes de invocar herramientas que dependen de fecha (GET_FIELD, GET_DURATION, DECISION_EXTRACTION, COUNT_DOCUMENTS). Si no hay documento para esa fecha, devuelve respuesta estándar "No hay ninguna acta registrada en esa fecha" sin llamar a la herramienta.
- **QueryDateExtractor**: Extrae y normaliza fechas de la consulta para uso del guard.
- **Integración**: ProcessQueryService y EvaluationServiceFactory usan DateExistenceGuard cuando está habilitado el flujo con herramientas/metadatos.

#### 🏗️ **model/** - Modelos de Datos

##### **Minute.java** - Modelo de Acta de Reunión
- **Record Java** que representa una acta completa
- **Campos principales**:
  - `id`, `filename`: Identificación del documento
  - `date`, `place`, `startTime`, `endTime`: Información temporal y espacial
  - `president`, `secretary`: Roles de la reunión
  - `attendees`, `numberOfAttendees`: Lista y conteo de asistentes
  - `agenda`: Mapa de puntos del orden del día
  - `decisions`: Lista de acuerdos explícitos
  - `mentionedEntities`: Entidades mencionadas (empresas, organismos)
  - `topics`: Temas tratados en la reunión
  - `summary`: Resumen completo del acta
- **Propósito**: Estructura de datos para procesamiento y análisis de actas

##### **Loggable.java** - Interfaz de Logging
- **Interfaz funcional** que proporciona logging automático
- **Implementación por defecto** que crea un logger específico para cada clase
- **Uso**: Todas las interfaces principales extienden de Loggable

#### 🔧 **services/** - Servicios del Sistema

##### **query/** - Servicios de Consulta

###### **QueryService.java** - Interfaz Principal de Consulta
- **Interfaz base** para todos los servicios de consulta
- **Método principal**: `generateResponse(String question)`
- **Extiende de Loggable** para logging automático

###### **AgenticQueryService.java** - Servicio de Consulta Agentic
- **Implementación avanzada** del sistema de consultas
- **Características**:
  - Expansión de consultas
  - Análisis NER
  - Clasificación de consultas
  - Sistema de herramientas inteligente
  - Gestión de herramientas agentic
- **Integración** con todas las funcionalidades del sistema

###### **SimpleQueryService.java** - Servicio de Consulta Simple
- **Implementación básica** para consultas simples
- **Funcionalidad limitada** sin expansión ni herramientas avanzadas

###### **ProcessQueryService.java** - Servicio de Procesamiento de Consultas
- **Interfaz** para servicios que procesan consultas paso a paso
- **Preparado para** implementaciones de procesamiento complejo

###### **SimpleProcessQueryService.java** - Implementación Simple de Procesamiento
- **Implementación básica** del procesamiento de consultas

##### **tools/** - Sistema de Herramientas

###### **Tool.java** - Interfaz Base de Herramientas
- **Interfaz principal** para todas las herramientas del sistema
- **Método**: `execute(ToolExecutionContext context)`
- **Extiende de Loggable**

###### **AbstractTool.java** - Clase Abstracta de Herramientas
- **Implementación base** para todas las herramientas
- **Funcionalidades comunes**:
  - Recuperación de documentos con diferentes configuraciones
  - Acceso a ChatClient y ContextRetriever
  - Métodos de recuperación configurable (topK, threshold)
- **Métodos de recuperación**:
  - `retrieveAllDocuments()`: Recupera todos los documentos
  - `retrieveDocuments()`: Recupera con configuración por defecto
  - `retrieveDocumentsWithTopK()`: Recupera con topK específico

###### **ToolResult.java** - Resultado de Ejecución de Herramienta
- **Clase de resultado** para operaciones de herramientas

###### **ToolExecutionContext.java** - Contexto de Ejecución
- **Contexto** que se pasa a las herramientas durante la ejecución

###### **Herramientas Específicas**:
- **CountDocumentsTool**: Cuenta documentos que cumplen criterios
- **FindParagraphTool**: Encuentra párrafos específicos
- **CountAndExplainTool**: Cuenta y explica resultados
- **ExtractEntitiesTool**: Extrae entidades nombradas
- **SummarizeTopicTool**: Resume temas específicos
- **SummarizeMeetingTool**: Resume reuniones completas
- **BooleanQueryTool**: Consultas de tipo booleano
- **CompareTool**: Compara diferentes aspectos
- **GetDurationTool**: Obtiene duración de reuniones
- **GetFieldTool**: Extrae campos específicos
- **FilterAndListTool**: Filtra y lista resultados
- **DecisionExtractionTool**: Extrae decisiones de reuniones

###### **metadata/** - Herramientas de Metadatos
- **AbstractMetadataTool.java**: Clase base con recuperación por metadatos, filtrado por tema/fecha, extracción de `Minute` y lógica compartida (normalización, extracción de tema).
- **Implementaciones**: MetadataCountDocumentsTool, MetadataFindParagraphTool, MetadataSummarizeTopicTool, MetadataSummarizeMeetingTool, MetadataBooleanQueryTool, MetadataCompareTool, MetadataGetDurationTool, MetadataGetFieldTool, MetadataFilterAndListTool, MetadataDecisionExtractionTool, MetadataExtractEntitiesTool, MetadataCountAndExplainTool.
- **Ventajas**: Uso de metadatos estructurados (Minute), filtros por tema/fecha, fallbacks literales y por sinónimos; evaluación v4 con tools+metadata alcanza Correctness ≥ 4 en todos los ítems.

##### **classifier/** - Clasificación de Consultas

###### **QueryType.java** - Tipos de Consulta
- **Enum** que define todos los tipos de consulta soportados:
  - `COUNT_DOCUMENTS`: Contar documentos
  - `EXTRACT_ENTITIES`: Extraer entidades
  - `COUNT_AND_EXPLAIN`: Contar y explicar
  - `FIND_PARAGRAPH`: Encontrar párrafos
  - `DECISION_EXTRACTION`: Extraer decisiones
  - `GET_DURATION`: Obtener duración
  - `GET_FIELD`: Obtener campos
  - `SUMMARIZE_TOPIC`: Resumir temas
  - `SUMMARIZE_MEETING`: Resumir reuniones
  - `BOOLEAN_QUERY`: Consultas booleanas
  - `FILTER_AND_LIST`: Filtrar y listar
  - `COMPARE`: Comparar

###### **QueryClassifier.java** - Interfaz de Clasificación
- **Interfaz base** para clasificar consultas por tipo

###### **EnhancedQueryClassifier.java** - Clasificador Mejorado
- **Implementación avanzada** que combina múltiples estrategias
- **Integración** con PythonQueryClassifier y ChatClient

###### **PythonQueryClassifier.java** - Clasificador en Python
- **Clasificador externo** implementado en Python
- **Integración** con el sistema Java

##### **analyser/** - Análisis de Consultas

###### **QueryAnalyser.java** - Interfaz de Análisis
- **Interfaz base** para analizar consultas

###### **NERQueryAnalyser.java** - Analizador NER
- **Implementación** de reconocimiento de entidades nombradas
- **Uso del ChatClient** para análisis avanzado

##### **expand/** - Expansión de Consultas

###### **QueryExpander.java** - Interfaz de Expansión
- **Interfaz base** para expandir consultas

###### **AbstractQueryExpander.java** - Clase Abstracta de Expansión
- **Implementación base** para expansores de consultas

###### **DocumentStructureExpander.java** - Expansor de Estructura de Documentos
- **Expansión basada** en la estructura de los documentos
- **Uso del ChatClient** para expansión inteligente

##### **retriever/** - Recuperación de Contexto

###### **ContextRetriever.java** - Interfaz de Recuperación
- **Interfaz principal** para recuperación de contexto
- **Métodos**:
  - `retrieve(String query)`: Recupera documentos relevantes
  - `setTopK(int topK)`: Configura número máximo de resultados
  - `setSimilarityThreshold(double threshold)`: Configura umbral de similitud
  - `restoreDefaultSettings()`: Restaura configuración por defecto
  - `createContext()`: Crea contexto a partir de documentos

###### **AbstractContextRetriever.java** - Clase Abstracta de Recuperación
- **Implementación base** con funcionalidades comunes

###### **BasicContextRetriever.java** - Recuperador Básico
- **Implementación simple** de recuperación de contexto

###### **FilteredContextRetriever.java** - Recuperador Filtrado
- **Recuperación con filtros** adicionales

###### **DocumentContextRetriever.java** - Recuperador de Contexto de Documentos
- **Recuperación especializada** para documentos

##### **document/** - Servicios de Documentos

###### **DocumentService.java** - Interfaz de Servicio de Documentos
- **Interfaz base** para procesamiento de documentos
- **Métodos**:
  - `processDocument(MultipartFile file)`: Procesa archivo subido
  - `add(List<Document> documents)`: Añade documentos al sistema

###### **AbstractDocumentService.java** - Clase Abstracta de Documentos
- **Implementación base** con funcionalidades comunes

###### **SimpleDocumentService.java** - Servicio Simple de Documentos
- **Implementación básica** sin metadatos

###### **AbstractMetadataDocumentService.java** - Clase Abstracta de Metadatos
- **Base para servicios** que procesan metadatos

###### **MetadataDocumentService.java** - Servicio de Metadatos
- **Implementación avanzada** con procesamiento de metadatos
- **Extracción automática** de información estructurada

##### **evaluation/** - Evaluación del Sistema

###### **EvaluationService.java** - Interfaz de Evaluación
- **Interfaz base** para evaluar el rendimiento del sistema
- **Métodos**:
  - `evaluate()`: Ejecuta evaluación completa
  - `loadData()`: Carga datos de evaluación
  - `getQuestionsAndAnswers()`: Obtiene preguntas y respuestas de referencia

###### **AbstractEvaluationService.java** - Clase Abstracta de Evaluación
- **Implementación base** con funcionalidades comunes

###### **AbstractMinuteEvaluationService.java** - Evaluación de Actas
- **Base para servicios** que evalúan específicamente actas

###### **SimpleMinuteEvaluationService.java** - Evaluación Simple de Actas
- **Implementación básica** de evaluación

###### **DatasetMinuteEvaluationService.java** - Evaluación con Dataset
- **Dataset**: `python/evaluation_dataset.xlsx` (preguntas y respuestas de referencia).
- **Evaluación**: Carga datos, opcionalmente limpia BD (`evaluation.clean-before-load`), ejecuta cada pregunta con el QueryService configurado y compara con la referencia; evaluación LLM de Correctness, Context Sufficiency, Relevance, Independence.

###### **EvaluationServiceFactory.java** - Factory de Evaluación
- **Propósito**: Construye el pipeline de consulta (DocumentService, QueryService) con una configuración concreta de RagFeatureConfiguration para evaluar cada combinación (expansion, ner, tools, metadata).
- **Uso**: `evaluateWithConfiguration(config)` y `evaluateAllConfigurations()` en AbstractEvaluationService.

#### 🛠️ **utils/** - Utilidades del Sistema

##### **InfoExtractor.java** - Extractor de Información
- **Clase utilitaria** para extraer información de documentos
- **Funcionalidades principales**:
  - **Extracción de campos**: fecha, hora, lugar, asistentes
  - **Análisis de contenido**: agenda, decisiones, entidades
  - **Procesamiento de texto**: keywords, fragmentos relevantes
  - **Cálculos**: duración, conteos, comparaciones
- **Métodos destacados**:
  - `extractDate()`, `extractTime()`: Extracción temporal
  - `extractAttendees()`: Lista de asistentes
  - `extractAgenda()`: Puntos del orden del día
  - `extractLiteralField()`: Campos específicos
  - `calculateDuration()`: Cálculo de duración
  - `compare()`: Comparación de métricas
- **Uso de expresiones regulares** para extracción precisa
- **Manejo de diferentes formatos** de documentos

## 📁 **src/main/resources/** - Recursos del Sistema

### **application.properties** - Configuración Principal
- **Servidor**: Puerto 9000
- **Ollama**: base-url, chat.model (gemma3:4b), embedding.model (mxbai-embed-large), top-k, similarity-threshold
- **Base de datos**: PostgreSQL (vectordb), JPA ddl-auto=update
- **RAG features** (rag.features): `expansion-enabled`, `ner-enabled`, `tools-enabled`, `metadata-enabled` (por defecto todos false; para evaluación con tools+metadata se usan vía POST /evaluate/custom)
- **Evaluación**: `evaluation.clean-before-load=true` para limpiar BD antes de cargar en evaluaciones
- **Chunk**: `rag.chunk.max-chars=400`
- **Clasificador Python**: rutas a ejecutable y script de clasificación

### **docs/actas/** - Documentos de Validación
- **Contiene actas de reuniones** utilizadas para validar el sistema
- **Propósito**: Evaluar mejoras en la arquitectura y funcionalidades
- **Archivos**: ACTA 1.pdf a ACTA 6.pdf (excepto ACTA 4)
- **Uso**: Dataset de prueba para validar respuestas del sistema

### **Otras carpetas de recursos**:
- **python/**: Scripts de clasificación y `evaluation_dataset.xlsx` (dataset de evaluación)
- **results/**: Resultados de evaluaciones (v2, v3, **v4**: tools-metadata.json, tools-metadata.log, full.json, full.log)
- **docs/actas/**: PDFs de actas para pruebas
- **enhancement/**: Ejemplos y referencias (RAG, LlamaIndex, etc.)

## 🔄 **Flujo de Funcionamiento del Sistema**

### 1. **Procesamiento de Documentos**
- Subida de documentos vía API
- Procesamiento por DocumentService (Simple o Metadata)
- Almacenamiento en vector store con embeddings

### 2. **Procesamiento de Consultas**
- Recepción de consulta en QueryService
- Clasificación por QueryClassifier
- Expansión opcional por QueryExpander
- Análisis NER por QueryAnalyser
- Recuperación de contexto por ContextRetriever

### 3. **Ejecución de Herramientas**
- Selección de herramienta apropiada según QueryType
- Ejecución con contexto recuperado
- Generación de respuesta estructurada

### 4. **Evaluación del Sistema**
- Carga de dataset de evaluación
- Ejecución de consultas de prueba
- Comparación con respuestas de referencia
- Generación de métricas de rendimiento

## 🎯 **Características Destacadas del Sistema**

### **Arquitectura Modular**
- Separación clara de responsabilidades
- Interfaces bien definidas
- Fácil extensión y modificación

### **Sistema de Herramientas Inteligente**
- Herramientas especializadas por tipo de consulta
- Versiones estándar y de metadatos
- Ejecución contextualizada

### **Configuración Flexible**
- Flags para activar/desactivar características
- Configuración de modelos configurable
- Adaptación a diferentes entornos

### **Procesamiento Avanzado**
- Clasificación automática de consultas
- Expansión inteligente de consultas
- Reconocimiento de entidades nombradas
- Extracción de metadatos estructurados

### **Evaluación Continua**
- Sistema de evaluación integrado
- Datasets de validación
- Métricas de rendimiento

## 🚀 **Casos de Uso Principales**

1. **Consulta de Documentos**: Búsqueda semántica en actas de reuniones
2. **Extracción de Información**: Obtención de datos estructurados
3. **Análisis de Contenido**: Resúmenes, comparaciones, filtrados
4. **Validación del Sistema**: Evaluación continua del rendimiento
5. **Investigación y Auditoría**: Análisis de reuniones y decisiones

## 📌 **Estado actual del proyecto (Febrero 2025)**

- **Controladores**: Solo RagController y TestController (no existe ToolsTestController).
- **Configuración**: 4 flags (expansion, ner, tools, metadata); evaluación con configuración custom y todas las combinaciones vía API.
- **Guard**: DateExistenceGuard integrado en el flujo de consultas para evitar errores de fecha cuando no existe acta.
- **Evaluación**: Dataset desde Excel; resultados v4 en `results/v4/` (tools-metadata: Correctness ≥ 4 en todos los ítems).
- **Java**: ~92 clases en `com.uniovi.rag` (controllers, configuration, model, services, utils).

## 🔮 **Preparado para Futuras Mejoras**

- **Sistema Agentic**: Estructura preparada para implementación
- **Herramientas Avanzadas**: Framework extensible
- **Configuración Dinámica**: Fácil activación de nuevas características
- **Integración con Ollama**: Soporte para diferentes modelos locales
