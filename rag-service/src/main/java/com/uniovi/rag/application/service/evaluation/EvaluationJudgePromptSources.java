package com.uniovi.rag.application.service.evaluation;

/** Exposes frozen evaluation-judge prompt material for {@link com.uniovi.rag.infrastructure.config.PromptBundleFingerprint}. */
public final class EvaluationJudgePromptSources {

    static final String TEMPLATE_RAW =
            """
            Act as an expert evaluator of RAG (Retrieval-Augmented Generation) systems.
            Assess the quality of a generated answer by determining if it correctly answers the question.

            **CRITICAL EVALUATION PRINCIPLES (BE STRICT)**:
            1. **Correctness 5 only when fully correct**: Score 5 ONLY if the answer has all key facts correct and adds NO wrong facts. One correct fact plus one wrong fact (e.g. two dates when only one is correct) = at most 4, not 5.
            2. **Lists and enumerated answers**: If the expected answer specifies a set (e.g. one minute, two dates, "none") and the generated answer adds extra or wrong items, Correctness at most 4 (or 3 if more wrong than right). Expected "one date" and generated "date A and date B" with one wrong → not 5.
            3. **"No information found" / "None"**: If the expected answer says no information was found (e.g. no matching minutes) and the generated answer invents or lists content, Correctness MUST be 1 or 2 and Groundedness 1 or 2.
            4. **Yes/No questions**: If the generated answer contradicts the expected Yes/No, Correctness MUST be 1 or 2.
            5. **Comparison questions**: If the conclusion is opposite to the expected (e.g. expected "August" but generated "February"), Correctness at most 2 or 3.
            6. **Context understanding**: If asked for a specific fact (e.g. which acta, duration), the answer must contain that information; partial or wrong set of items reduces the score.

            **IMPORTANT**: Do not invent or use any external knowledge.
            Evaluate only what can be inferred from the three provided inputs: the question, the expected correct answer (as a guide), and the system-generated answer.

            Question: {question}
            Expected Correct Answer (GUIDE ONLY - not required to match exactly): {correctAnswer}
            System-Generated Answer: {generatedAnswer}

            Evaluate the following criteria on a scale from 1 to 5:

            1. **Correctness**: Does the answer correctly respond to what the question is asking?
               - Consider if the essential information requested is present, even if formatted differently.
               - Do NOT penalize for missing details that weren't explicitly asked for in the question.
               - Example: If asked "Which acta?", answering with the date is correct, even if duration details are missing.

            2. **Context Sufficiency**: Is it possible to answer correctly with the information provided?

            3. **Relevance**: Does the answer address what was asked, without unnecessary digressions?
               - A shorter answer that directly answers the question is better than a longer one with irrelevant details.

            4. **Independence**: Can the answer be understood on its own, without relying on additional context?

            5. **Groundedness (Fidelity)**: Does the answer rely only on the provided context, without inventing facts? Score 1–5 (1 = invented/unsupported, 5 = fully grounded in context).

            **Strict Scoring Guidelines**:
            - Score 5 ONLY when the answer is fully correct: all key facts present, no wrong or extra facts added. One correct fact plus one wrong fact = at most 4.
            - If the expected answer lists a specific number of items (e.g. one acta, two dates) and the generated answer includes extra or wrong items, Correctness at most 4 (or 3 if more wrong than right).
            - If expected says "none"/"ninguna"/"no information found" and generated invents or lists content, Correctness 1 or 2, Groundedness 1 or 2.
            - Yes/No contradiction → Correctness 1 or 2.
            - Score 4 only when essential information is correct and at most minor, non-contradictory extras.
            - Score 3 when partially correct but with missing or wrong important information.
            - Score 1-2 when incorrect, contradictory, irrelevant, or inventing content.

            Respond in this format:

            Correctness: [1-5] - Justification: [Focus on whether the answer responds to the question, not word matching]
            Context Sufficiency: [1-5] - Justification: ...
            Relevance: [1-5] - Justification: ...
            Independence: [1-5] - Justification: ...
            Groundedness: [1-5] - Justification: [Whether the answer relies only on context without inventing facts]
            Overall Summary: [Brief overall assessment focusing on whether the answer correctly responds to the question]
            """;

    private EvaluationJudgePromptSources() {}

    public static String fingerprintMaterial() {
        return TEMPLATE_RAW;
    }
}
