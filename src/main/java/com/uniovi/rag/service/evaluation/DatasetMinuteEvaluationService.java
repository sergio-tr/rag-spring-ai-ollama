package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class DatasetMinuteEvaluationService extends AbstractMinuteEvaluationService {

    private static final String EXCEL_CLASSPATH = "python/evaluation_dataset.xlsx";

    public DatasetMinuteEvaluationService(
        RagFeatureConfiguration featureConfig,
        ChatClient chatClient,
        DocumentService documentService,
        QueryService queryService,
        boolean cleanBeforeLoad
    ) {
        super(featureConfig, chatClient, documentService, queryService, cleanBeforeLoad);
    }

    /** Expected answers should be verified against ACTA 1, 2, 3, 5, 6 as source of truth. */
    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        Map<String, String> qaList = new HashMap<>();

        try (
                InputStream fis = new ClassPathResource(EXCEL_CLASSPATH).getInputStream();
                Workbook workbook = new XSSFWorkbook(fis)
        ) {

            Sheet sheet = workbook.getSheetAt(0);
            boolean isHeader = true;

            for (Row row : sheet) {
                if (isHeader) {
                    isHeader = false;
                    continue; // saltar la cabecera
                }

                Cell questionCell = row.getCell(0);
                Cell answerCell = row.getCell(1);

                if (questionCell == null || answerCell == null) continue;

                String question = questionCell.getStringCellValue().trim();
                String answer = answerCell.getStringCellValue().trim();

                qaList.put(question, answer);
            }

        } catch (Exception e) {
            log().error("Error reading dataset file", e);
        }

        return qaList;
    }

}
