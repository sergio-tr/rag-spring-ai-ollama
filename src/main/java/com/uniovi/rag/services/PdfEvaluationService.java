package com.uniovi.rag.services;

import org.springframework.ai.ollama.OllamaChatModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfEvaluationService extends AbstractEvaluationService {

    private final static Map<String, String> QUESTION_ANSWERS_1 = Map.of(
            "¿Dónde se encuentran los centros de datos de las bases de datos de oracle para asistema financiero?", "La infraestructura está en centros de datos ubicados en España",
            "¿Qué puedes decir de la autenticacón para la base de datos oracle para sistema financiero?", "La autenticación se basa en una integración con LDAP",
            "¿Qué incluye el servicio de gestión de bases de datos MySQL y PostgreSQL?", "El servicio incluye administración remota, optimización de rendimiento, copias de seguridad automáticas y control de accesos.",
            "¿Cuánto tiempo se retienen las copias de seguridad en el servicio de gestión de bases de datos?", "Las copias de seguridad se retienen durante 30 días.",
            "¿Qué nivel de soporte ofrece el servicio en caso de incidencias?", "Las incidencias se atienden en un tiempo de respuesta inferior a 4 horas.",
            "¿Dónde se encuentra la infraestructura del servicio de nube privada para aplicaciones críticas?", "La infraestructura está dedicada dentro de la UE.",
            "¿Qué nivel de disponibilidad garantiza el servicio de nube privada?", "El servicio ofrece una alta disponibilidad con un SLA del 99,99%.",
            "¿Cómo se garantiza la seguridad en el acceso al servicio?", "El acceso es seguro mediante VPN para empleados públicos y auditorías de seguridad semestrales.",
            "¿Dónde se encuentran los centros de datos del servicio de nube pública para entorno de pruebas?", "Los centros de datos están ubicados en la UE.",
            "¿Qué tecnologías de contenedores son compatibles con el servicio de nube pública?", "El servicio es compatible con contenedores Docker y Kubernetes."
    );

    private final static Map<String, String> QUESTION_ANSWERS_2 = Map.of(
            "¿Cómo se gestiona el acceso a los recursos en la nube pública?", "El acceso se gestiona mediante una API REST y un panel de control web con métricas detalladas.",
            "¿Qué tipo de firewall incluye el servicio de seguridad perimetral para infraestructura IT?", "El servicio cuenta con un firewall de nueva generación con inspección profunda de paquetes.",
            "¿Cómo protege el servicio contra amenazas externas?", "Incluye protección contra ataques DDoS, filtrado de contenido web y actualización automática de firmas de amenazas.",
            "¿Qué medidas de autenticación y acceso seguro ofrece el servicio?", "El servicio soporta VPNs seguras y autenticación multifactor para mejorar la seguridad de los accesos.",
            "¿Qué tipo de redundancia ofrece el servicio de almacenamiento en red?", "El servicio utiliza dispositivos con redundancia RAID 10.",
            "¿Cuáles son los protocolos compatibles con el almacenamiento en red?", "El almacenamiento es compatible con los protocolos NFS y SMB.",
            "¿Cuál es el tiempo máximo de recuperación ante un fallo?", "El tiempo máximo de recuperación ante fallo es de 1 hora.",
            "¿Con qué sistemas operativos es compatible la solución de backup y recuperación?", "El sistema de copias de seguridad es compatible con servidores Windows y Linux.",
            "¿Qué tipo de backups realiza la solución y cuánto tiempo se retienen?", "Realiza backups incrementales y diferenciales con una retención de 90 días.",
            "¿Cuál es el tiempo de recuperación ante desastres garantizado?", "El tiempo de recuperación ante desastres (RTO) es inferior a 4 horas."
    );

    public PdfEvaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        super(chatModel, documentService, queryService);
    }

    @Override
    public void loadSpecificData() {
        documentService.loadPdfsData();
    }

    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        Map<String, String> questionAnswers = new HashMap<>(QUESTION_ANSWERS_1);
        questionAnswers.putAll(QUESTION_ANSWERS_2);
        return questionAnswers;
    }

    @Override
    public List<String> getSystemPrompts() {
        return List.of(
                """
                Responde siempre en español. A continuación, tienes información clave para interpretar las preguntas y generar respuestas correctas.
                Presta especial atención a los elementos de contexto para asegurar precisión en las respuestas.
                """,
                """
                1. Sinónimos:
                - "base de datos" puede referirse a sistema de almacenamiento, repositorio de información o DBMS.
                - "nube privada" puede ser infraestructura dedicada, entorno virtualizado o plataforma de hosting.
                - "nube pública" puede referirse a infraestructura compartida, servicio en la nube o PaaS.
                - "seguridad perimetral" puede referirse a firewall, protección de red o control de accesos.
                - "almacenamiento en red" puede ser SAN, NAS, solución de backup o almacenamiento centralizado.
                - "alta disponibilidad" puede referirse a redundancia, tolerancia a fallos o SLA garantizado.
                - "backup" puede ser copia de seguridad, respaldo de datos o snapshot.
                - "incidencia" puede ser fallo, error, interrupción o anomalía.
                """,
                """
                2. Elementos de contexto:
                    - Base de Datos: Se refiere a los sistemas de gestión de datos como Oracle, MySQL y PostgreSQL, utilizados en entornos de misión crítica y pruebas.
                    - Nube Privada: Infraestructura dedicada para aplicaciones críticas, con acceso seguro mediante VPN y replicación en múltiples nodos.
                    - Nube Pública: Plataforma flexible con escalabilidad bajo demanda y compatibilidad con contenedores Docker y Kubernetes.
                    - Seguridad Perimetral: Protección de infraestructura IT mediante firewalls de nueva generación, integración con SIEM y autenticación multifactor.
                    - Almacenamiento en Red: Sistemas NAS/SAN con redundancia RAID y compatibilidad con protocolos como NFS y SMB.
                    - Backup y Recuperación: Soluciones con soporte para almacenamiento en cinta y en la nube, con encriptación AES-256 y recuperación ante desastres (RTO < 4h).
                    - Monitorización: Sistemas de control en tiempo real de rendimiento, tráfico, consumo de recursos y estado de dispositivos.
                    - Cumplimiento Normativo: Servicios diseñados para cumplir regulaciones como RGPD, con auditorías periódicas de seguridad.
                """,
                """
                3. Abreviaturas relevantes:
                    - DBMS: Sistema de gestión de bases de datos.
                    - HA: Alta disponibilidad (High Availability).
                    - SLA: Acuerdo de nivel de servicio (Service Level Agreement).
                    - VPN: Red privada virtual (Virtual Private Network).
                    - SIEM: Sistema de gestión de eventos e información de seguridad.
                    - RAID 10: Configuración de almacenamiento con redundancia optimizada.
                    - NAS: Almacenamiento en red (Network Attached Storage).
                    - SAN: Red de área de almacenamiento (Storage Area Network).
                    - RTO: Tiempo objetivo de recuperación (Recovery Time Objective).
                    - AES-256: Algoritmo de cifrado avanzado de 256 bits.
                """,
                """
                4. Reglas de búsqueda:
                    1. Siempre proporciona respuestas detalladas, explicando cada concepto técnico cuando sea necesario.
                    2. Si una pregunta incluye términos ambiguos, aclara el significado en la respuesta antes de responder directamente.
                    3. Contextualiza el dato solicitado dentro de los sistemas y servicios disponibles.
                    4. Si se solicita información sobre disponibilidad o seguridad, indica los niveles de SLA y las protecciones implementadas.
                    5. Si se hace referencia a almacenamiento o backup, especifica las tecnologías utilizadas (RAID, NAS, SAN, encriptación, retención de copias, etc.).
                    6. Para términos relacionados con redes y conectividad, describe los niveles de acceso seguro (VPN, autenticación multifactor, segmentación de red).
                    7. Si se menciona recuperación ante fallos, indica los mecanismos de respaldo y el tiempo de respuesta garantizado.
                """,
                """
                5. Operaciones Específicas del Dominio:
                    - Evaluación de Alta Disponibilidad: Análisis de configuraciones en clúster y replicación en tiempo real.
                    - Monitorización de Seguridad: Evaluación de amenazas y detección de anomalías en infraestructuras IT.
                    - Comparación de Almacenamiento: Diferencias entre NAS y SAN en términos de redundancia y accesibilidad.
                    - Análisis de Rendimiento: Optimización de bases de datos y tuning de consultas en MySQL/PostgreSQL.
                    - Cálculo de Capacidad: Determinación de la escalabilidad de almacenamiento según demanda.
                    - Análisis de Incidencias: Frecuencia de fallos e impacto en servicios con tiempos de respuesta garantizados.
                    - Verificación de Cumplimiento: Evaluación de auditorías de seguridad y normativas de protección de datos.
                    - Identificación de Riesgos: Factores de vulnerabilidad en nubes públicas y privadas.
                    - Planificación de Backup: Estrategia de copias incrementales, diferenciales y retención de datos.
                    - Filtrado Temporal de Eventos: Búsqueda de incidentes registrados en un rango de fechas específico.
                """,
                """
                6. Estilo de Respuesta:
                    - Responde siempre en prosa explicativa y desarrollada.
                    - Proporciona detalles adicionales siempre que sea relevante.
                    - Explica el contexto y la lógica detrás del resultado, especialmente en preguntas complejas.
                """
        );

    }


}
