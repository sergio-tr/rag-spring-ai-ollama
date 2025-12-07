package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.client.ChatClient;


import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

public class DatasetMinuteEvaluationService extends AbstractMinuteEvaluationService {

    private static final String excelPath = "src/main/resources/python/evaluation_dataset.xlsx";

    public DatasetMinuteEvaluationService(
            RagFeatureConfiguration featureConfig,
            ChatClient chatClient,
            DocumentService documentService,
            QueryService queryService) {
        super(featureConfig, chatClient, documentService, queryService);
    }

    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        Map<String, String> qaList = new HashMap<>();

        try (
                FileInputStream fis = new FileInputStream(excelPath);
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
