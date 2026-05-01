package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.services.DocumentService;
import com.uniovi.rag.services.query.SimpleQueryService;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

@Service
public class ExcelEvaluationService extends AbstractEvaluationService {

    private final static Map<String, String> QUESTION_ANSWERS = Map.of(
            "¿Cuántos dispositivos diferentes revisó Juan Perez?", "Juan Pérez revisó un total de dos dispositivos diferentes, identificados como D001 y D003.",
            "¿Cuál fue el voltaje máximo del dispositivo D001?", "El voltaje máximo registrado para el dispositivo identificado como D001 fue de 230 V.",
            "¿Cuántos dispositivos diferentes se realizaron el último mes?", "Durante el último mes, se inspeccionó únicamente un dispositivo, el cual corresponde a D001.",
            "¿Cuál fue el voltaje máximo obtenido en una inspección?", "El voltaje máximo registrado en una inspección alcanzó un valor de 400 V.",
            "¿Cuántos dispositivos tienen un voltaje menor de 200?", "Actualmente, hay solo un dispositivo cuyo voltaje es inferior a 200 V, identificado como D002.",
            "¿Qué dispositivos tienen un voltaje menor de 200?", "El único dispositivo con un voltaje menor a 200 V es D002.",
            "¿Cuántas evaluaciones se realizaron de manera correcta el último mes?","En el último mes, se registró una evaluación correctamente realizada dentro de las pruebas funcionales.",
            "¿Cuántas evaluaciones se realizaron de manera incorrecta el último mes?","No se registraron evaluaciones incorrectas durante el último mes.",
            "¿Quién es el revisor/inspector que más evaluaciones ha realizado?", "No hay un inspector con más evaluaciones realizadas, ya que se registra un empate con 3 revisiones cada uno.",
            "¿Cuántas revisiones realizó cada inspector en el mes 7-2023?", "En el mes 7-2023, los inspectores Juan Pérez y Pedro Sánchez realizaron una revisión cada uno."
    );

    public ExcelEvaluationService(OllamaChatModel chatModel, DocumentService documentService, SimpleQueryService queryService) {
        super(chatModel, documentService, queryService);
    }

