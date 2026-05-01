package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.services.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
public abstract class ActaEvaluationService extends AbstractEvaluationService {

    public static final Map<String, String> QA1 = Map.of(
            "¿Qué secciones comparten todas las actas?", "Título, Fecha, Lugar, Hora de inicio y finalización, Asistentes, Orden del día (que incluye la lectura y aprobación del acta anterior, puntos específicos a tratar y, Ruegos y preguntas.) y la hora de finalización",
            "¿Cuántas reuniones comenzaron a las 19:00 horas?", "Tres reuniones comenzaron a las 19:00 horas (24 de febrero de 2025, 25 de febrero de 2025 y 25 de febrero de 2026).",
            "¿En cuántas actas aparece Juan Pérez Gutiérrez?", "Juan Pérez Gutiérrez aparece en 2 actas (24 de febrero de 2025 y 25 de febrero de 2025).",
            "¿Cuáles fueron los puntos del orden del día en la reunión del 24 de febrero de 2025?", "Los puntos fueron: lectura y aprobación del acta anterior, estado de cuentas y presupuesto, reparaciones del edificio, normas de convivencia y ruegos y preguntas.",
            "¿Cuántas personas asistieron a la reunión del 25 de febrero de 2025?", "Los asistentes incluyeron a 20 personas.",
            "¿En cuántas reuniones se habló sobre presupuestos?", "En cinco reuniones se discutió sobre presupuestos con diferentes fines. En una de las reuniones se aprobó un presupuesto anual con una ligera subida en la cuota mensual.",
            "¿Cuántas reuniones tuvieron una duración superior o igual a 1 hora y 45 minutos?", "Una única reunión tuvo una duración superior a 1 hora y 45 minutos (25 de febrero de 2025).",
            "¿En qué reuniones se mencionó la instalación de cámaras de seguridad?", "Se mencionó en las reuniones del 24 de febrero de 2025 y 25 de febrero de 2025.",
            "¿Cuántas reuniones se realizaron en el año 2025?", "Tres reuniones se realizaron en 2025 (24 de febrero, 25 de febrero y 25 de agosto).",
            "¿Quién fue el presidente en la reunión del 25 de agosto de 2025?", "Beatriz Suárez Aguilar fue la presidenta en la reunión del 25 de agosto de 2025."
    );
    public static final Map<String, String> QA2 = Map.of(
            "¿En cuántas actas se realizaron propuestas?", "En dos actas se hicieron propuestas (25 de febrero de 2025, 25 de febrero de 2026, ).",
            "¿Cuántas reuniones se llevaron a cabo en el mes de agosto?", "Dos reuniones se llevaron a cabo en agosto (25 de agosto de 2025 y 25 de agosto de 2026).",
            "¿Qué reuniones incluyeron debates sobre normas de convivencia?", "Se debatieron normas de convivencia en las reuniones del 24 de febrero de 2025, 25 de agosto de 2025 y 25 de febrero de 2026.",
            "¿En qué reuniones se habló sobre la contratación de nuevos servicios?", "Se discutió la contratación de nuevos servicios en las reuniones del 25 de febrero de 2025 y 25 de agosto de 2025.",
            "¿Cuántas reuniones se realizaron con menos de 20 asistentes?", "Tres reuniones tuvieron menos de 20 asistentes (25 de agosto de 2025, 25 de febrero de 2026 y 25 de agosto de 2026).",
            "¿En qué actas se mencionaron problemas con el sistema eléctrico?", "Los problemas con el sistema eléctrico fueron mencionados en el acta del 25 de agosto de 2026."
    );

    public ActaEvaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        super(chatModel, documentService, queryService);
    }

    @Override
    protected void loadSpecificData() {
        List<Document> documents = new ArrayList<>();

        try {
            ClassPathResource resource = new ClassPathResource("docs/actas");
            File directory = resource.getFile();

            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

                if (files != null) {
                    for (File file : files) {
                        try (PDDocument document = PDDocument.load(file)) {
                            PDFTextStripper stripper = new PDFTextStripper();
                            String content = stripper.getText(document);

                            if (!content.isEmpty()) {
                                String documentId = UUID.randomUUID().toString();
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("document_id", documentId);
                                metadata.put("filename", file.getName());

                                Document doc = new Document(documentId, content, metadata);
                                documents.add(doc);
                            }
                        } catch (Exception e) {
                            System.err.println("Error procesando el PDF: " + file.getName());
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar archivos PDF desde resources/docs/", e);
        }

        documentService.add(documents);
    }

    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        Map<String, String> qa = new HashMap<>(QA1);
        qa.putAll(QA2);
        return qa;
    }
}
