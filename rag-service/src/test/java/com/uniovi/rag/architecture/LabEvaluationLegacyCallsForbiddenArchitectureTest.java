package com.uniovi.rag.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.uniovi.rag.application.service.evaluation.EvaluationService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Ensures Lab async handlers and {@code LabController} never call removed {@code EvaluationService#getQuestionsAndAnswers()}.
 */
@AnalyzeClasses(packages = "com.uniovi.rag", importOptions = ImportOption.DoNotIncludeTests.class)
class LabEvaluationLegacyCallsForbiddenArchitectureTest {

    @ArchTest
    static final ArchRule lab_async_handlers_must_not_call_evaluation_service_getQuestionsAndAnswers =
            classes()
                    .that()
                    .resideInAPackage("..service.async.lab..")
                    .and()
                    .haveSimpleNameEndingWith("JobHandler")
                    .and()
                    .areNotInterfaces()
                    .should(notCallEvaluationServiceGetQuestionsAndAnswers());

    @ArchTest
    static final ArchRule lab_controller_must_not_call_evaluation_service_getQuestionsAndAnswers =
            classes()
                    .that()
                    .haveSimpleName("LabController")
                    .should(notCallEvaluationServiceGetQuestionsAndAnswers());

    private static ArchCondition<JavaClass> notCallEvaluationServiceGetQuestionsAndAnswers() {
        return new ArchCondition<>("not call EvaluationService.getQuestionsAndAnswers()") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    if (!"getQuestionsAndAnswers".equals(call.getName())) {
                        continue;
                    }
                    if (call.getTargetOwner().isAssignableTo(EvaluationService.class)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        call,
                                        javaClass.getSimpleName()
                                                + " must not call EvaluationService.getQuestionsAndAnswers()"));
                    }
                }
            }
        };
    }
}