    @Override
    public void loadSpecificData() {
        StringBuilder content = new StringBuilder();

        try {
            InputStream file = new ClassPathResource("docs/excel/ejemplo.csv").getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar el archivo CSV", e);
        }

        Document document = new Document(String.join("\n", content.toString()));
        documentService.add(List.of(document));
    }

    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        return QUESTION_ANSWERS;
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
                - "dispositivo" puede referirse a equipo, aparato, instrumento, unidad o módulo.
                - "revisor" puede ser inspector, auditor, verificador, controlador o evaluador.
                - "evaluación" puede ser prueba, verificación, comprobación, test o revisión.
                - "inspección" puede ser examen, revisión, chequeo, control o análisis.
                - "voltaje" puede ser tensión, potencial eléctrico o carga.
                - "sensor" puede referirse a detector, transductor o medidor.
                - "temperatura" puede ser grado térmico, calor o nivel térmico.
                - "revisión mensual" puede ser control mensual, auditoría mensual o chequeo mensual.
                """,
                """
                2. Elementos de contexto:
                    - Dispositivo: Se refiere exclusivamente a componentes físicos como cámaras, receptores y sensores, no incluye software o sistemas operativos. Ejemplos de dispositivos son el sensor de temperatura D001, la cámara de seguridad D003 y el receptor de señales D002.
                    - Inspector: Es la persona encargada de realizar las revisiones físicas de los dispositivos para asegurar su correcto funcionamiento. No debe confundirse con el supervisor, que solo supervisa el proceso, ni con el técnico de mantenimiento, quien se encarga de reparaciones.
                    - Evaluación: Proceso específico de pruebas funcionales realizadas sobre los dispositivos para verificar que cumplen con los requisitos definidos. Las evaluaciones pueden ser correctas (sin fallos detectados) o incorrectas (fallo o incumplimiento detectado). No se refiere a revisiones generales ni auditorías externas.
                    - Inspección: Proceso detallado en el que el inspector verifica las características físicas y funcionales del dispositivo. Una inspección incluye la medición de voltajes, revisión de temperaturas y pruebas de conectividad.
                    - Voltaje: Medición eléctrica realizada en cada dispositivo durante la inspección. Puede ser el voltaje máximo (Vmax) o mínimo (Vmin), expresado en voltios (V).
                    - Revisión mensual: Conjunto de inspecciones y evaluaciones realizadas en un período de un mes calendario. Se suele realizar una revisión por dispositivo cada mes.
                    - Sensor de temperatura: Dispositivo que mide y reporta la temperatura durante la operación. Identificado como D001 en el sistema.
                    - Cámara de seguridad: Dispositivo utilizado para capturar imágenes o video. Identificado como D003 en el sistema.
                    - Receptor de señales: Dispositivo que recibe y procesa señales de diferentes sensores. Identificado como D002 en el sistema.
                """,
                """
                3. Abreviaturas relevantes:
                    - D001: Dispositivo identificado como D001, corresponde a un sensor de temperatura.
                    - D002: Dispositivo identificado como D002, es un receptor de señales.
                    - D003: Dispositivo identificado como D003, es una cámara de seguridad.
                    - Vmax: Voltaje máximo registrado en las mediciones.
                    - Vmin: Voltaje mínimo registrado en las mediciones.
                    - Eval .: Evaluación, utilizada para describir el proceso de prueba funcional.
                    - Inspec.: Inspección, que hace referencia a la revisión completa de un dispositivo.
                    - Temp.: Temperatura medida durante la inspección del dispositivo.
                    - Mes 7-2023: Período correspondiente a julio de 2023.
                    - Correcta: Evaluación realizada sin errores detectados.
                    - Incorrecta: Evaluación que ha fallado o ha detectado errores.
                """,
                """
                4. Reglas de búsqueda:
                    1. Siempre proporciona respuestas detalladas, explicando cada paso o contexto relevante del resultado.
                    2. Si una pregunta incluye términos ambiguos, aclara el significado en la respuesta antes de responder directamente.
                    3. En las respuestas, contextualiza el dato solicitado. Por ejemplo, si se menciona un dispositivo, añade detalles sobre su tipo o función.
                    4. Si una pregunta incluye un período de tiempo, verifica los registros correspondientes a ese período y explica cómo se obtuvo el dato.
                    5. Si detectas resultados iguales o cercanos, destaca las similitudes o diferencias relevantes.
                    6. Describe el proceso que seguiste para obtener el resultado, en especial si implica cálculos, filtrados o consultas complejas.
                    7. No omitas detalles importantes, aunque la respuesta sea más extensa.
                    8. Prioriza siempre dispositivos físicos sobre registros de prueba virtual.
                """
                ,
                """
                5. Operaciones Específicas del Dominio:
                    - Media Aritmética: Promedio de los valores de voltaje registrados para un dispositivo.
                    - Máximo/Mínimo: Valor máximo o mínimo de voltaje o temperatura registrado durante una inspección.
                    - Cálculo de Tendencias: Identificación de patrones de aumento o disminución en los registros de inspección.
                    - Comparaciones: Comparar voltajes entre dispositivos para determinar cuál es más estable.
                    - Frecuencia de Fallos: Determinación del número de fallos detectados por dispositivo en un período específico.
                    - Porcentaje de Evaluaciones Correctas/Incorrectas: Proporción de evaluaciones realizadas correctamente respecto al total.
                    - Filtrado Temporal: Buscar dispositivos inspeccionados en un rango de fechas específico.
                    - Agrupación por Tipo de Dispositivo: Clasificación de dispositivos por su tipo (sensores, cámaras, receptores).
                    - Filtrado por Parámetro: Seleccionar dispositivos con un voltaje menor a 200V o una temperatura superior a 40°C.
                    - Cruce de Datos: Relacionar datos de inspección con datos de evaluaciones para obtener resultados combinados.
                    - Análisis de Desviación Estándar: Verificación de la variabilidad en los resultados de inspección.
                    - Correlación de Parámetros: Relación entre temperatura y voltaje para predecir anomalías.
                    - Detección de Anomalías: Identificación de resultados atípicos o fuera de rango esperado.
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

