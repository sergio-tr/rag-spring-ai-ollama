package com.uniovi.rag.interfaces.rest;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.P39ImportZipTestUtil;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportSizeExceededException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RunImportZipTestUtil;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUserSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.RagApiTestPaths;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Microphase 5.52 - P60 observable HTTP contract gate ({@code T-P60-e2e}, {@code T-P60-errors}).
 *
 * <p>FD-p60-arch-inventory: each {@code P60-M-xx} binds to the authoritative {@code Class#method} named in the
 * microphase plan Compatibility matrix; each {@code p60_mXX} test re-states the same MockMvc observables.
 */
@DisplayName("Runtime trace regression suite HTTP contract (MockMvc)")
public class RuntimeTraceRegressionSuiteEndToEndContractTest {

    /*
     * FD-p60-arch-inventory (T-P60-e2e, T-P60-errors) - matrix row binding:
     * P60-M-01 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#list_noQueryString_emptyService_returns200_emptyRuns
     * P60-M-01 p60 p60_m01
     * P60-M-02 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#list_queryParam_returns400_emptyBody
     * P60-M-02 p60 p60_m02
     * P60-M-03 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#detail_validUuid_snapshot_returns200_strictTopLevelKeys
     * P60-M-03 p60 p60_m03
     * P60-M-04 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#detail_emptyOptional_returns404
     * P60-M-04 p60 p60_m04
     * P60-M-05 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#detail_malformedUuid_returns400_emptyBody
     * P60-M-05 p60 p60_m05
     * P60-M-06 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#detail_validUuid_queryParam_returns400_emptyBody
     * P60-M-06 p60 p60_m06
     * P60-M-07 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#post_explicit_valid_empty_suite_completed_outcome_returns_201_location_and_persists
     * P60-M-07 p60 p60_m07
     * P60-M-08 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#post_conversation_valid_uuid_body_completed_outcome_returns_201_same_location_rule
     * P60-M-08 p60 p60_m08
     * P60-M-09 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#post_explicit_query_string_returns_400_never_execute_or_createRun
     * P60-M-09 p60 p60_m09
     * P60-M-10 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#post_explicit_malformed_json_returns_400_never_execute_or_createRun
     * P60-M-10 p60 p60_m10
     * P60-M-11 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#post_conversation_invalid_path_uuid_returns_400_never_execute_or_createRun
     * P60-M-11 p60 p60_m11
     * P60-M-12 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#post_explicit_not_attempted_returns_400_execute_once_never_createRun
     * P60-M-12 p60 p60_m12
     * P60-M-13 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#p49_t1_delete_success_returns_204_empty_body_no_location_headers
     * P60-M-13 p60 p60_m13
     * P60-M-14 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#p49_t2_delete_missing_run_returns_404_empty_body_no_location_headers
     * P60-M-14 p60 p60_m14
     * P60-M-15 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#p49_t4_delete_query_string_returns_400_never_deleteRunForUser
     * P60-M-15 p60 p60_m15
     * P60-M-16 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunControllerTest#p49_t5_delete_malformed_uuid_returns_400_never_deleteRunForUser
     * P60-M-16 p60 p60_m16
     * P60-M-17 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunExportControllerWebMvcTest#t1_getExport200_zipHeaders
     * P60-M-17 p60 p60_m17
     * P60-M-18 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunExportControllerWebMvcTest#t2_queryString_returns400_neverCallsLoad
     * P60-M-18 p60 p60_m18
     * P60-M-19 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunExportControllerWebMvcTest#t3_malformedUuid_returns400_neverCallsLoad
     * P60-M-19 p60 p60_m19
     * P60-M-20 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunExportControllerWebMvcTest#t4_emptyOptional_returns404_loadOnce_verifyNoMoreInteractions
     * P60-M-20 p60 p60_m20
     * P60-M-21 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunExportControllerWebMvc413Test#t5_exportServiceThrows413_noPersistenceCalls
     * P60-M-21 p60 p60_m21
     * P60-M-22 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunImportControllerWebMvcTest#t1_validZip_201_location_emptyBody
     * P60-M-22 p60 p60_m22
     * P60-M-23 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunImportControllerWebMvcTest#t2_queryString_400_neverCreateRun
     * P60-M-23 p60 p60_m23
     * P60-M-24 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunImportControllerWebMvcTest#t3_contentTypeWithCharset_400
     * P60-M-24 p60 p60_m24
     * P60-M-25 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunImportPreviewControllerWebMvcTest#t1_validZip_200_jsonImportableNoLocation
     * P60-M-25 p60 p60_m25
     * P60-M-26 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunImportPreviewControllerWebMvcTest#t2_queryString_400_neverPreview
     * P60-M-26 p60 p60_m26
     * P60-M-27 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteRunImportPreviewControllerWebMvcTest#t3_contentTypeNull_400
     * P60-M-27 p60 p60_m27
     * P60-M-28 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExportControllerWebMvcTest#t1_getExport200_zipHeaders
     * P60-M-28 p60 p60_m28
     * P60-M-29 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExportControllerWebMvcTest#t2_queryString_returns400_neverCallsLoad
     * P60-M-29 p60 p60_m29
     * P60-M-30 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExportControllerWebMvcTest#t3_malformedUuid_returns400_neverCallsLoad
     * P60-M-30 p60 p60_m30
     * P60-M-31 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExportControllerWebMvcTest#t4_emptyOptional_returns404_loadOnce_verifyNoMoreInteractions
     * P60-M-31 p60 p60_m31
     * P60-M-32 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionImportControllerWebMvcTest#t1_created201_locationEmptyBody_createOnce
     * P60-M-32 p60 p60_m32
     * P60-M-33 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionImportControllerWebMvcTest#t2_queryString_neverCreate
     * P60-M-33 p60 p60_m33
     * P60-M-34 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionImportControllerWebMvcTest#t11_createIllegalState_conflict
     * P60-M-34 p60 p60_m34
     * P60-M-35 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionImportPreviewControllerWebMvcTest#t1_validZip_200_jsonShape
     * P60-M-35 p60 p60_m35
     * P60-M-36 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionImportPreviewControllerWebMvcTest#t2_queryString_400_emptyBody
     * P60-M-36 p60 p60_m36
     * P60-M-37 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionImportPreviewControllerWebMvcTest#t4_emptyBody_400
     * P60-M-37 p60 p60_m37
     * P60-M-38 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t1_x1_completedAllBatchReturns_zipHeaders
     * P60-M-38 p60 p60_m38
     * P60-M-39 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t2_x1_notFound
     * P60-M-39 p60 p60_m39
     * P60-M-40 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t3_x2_notFound
     * P60-M-40 p60 p60_m40
     * P60-M-41 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t4_x1_malformedDefinitionId
     * P60-M-41 p60 p60_m41
     * P60-M-42 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t11a_x1_sizeExceeded
     * P60-M-42 p60 p60_m42
     * P60-M-43 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t11b_x2_sizeExceeded
     * P60-M-43 p60 p60_m43
     * P60-M-44 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t12_x2_zipDispositionContainsConversation
     * P60-M-44 p60 p60_m44
     * P60-M-45 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest#t8_notAttemptedException_badRequest
     * P60-M-45 p60 p60_m45
     * P60-M-46 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerTest#list_noQueryString_emptyService_returns200_emptyDefinitions
     * P60-M-46 p60 p60_m46
     * P60-M-47 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerTest#list_queryParam_returns400_emptyBody
     * P60-M-47 p60 p60_m47
     * P60-M-48 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerTest#detail_validUuid_snapshot_returns200_shape
     * P60-M-48 p60 p60_m48
     * P60-M-49 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerTest#detail_emptyOptional_missingId_returns404
     * P60-M-49 p60 p60_m49
     * P60-M-50 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerTest#detail_malformedUuid_returns400
     * P60-M-50 p60 p60_m50
     * P60-M-51 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest#t1_create_valid_returns201_location_emptyBody
     * P60-M-51 p60 p60_m51
     * P60-M-52 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest#t2_update_valid_returns204_emptyBody
     * P60-M-52 p60 p60_m52
     * P60-M-53 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest#t3_delete_valid_returns204_emptyBody
     * P60-M-53 p60 p60_m53
     * P60-M-54 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest#t4_create_throwsIllegalState_returns409_emptyBody
     * P60-M-54 p60 p60_m54
     * P60-M-55 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest#t5_update_throwsIllegalState_returns409_emptyBody
     * P60-M-55 p60 p60_m55
     * P60-M-56 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest#t7_delete_throwsNotFound_returns404_emptyBody
     * P60-M-56 p60 p60_m56
     * P60-M-57 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerExecutionWebMvcTest#t1_e1_happyPath_completedAllBatchReturns_inOrderSameReq
     * P60-M-57 p60 p60_m57
     * P60-M-58 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerExecutionWebMvcTest#t9_e2_happyPath_inOrderSameReq
     * P60-M-58 p60 p60_m58
     * P60-M-59 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerExecutionWebMvcTest#t2_e1_materializeNotFound_returns404_neverExecute
     * P60-M-59 p60 p60_m59
     * P60-M-60 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerExecutionWebMvcTest#t3_e1_malformedDefinitionId_returns400_neverMaterializeNeverExecute
     * P60-M-60 p60 p60_m60
     * P60-M-61 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunCreationWebMvcTest#t1_route1_persist_eligible_201_location_createRun_saved_definition
     * P60-M-61 p60 p60_m61
     * P60-M-62 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunCreationWebMvcTest#t7_materialize_not_found_404
     * P60-M-62 p60 p60_m62
     * P60-M-63 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunCreationWebMvcTest#t3_query_string_400_no_service_calls
     * P60-M-63 p60 p60_m63
     * P60-M-64 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunCreationWebMvcTest#t10_not_attempted_400_execute_once_never_create_run
     * P60-M-64 p60 p60_m64
     * P60-M-65 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunQueryWebMvcTest#p50_t1_list_emptySummaries_200_emptyRuns
     * P60-M-65 p60 p60_m65
     * P60-M-66 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunQueryWebMvcTest#p50_t6_detail_200_elevenTopLevelKeys
     * P60-M-66 p60 p60_m66
     * P60-M-67 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunQueryWebMvcTest#p50_t5_list_gate_empty_404_neverRunQueries
     * P60-M-67 p60 p60_m67
     * P60-M-68 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunQueryWebMvcTest#p50_t7_detail_runMissing_404
     * P60-M-68 p60 p60_m68
     * P60-M-69 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunExportWebMvcTest#p53_t1_export_200_zipHeaders_verifyExportOnce_neverPersistenceFromController
     * P60-M-69 p60 p60_m69
     * P60-M-70 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunImportWebMvcTest#p54_t4_gate_ok_201_location_verifyImportOnce_neverPersistenceFromController
     * P60-M-70 p60 p60_m70
     * P60-M-71 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunImportPreviewWebMvcTest#p55_t4_gate_ok_200_json_verifyPreviewOnce_neverPersistenceOrImportOnThisPost
     * P60-M-71 p60 p60_m71
     * P60-M-72 authoritative com.uniovi.rag.interfaces.rest.RuntimeTraceRegressionSuiteDefinitionControllerRunDeletionWebMvcTest#p52_t1_delete_true_returns204_emptyBody_noLocationHeaders_verifyDeleteOnce
     * P60-M-72 p60 p60_m72
     *
     * All P60 @Test methods: p60_m01, p60_m02, p60_m03, p60_m04, p60_m05, p60_m06, p60_m07, p60_m08, p60_m09, p60_m10,
     * p60_m11, p60_m12, p60_m13, p60_m14, p60_m15, p60_m16, p60_m17, p60_m18, p60_m19, p60_m20, p60_m21, p60_m22, p60_m23,
     * p60_m24, p60_m25, p60_m26, p60_m27, p60_m28, p60_m29, p60_m30, p60_m31, p60_m32, p60_m33, p60_m34, p60_m35, p60_m36,
     * p60_m37, p60_m38, p60_m39, p60_m40, p60_m41, p60_m42, p60_m43, p60_m44, p60_m45, p60_m46, p60_m47, p60_m48, p60_m49,
     * p60_m50, p60_m51, p60_m52, p60_m53, p60_m54, p60_m55, p60_m56, p60_m57, p60_m58, p60_m59, p60_m60, p60_m61, p60_m62,
     * p60_m63, p60_m64, p60_m65, p60_m66, p60_m67, p60_m68, p60_m69, p60_m70, p60_m71, p60_m72
     */
    // --- Nested @WebMvcTest slices follow (authoritative parity per matrix row). ---

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteRunController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({RuntimeTraceRegressionSuiteRunController.class, RegressionSuiteRestJacksonConfiguration.class})
    @ActiveProfiles("test")
    @TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
    class SliceRunController {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private static final String VALID_EMPTY_ENTRIES_JSON = "{\"entries\":[]}";

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        private UUID userId;
        private UUID runId;
        private UUID conversationId;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            runId = UUID.randomUUID();
            conversationId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void p60_m01() throws Exception {
            when(runPersistenceService.listSummariesForUser(userId)).thenReturn(List.of());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"runs\":[]}", true));
            verify(runPersistenceService, times(1)).listSummariesForUser(any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m02() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs").queryParam("x", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m03() throws Exception {
            RuntimeTraceRegressionSuiteRunSnapshot snap =
                    new RuntimeTraceRegressionSuiteRunSnapshot(
                            new RuntimeTraceRegressionSuiteRunId(runId),
                            userId,
                            RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                            Optional.empty(),
                            RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                            new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                            Instant.parse("2024-03-01T12:00:00Z"),
                            List.of());
            when(runPersistenceService.loadByIdForUser(runId, userId)).thenReturn(Optional.of(snap));
            MvcResult result =
                    mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}", runId))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.id").value(runId.toString()))
                            .andExpect(jsonPath("$.definitionId").value(nullValue()))
                            .andExpect(jsonPath("$.entries").isArray())
                            .andExpect(jsonPath("$.entries", hasSize(0)))
                            .andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("hibernateLazyInitializer");
            JsonNode root = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            Set<String> names = new HashSet<>();
            root.fieldNames().forEachRemaining(names::add);
            assertThat(names)
                    .hasSize(11)
                    .isEqualTo(
                            Set.of(
                                    "id",
                                    "sourceType",
                                    "definitionId",
                                    "suiteOutcome",
                                    "createdAt",
                                    "requestedEntryCount",
                                    "processedEntryCount",
                                    "batchReturnedCount",
                                    "executionFailedCount",
                                    "batchNotAttemptedSubcount",
                                    "entries"));
            verify(runPersistenceService, times(1)).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m04() throws Exception {
            UUID id = UUID.randomUUID();
            when(runPersistenceService.loadByIdForUser(id, userId)).thenReturn(Optional.empty());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            r ->
                                    assertThat(r.getResolvedException())
                                            .isInstanceOf(NotFoundException.class)
                                            .hasMessage("run not found"));
            verify(runPersistenceService, times(1)).loadByIdForUser(id, userId);
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m05() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m06() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}", runId).queryParam("x", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m07() throws Exception {
            UUID createdId = UUID.randomUUID();
            RuntimeTraceRegressionSuiteResult result =
                    outcomeResult(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS);
            when(suiteService.execute(any())).thenReturn(result);
            when(runPersistenceService.createRun(
                            eq(userId), eq(RuntimeTraceRegressionSuiteRunSourceType.AD_HOC), eq(Optional.empty()), any()))
                    .thenReturn(createdId);

            mockMvc.perform(
                            post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_EMPTY_ENTRIES_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(content().string(""))
                    .andExpect(
                            header()
                                    .string(
                                            HttpHeaders.LOCATION,
                                            productBase() + "/runtime-trace-regression-suite-runs/" + createdId));

            verify(suiteService, times(1)).execute(any());
            verify(runPersistenceService, times(1))
                    .createRun(
                            eq(userId),
                            eq(RuntimeTraceRegressionSuiteRunSourceType.AD_HOC),
                            eq(Optional.empty()),
                            any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
        }

        @Test
        void p60_m08() throws Exception {
            UUID createdId = UUID.randomUUID();
            RuntimeTraceRegressionSuiteResult result =
                    outcomeResult(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS);
            when(suiteService.execute(any())).thenReturn(result);
            when(runPersistenceService.createRun(
                            eq(userId), eq(RuntimeTraceRegressionSuiteRunSourceType.AD_HOC), eq(Optional.empty()), any()))
                    .thenReturn(createdId);

            mockMvc.perform(
                            post(
                                            productBase()
                                                    + "/conversations/{cid}/runtime-trace-regression-suite-runs",
                                            conversationId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_EMPTY_ENTRIES_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(content().string(""))
                    .andExpect(
                            header()
                                    .string(
                                            HttpHeaders.LOCATION,
                                            productBase() + "/runtime-trace-regression-suite-runs/" + createdId));

            verify(suiteService, times(1)).execute(any());
            verify(runPersistenceService, times(1))
                    .createRun(
                            eq(userId),
                            eq(RuntimeTraceRegressionSuiteRunSourceType.AD_HOC),
                            eq(Optional.empty()),
                            any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
        }

        @Test
        void p60_m09() throws Exception {
            mockMvc.perform(
                            post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs"))
                                    .queryParam("x", "1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_EMPTY_ENTRIES_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));

            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
        }

        @Test
        void p60_m10() throws Exception {
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs")).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));

            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
        }

        @Test
        void p60_m11() throws Exception {
            mockMvc.perform(
                            post(productBase() + "/conversations/{cid}/runtime-trace-regression-suite-runs", "not-a-uuid")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_EMPTY_ENTRIES_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));

            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
        }

        @Test
        void p60_m12() throws Exception {
            RuntimeTraceRegressionSuiteResult result = outcomeResult(RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED);
            when(suiteService.execute(any())).thenReturn(result);

            mockMvc.perform(
                            post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_EMPTY_ENTRIES_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));

            verify(suiteService, times(1)).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
        }

        @Test
        void p60_m13() throws Exception {
            when(runPersistenceService.deleteRunForUser(eq(runId), eq(userId))).thenReturn(true);
            mockMvc.perform(delete(productBase() + "/runtime-trace-regression-suite-runs/{id}", runId))
                    .andExpect(status().isNoContent())
                    .andExpect(
                            r -> {
                                assertThat(r.getResponse().getContentAsByteArray().length).isZero();
                                assertThat(r.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
                                assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
                            });
            verify(runPersistenceService, times(1)).deleteRunForUser(eq(runId), eq(userId));
            verifyDeletePathDoesNotTouchGetPostServices();
        }

        @Test
        void p60_m14() throws Exception {
            when(runPersistenceService.deleteRunForUser(eq(runId), eq(userId))).thenReturn(false);
            mockMvc.perform(delete(productBase() + "/runtime-trace-regression-suite-runs/{id}", runId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            r -> {
                                assertThat(r.getResponse().getContentAsByteArray().length).isZero();
                                assertThat(r.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
                                assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
                            });
            verify(runPersistenceService, times(1)).deleteRunForUser(eq(runId), eq(userId));
            verifyDeletePathDoesNotTouchGetPostServices();
        }

        @Test
        void p60_m15() throws Exception {
            mockMvc.perform(delete(productBase() + "/runtime-trace-regression-suite-runs/{id}", runId).queryParam("x", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            r -> {
                                assertThat(r.getResponse().getContentAsByteArray().length).isZero();
                                assertThat(r.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
                                assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
                            });
            verify(runPersistenceService, never()).deleteRunForUser(any(), any());
            verifyDeletePathDoesNotTouchGetPostServices();
        }

        @Test
        void p60_m16() throws Exception {
            mockMvc.perform(delete(productBase() + "/runtime-trace-regression-suite-runs/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            r -> {
                                assertThat(r.getResponse().getContentAsByteArray().length).isZero();
                                assertThat(r.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
                                assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
                            });
            verify(runPersistenceService, never()).deleteRunForUser(any(), any());
            verifyDeletePathDoesNotTouchGetPostServices();
        }

        private void verifyDeletePathDoesNotTouchGetPostServices() {
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }

        private static RuntimeTraceRegressionSuiteResult outcomeResult(RuntimeTraceRegressionSuiteOutcome outcome) {
            return new RuntimeTraceRegressionSuiteResult(
                    outcome, new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0), List.of());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteRunExportController (200/4xx)")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunExportController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({
        RuntimeTraceRegressionSuiteRunExportController.class,
        RuntimeTraceRegressionSuiteEndToEndContractTest.SliceRunExport.ExportServiceTestConfig.class
    })
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceRunExport {

        @TestConfiguration
        static class ExportServiceTestConfig {

            @Bean
            RuntimeTraceRegressionSuiteRunExportService runtimeTraceRegressionSuiteRunExportService(
                    RuntimeTraceRegressionSuiteRunPersistenceService persistenceService) {
                return new RuntimeTraceRegressionSuiteRunExportService(persistenceService);
            }
        }

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        private UUID userId;
        private UUID runId;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            runId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        private static ResultMatcher noZipDownloadResponse() {
            return result -> {
                assertThat(result.getResponse().getContentAsByteArray().length).isZero();
                assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
                String ct = result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE);
                assertThat(
                                ct == null
                                        || (!ct.equalsIgnoreCase("application/zip")
                                                && !ct.toLowerCase(Locale.ROOT).startsWith("application/zip;")))
                        .isTrue();
            };
        }

        private static RuntimeTraceRegressionSuiteRunSnapshot minimalSnapshot(UUID id, UUID ownerUserId) {
            return new RuntimeTraceRegressionSuiteRunSnapshot(
                    new RuntimeTraceRegressionSuiteRunId(id),
                    ownerUserId,
                    RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                    Optional.empty(),
                    RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                    new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                    Instant.parse("2024-03-01T12:00:00Z"),
                    List.of());
        }

        @Test
        void p60_m17() throws Exception {
            when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId)))
                    .thenReturn(Optional.of(minimalSnapshot(runId, userId)));
            String expectedDisposition = "attachment; filename=\"runtime-trace-regression-suite-run_" + runId + ".zip\"";
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
                    .andExpect(
                            result -> {
                                byte[] body = result.getResponse().getContentAsByteArray();
                                String cl = result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH);
                                assertThat(cl).isEqualTo(Long.toString(body.length));
                                assertThat(body.length).isGreaterThan(0);
                            });
            verify(runPersistenceService, times(1)).loadByIdForUser(eq(runId), eq(userId));
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verifyNoMoreInteractions(runPersistenceService);
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m18() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId).queryParam("x", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(noZipDownloadResponse());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m19() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(noZipDownloadResponse());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m20() throws Exception {
            when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId))).thenReturn(Optional.empty());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId))
                    .andExpect(status().isNotFound())
                    .andExpect(noZipDownloadResponse());
            verify(runPersistenceService, times(1)).loadByIdForUser(eq(runId), eq(userId));
            verifyNoMoreInteractions(runPersistenceService);
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteRunExportController (413)")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunExportController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(RuntimeTraceRegressionSuiteRunExportController.class)
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceRunExport413 {

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunExportService exportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        private UUID userId;
        private UUID runId;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            runId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        private static ResultMatcher noZipDownloadResponse() {
            return result -> {
                assertThat(result.getResponse().getContentAsByteArray().length).isZero();
                assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
                String ct = result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE);
                assertThat(
                                ct == null
                                        || (!ct.equalsIgnoreCase("application/zip")
                                                && !ct.toLowerCase(Locale.ROOT).startsWith("application/zip;")))
                        .isTrue();
            };
        }

        @Test
        void p60_m21() throws Exception {
            when(exportService.exportRunZip(eq(runId), eq(userId)))
                    .thenThrow(new RuntimeTraceRegressionSuiteRunExportSizeExceededException("run export exceeds max ZIP size"));
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(noZipDownloadResponse());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).listSummariesForUser(any());
            verify(suiteService, never()).execute(any());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteRunImportController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunImportController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({
        RuntimeTraceRegressionSuiteRunImportController.class,
        RuntimeTraceRegressionSuiteEndToEndContractTest.SliceRunImport.ImportTestConfig.class
    })
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceRunImport {

        @TestConfiguration
        static class ImportTestConfig {

            @Bean
            RuntimeTraceRegressionSuiteRunImportService runtimeTraceRegressionSuiteRunImportService(
                    RuntimeTraceRegressionSuiteRunPersistenceService persistenceService) {
                return new RuntimeTraceRegressionSuiteRunImportService(persistenceService);
            }
        }

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService persistence;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionService definitionService;

        private UUID userId;
        private UUID runIdInFixture;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            runIdInFixture = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void p60_m22() throws Exception {
            UUID createdId = UUID.randomUUID();
            when(persistence.createRun(eq(userId), any(), any(), any())).thenReturn(createdId);
            byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId);
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-runs/import")
                                    .contentType("application/zip")
                                    .content(zip))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, productBase() + "/runtime-trace-regression-suite-runs/" + createdId))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray().length).isZero());
            verify(persistence, times(1)).createRun(eq(userId), any(), any(), any());
            verify(persistence, never()).loadByIdForUser(any(), any());
            verify(persistence, never()).listSummariesForUser(any());
            verifyNoMoreInteractions(persistence);
            verify(suiteService, never()).execute(any());
            verify(definitionService, never()).create(any(), any());
        }

        @Test
        void p60_m23() throws Exception {
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-runs/import")
                                    .queryParam("x", "1")
                                    .contentType("application/zip")
                                    .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId)))
                    .andExpect(status().isBadRequest());
            verify(persistence, never()).createRun(any(), any(), any(), any());
        }

        @Test
        void p60_m24() throws Exception {
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-runs/import")
                                    .contentType("application/zip; charset=UTF-8")
                                    .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId)))
                    .andExpect(status().isBadRequest());
            verify(persistence, never()).createRun(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteRunImportPreviewController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunImportPreviewController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(RuntimeTraceRegressionSuiteRunImportPreviewController.class)
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceRunImportPreview {

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportPreviewService previewService;

        private final RuntimeTraceRegressionSuiteRunImportPreviewService realPreviewService =
                new RuntimeTraceRegressionSuiteRunImportPreviewService();

        private UUID userId;
        private UUID runId;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            runId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        private void delegatePreviewToReal() {
            when(previewService.previewImportZip(any()))
                    .thenAnswer(
                            invocation -> realPreviewService.previewImportZip(invocation.getArgument(0)));
        }

        @Test
        void p60_m25() throws Exception {
            delegatePreviewToReal();
            byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId);
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs/import/preview")).contentType("application/zip").content(zip))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.importable").value(true))
                    .andExpect(jsonPath("$.warnings").isArray())
                    .andExpect(jsonPath("$.warnings.length()").value(0))
                    .andExpect(jsonPath("$.run.id").value(runId.toString()))
                    .andExpect(
                            result -> {
                                assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
                                assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
                                String ct = result.getResponse().getContentType();
                                assertThat(ct).isNotNull();
                                assertThat(ct).contains("application/json");
                            });
            verify(previewService, times(1)).previewImportZip(any());
        }

        @Test
        void p60_m26() throws Exception {
            mockMvc.perform(
                            post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs/import/preview"))
                                    .queryParam("x", "1")
                                    .contentType("application/zip")
                                    .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                    .andExpect(status().isBadRequest());
            verify(previewService, never()).previewImportZip(any());
        }

        @Test
        void p60_m27() throws Exception {
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs/import/preview")).content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                    .andExpect(status().isBadRequest());
            verify(previewService, never()).previewImportZip(any());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionExportController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionExportController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({
        RuntimeTraceRegressionSuiteDefinitionExportController.class,
        RuntimeTraceRegressionSuiteEndToEndContractTest.SliceDefinitionExport.ExportServiceTestConfig.class
    })
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceDefinitionExport {

        @TestConfiguration
        static class ExportServiceTestConfig {

            @Bean
            RuntimeTraceRegressionSuiteDefinitionExportService runtimeTraceRegressionSuiteDefinitionExportService(
                    RuntimeTraceRegressionSuiteDefinitionService definitionService) {
                return new RuntimeTraceRegressionSuiteDefinitionExportService(definitionService);
            }
        }

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionService definitionService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        private UUID userId;
        private UUID definitionId;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            definitionId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        private static ResultMatcher noZipDownloadResponse() {
            return result -> {
                assertThat(result.getResponse().getContentAsByteArray().length).isZero();
                assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
                String ct = result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE);
                assertThat(
                                ct == null
                                        || (!ct.equalsIgnoreCase("application/zip")
                                                && !ct.toLowerCase(Locale.ROOT).startsWith("application/zip;")))
                        .isTrue();
            };
        }

        private static RuntimeTraceRegressionSuiteDefinitionSnapshot minimalSnapshot(UUID id) {
            return new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                    id,
                    "n",
                    null,
                    1,
                    Instant.parse("2024-01-01T00:00:00Z"),
                    Instant.parse("2024-01-02T00:00:00Z"),
                    List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(UUID.randomUUID()))));
        }

        @Test
        void p60_m28() throws Exception {
            when(definitionService.loadByIdForUser(eq(definitionId), eq(userId)))
                    .thenReturn(Optional.of(minimalSnapshot(definitionId)));
            String expectedDisposition =
                    "attachment; filename=\"runtime-trace-regression-suite-definition_" + definitionId + ".zip\"";
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/export", definitionId))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
                    .andExpect(
                            result -> {
                                byte[] body = result.getResponse().getContentAsByteArray();
                                String cl = result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH);
                                assertThat(cl).isEqualTo(Long.toString(body.length));
                                assertThat(body.length).isGreaterThan(0);
                            });
        }

        @Test
        void p60_m29() throws Exception {
            mockMvc.perform(
                            get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/export", definitionId)
                                    .queryParam("x", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(noZipDownloadResponse());
            verify(definitionService, never()).loadByIdForUser(any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m30() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/export", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(noZipDownloadResponse());
            verify(definitionService, never()).loadByIdForUser(any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m31() throws Exception {
            when(definitionService.loadByIdForUser(eq(definitionId), eq(userId))).thenReturn(Optional.empty());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/export", definitionId))
                    .andExpect(status().isNotFound())
                    .andExpect(noZipDownloadResponse());
            verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
            verifyNoMoreInteractions(definitionService);
            verify(suiteService, never()).execute(any());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionImportController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionImportController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({
        RuntimeTraceRegressionSuiteDefinitionImportController.class,
        RuntimeTraceRegressionSuiteEndToEndContractTest.SliceDefinitionImport.ImportTestConfig.class
    })
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceDefinitionImport {

        @TestConfiguration
        static class ImportTestConfig {

            @Bean
            RuntimeTraceRegressionSuiteDefinitionImportService runtimeTraceRegressionSuiteDefinitionImportService(
                    RuntimeTraceRegressionSuiteDefinitionService definitionService) {
                return new RuntimeTraceRegressionSuiteDefinitionImportService(definitionService);
            }
        }

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionService definitionService;

        private UUID userId;
        private UUID definitionIdPath;
        private ObjectMapper fd4;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            definitionIdPath = UUID.randomUUID();
            fd4 = P39ImportZipTestUtil.fd4ObjectMapper();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        private byte[] validDefinitionJson() throws Exception {
            RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                    new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                            definitionIdPath,
                            "import-name",
                            "desc",
                            1,
                            Instant.parse("2020-01-01T00:00:00Z"),
                            Instant.parse("2020-01-02T00:00:00Z"),
                            List.of(
                                    new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(
                                            List.of(UUID.randomUUID()))));
            return fd4.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap));
        }

        private byte[] validZip() throws Exception {
            return P39ImportZipTestUtil.buildConvergedP38Zip(
                    fd4, Instant.parse("2024-06-01T00:00:00Z"), userId, definitionIdPath, validDefinitionJson());
        }

        @Test
        void p60_m32() throws Exception {
            UUID createdId = UUID.randomUUID();
            when(definitionService.create(eq(userId), any(CreateDefinitionCommand.class))).thenReturn(createdId);
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-definitions/import")).contentType("application/zip").content(validZip()))
                    .andExpect(status().isCreated())
                    .andExpect(
                            result -> {
                                assertThat(result.getResponse().getContentAsByteArray().length).isZero();
                                assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                                        .isEqualTo(productBase() + "/runtime-trace-regression-suite-definitions/" + createdId);
                            });
            verify(definitionService, times(1)).create(eq(userId), any(CreateDefinitionCommand.class));
        }

        @Test
        void p60_m33() throws Exception {
            mockMvc.perform(
                            post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-definitions/import"))
                                    .queryParam("x", "1")
                                    .contentType("application/zip")
                                    .content(validZip()))
                    .andExpect(status().isBadRequest())
                    .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
            verify(definitionService, never()).create(any(), any());
        }

        @Test
        void p60_m34() throws Exception {
            when(definitionService.create(eq(userId), any(CreateDefinitionCommand.class)))
                    .thenThrow(
                            new IllegalStateException(
                                    "A regression suite definition with this name already exists for the user",
                                    new RuntimeException()));
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-definitions/import")).contentType("application/zip").content(validZip()))
                    .andExpect(status().isConflict());
            verify(definitionService, times(1)).create(eq(userId), any(CreateDefinitionCommand.class));
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionImportPreviewController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionImportPreviewController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({
        RuntimeTraceRegressionSuiteDefinitionImportPreviewController.class,
        RuntimeTraceRegressionSuiteEndToEndContractTest.SliceDefinitionImportPreview.PreviewTestConfig.class
    })
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceDefinitionImportPreview {

        @TestConfiguration
        static class PreviewTestConfig {

            @Bean
            RuntimeTraceRegressionSuiteDefinitionImportPreviewService runtimeTraceRegressionSuiteDefinitionImportPreviewService() {
                return new RuntimeTraceRegressionSuiteDefinitionImportPreviewService();
            }
        }

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        private UUID userId;
        private UUID definitionIdPath;
        private ObjectMapper fd4;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            definitionIdPath = UUID.randomUUID();
            fd4 = P39ImportZipTestUtil.fd4ObjectMapper();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        private byte[] validDefinitionJson() throws Exception {
            RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                    new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                            definitionIdPath,
                            "import-name",
                            "desc",
                            1,
                            Instant.parse("2020-01-01T00:00:00Z"),
                            Instant.parse("2020-01-02T00:00:00Z"),
                            List.of(
                                    new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(
                                            List.of(UUID.randomUUID()))));
            return fd4.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap));
        }

        private byte[] validZip() throws Exception {
            return P39ImportZipTestUtil.buildConvergedP38Zip(
                    fd4, Instant.parse("2024-06-01T00:00:00Z"), userId, definitionIdPath, validDefinitionJson());
        }

        @Test
        void p60_m35() throws Exception {
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-definitions/import/preview")).contentType("application/zip").content(validZip()))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentType()).contains("application/json"))
                    .andExpect(jsonPath("$.importable").value(true))
                    .andExpect(jsonPath("$.warnings").isArray())
                    .andExpect(jsonPath("$.warnings.length()").value(0))
                    .andExpect(jsonPath("$.definition").isMap())
                    .andExpect(jsonPath("$.definition.name").value("import-name"));
        }

        @Test
        void p60_m36() throws Exception {
            mockMvc.perform(
                            post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-definitions/import/preview"))
                                    .queryParam("x", "1")
                                    .contentType("application/zip")
                                    .content(validZip()))
                    .andExpect(status().isBadRequest())
                    .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
        }

        @Test
        void p60_m37() throws Exception {
            mockMvc.perform(post(RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-definitions/import/preview")).contentType("application/zip").content(new byte[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionExecutionExportController")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class)
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceDefinitionExecutionExport {

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionExecutionExportService exportService;

        private UUID userId;
        private UUID definitionId;
        private UUID conversationId;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            definitionId = UUID.randomUUID();
            conversationId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        private static ResultMatcher noZipDownloadResponse() {
            return result -> {
                assertThat(result.getResponse().getContentAsByteArray().length).isZero();
                assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
                String ct = result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE);
                assertThat(
                                ct == null
                                        || (!ct.equalsIgnoreCase("application/zip")
                                                && !ct.toLowerCase(Locale.ROOT).startsWith("application/zip;")))
                        .isTrue();
            };
        }

        private static RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact sampleZip(String filename) {
            byte[] content = {1, 2, 3};
            return new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
                    filename,
                    RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact.MEDIA_TYPE_ZIP,
                    content,
                    content.length);
        }

        @Test
        void p60_m38() throws Exception {
            String fn = "runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip";
            when(exportService.exportByDefinitionId(eq(definitionId), eq(userId))).thenReturn(sampleZip(fn));
            String expectedDisposition =
                    "attachment; filename=\"runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip\"";
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, Matchers.containsString("application/zip")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
                    .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                    .andExpect(
                            result ->
                                    assertThat(result.getResponse().getContentAsByteArray().length)
                                            .isGreaterThan(0));
        }

        @Test
        void p60_m39() throws Exception {
            when(exportService.exportByDefinitionId(eq(definitionId), eq(userId)))
                    .thenThrow(new NotFoundException("missing"));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(noZipDownloadResponse());
        }

        @Test
        void p60_m40() throws Exception {
            when(exportService.exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId)))
                    .thenThrow(new NotFoundException("missing"));
            mockMvc.perform(
                            post(
                                            productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                            conversationId,
                                            definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(noZipDownloadResponse());
        }

        @Test
        void p60_m41() throws Exception {
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", "not-uuid")
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(noZipDownloadResponse());
        }

        @Test
        void p60_m42() throws Exception {
            when(exportService.exportByDefinitionId(eq(definitionId), eq(userId)))
                    .thenThrow(new RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException("big"));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(noZipDownloadResponse());
            verify(exportService, times(1)).exportByDefinitionId(eq(definitionId), eq(userId));
        }

        @Test
        void p60_m43() throws Exception {
            when(exportService.exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId)))
                    .thenThrow(new RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException("big"));
            mockMvc.perform(
                            post(
                                            productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                            conversationId,
                                            definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(noZipDownloadResponse());
            verify(exportService, times(1)).exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId));
        }

        @Test
        void p60_m44() throws Exception {
            String fn =
                    "runtime-trace-regression-suite-definition-execution_"
                            + definitionId
                            + "_conversation_"
                            + conversationId
                            + ".zip";
            when(exportService.exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId)))
                    .thenReturn(sampleZip(fn));
            mockMvc.perform(
                            post(
                                            productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                            conversationId,
                                            definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("_conversation_")));
        }

        @Test
        void p60_m45() throws Exception {
            when(exportService.exportByDefinitionId(eq(definitionId), eq(userId)))
                    .thenThrow(new RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException("x"));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(noZipDownloadResponse());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionController (read, product API)")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
    @ActiveProfiles("test")
    class SliceDefinitionReadV5 {

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionService definitionService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunExportService runExportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportService runImportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

        @MockitoBean
        private DefinitionRunZipServiceBundle runZipServices;

        private UUID userId;
        private UUID definitionId;

        @BeforeEach
        void setUp() {
            DefinitionRunZipBundleStubbing.linkMockBundleToZipServices(
                    runZipServices, runExportService, runImportService, runImportPreviewService);
            userId = UUID.randomUUID();
            definitionId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        }

        private void verifyMutatingNever() {
            verify(definitionService, never()).create(any(), any());
            verify(definitionService, never()).update(any(), any(), any());
            verify(definitionService, never()).delete(any(), any());
            verify(definitionService, never()).materializeToSuiteRequest(any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m46() throws Exception {
            when(definitionService.listSummariesForUser(userId)).thenReturn(List.of());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.definitions").isArray())
                    .andExpect(jsonPath("$.definitions", hasSize(0)))
                    .andExpect(jsonPath("$..hibernateLazyInitializer").doesNotExist());
            verify(definitionService, times(1)).listSummariesForUser(userId);
            verify(definitionService, never()).loadByIdForUser(any(), any());
            verifyMutatingNever();
        }

        @Test
        void p60_m47() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions").queryParam("a", "b"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            verify(definitionService, never()).listSummariesForUser(any());
            verify(definitionService, never()).loadByIdForUser(any(), any());
            verifyMutatingNever();
        }

        @Test
        void p60_m48() throws Exception {
            UUID tid1 = UUID.randomUUID();
            UUID tid2 = UUID.randomUUID();
            UUID conv = UUID.randomUUID();
            RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                    new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                            definitionId,
                            "suite",
                            null,
                            1,
                            Instant.parse("2024-03-01T12:00:00Z"),
                            Instant.parse("2024-03-02T12:00:00Z"),
                            List.of(
                                    new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(tid1, tid2)),
                                    new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByConversation(
                                            conv, null, null, "wf")));
            when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.of(snap));
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}", definitionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(definitionId.toString()))
                    .andExpect(jsonPath("$.name").value("suite"))
                    .andExpect(jsonPath("$.description").value(nullValue()))
                    .andExpect(jsonPath("$.schemaVersion").value(1))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists())
                    .andExpect(jsonPath("$.entries").isArray())
                    .andExpect(jsonPath("$.entries[0].entryKind").value("BY_TRACE_IDS"))
                    .andExpect(jsonPath("$.entries[0].traceIds[0]").value(tid1.toString()))
                    .andExpect(jsonPath("$.entries[0].traceIds[1]").value(tid2.toString()))
                    .andExpect(jsonPath("$.entries[0].conversationId").doesNotExist())
                    .andExpect(jsonPath("$.entries[0].createdAtFrom").doesNotExist())
                    .andExpect(jsonPath("$.entries[0].createdAtTo").doesNotExist())
                    .andExpect(jsonPath("$.entries[0].workflowName").doesNotExist())
                    .andExpect(jsonPath("$.entries[1].entryKind").value("BY_CONVERSATION"))
                    .andExpect(jsonPath("$.entries[1].conversationId").value(conv.toString()))
                    .andExpect(jsonPath("$.entries[1].traceIds").doesNotExist())
                    .andExpect(jsonPath("$..hibernateLazyInitializer").doesNotExist());
            verify(definitionService, times(1)).loadByIdForUser(definitionId, userId);
            verify(definitionService, never()).listSummariesForUser(any());
            verifyMutatingNever();
        }

        @Test
        void p60_m49() throws Exception {
            UUID missing = UUID.randomUUID();
            when(definitionService.loadByIdForUser(missing, userId)).thenReturn(Optional.empty());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}", missing))
                    .andExpect(status().isNotFound());
            verifyMutatingNever();
        }

        @Test
        void p60_m50() throws Exception {
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            verify(definitionService, never()).loadByIdForUser(any(), any());
            verifyMutatingNever();
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionController (mutate + execute, /api/test)")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
    @TestPropertySource(properties = "rag.api.product-base-path=/api/test")
    class SliceDefinitionMutateAndExecuteApiTest {

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionService definitionService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunExportService runExportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportService runImportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

        @MockitoBean
        private DefinitionRunZipServiceBundle runZipServices;

        private UUID userId;
        private UUID definitionId;
        private UUID conversationId;

        @BeforeEach
        void setUp() {
            DefinitionRunZipBundleStubbing.linkMockBundleToZipServices(
                    runZipServices, runExportService, runImportService, runImportPreviewService);
            userId = UUID.randomUUID();
            definitionId = UUID.randomUUID();
            conversationId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        private static String upsertOneByTraceIds(String name, String traceId) {
            return """
                    {"name":"%s","description":null,"entries":[{"entryKind":"BY_TRACE_IDS","traceIds":["%s"]}]}"""
                    .formatted(name, traceId);
        }

        @Test
        void p60_m51() throws Exception {
            UUID created = UUID.randomUUID();
            UUID tid = UUID.randomUUID();
            when(definitionService.create(eq(userId), any())).thenReturn(created);
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(upsertOneByTraceIds("suite", tid.toString())))
                    .andExpect(status().isCreated())
                    .andExpect(content().string(""))
                    .andExpect(
                            header()
                                    .string(
                                            "Location",
                                            productBase() + "/runtime-trace-regression-suite-definitions/" + created));
            verify(definitionService).create(eq(userId), any());
        }

        @Test
        void p60_m52() throws Exception {
            UUID tid = UUID.randomUUID();
            mockMvc.perform(
                            put(productBase() + "/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(upsertOneByTraceIds("suite", tid.toString())))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            verify(definitionService).update(eq(definitionId), eq(userId), any());
        }

        @Test
        void p60_m53() throws Exception {
            mockMvc.perform(delete(productBase() + "/runtime-trace-regression-suite-definitions/{id}", definitionId))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            verify(definitionService).delete(eq(definitionId), eq(userId));
        }

        @Test
        void p60_m54() throws Exception {
            UUID tid = UUID.randomUUID();
            when(definitionService.create(eq(userId), any())).thenThrow(new IllegalStateException("dup"));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(upsertOneByTraceIds("suite", tid.toString())))
                    .andExpect(status().isConflict())
                    .andExpect(content().string(""));
        }

        @Test
        void p60_m55() throws Exception {
            UUID tid = UUID.randomUUID();
            doThrow(new IllegalStateException("dup"))
                    .when(definitionService)
                    .update(eq(definitionId), eq(userId), any());
            mockMvc.perform(
                            put(productBase() + "/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(upsertOneByTraceIds("suite", tid.toString())))
                    .andExpect(status().isConflict())
                    .andExpect(content().string(""));
        }

        @Test
        void p60_m56() throws Exception {
            doThrow(new NotFoundException("missing"))
                    .when(definitionService)
                    .delete(eq(definitionId), eq(userId));
            mockMvc.perform(delete(productBase() + "/runtime-trace-regression-suite-definitions/{id}", definitionId))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(""));
        }

        @Test
        void p60_m57() throws Exception {
            RuntimeTraceRegressionSuiteRequest req =
                    new RuntimeTraceRegressionSuiteRequest(
                            userId,
                            List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
            var row =
                    new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                            0,
                            RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                            "echo",
                            RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                            1,
                            1,
                            1);
            var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
            when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
            when(suiteService.execute(same(req)))
                    .thenReturn(
                            new RuntimeTraceRegressionSuiteResult(
                                    RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(row)));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(
                            result -> assertThat(result.getResponse().getContentType()).contains("application/json"))
                    .andExpect(jsonPath("$.suiteOutcome").value("COMPLETED_ALL_BATCH_RETURNS"));
            InOrder inOrder = inOrder(definitionService, suiteService);
            inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
            inOrder.verify(suiteService).execute(same(req));
        }

        @Test
        void p60_m58() throws Exception {
            RuntimeTraceRegressionSuiteRequest req =
                    new RuntimeTraceRegressionSuiteRequest(
                            userId,
                            List.of(
                                    new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                            conversationId, Optional.empty(), Optional.empty(), Optional.empty())));
            var row =
                    new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                            0,
                            RuntimeTraceRegressionSuiteEntryKind.BY_CONVERSATION,
                            "echo",
                            RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                            1,
                            1,
                            1);
            var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
            when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
            when(suiteService.execute(same(req)))
                    .thenReturn(
                            new RuntimeTraceRegressionSuiteResult(
                                    RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(row)));
            mockMvc.perform(
                            post(
                                            productBase()
                                                    + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                            conversationId,
                                            definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(
                            result -> assertThat(result.getResponse().getContentType()).contains("application/json"))
                    .andExpect(jsonPath("$.suiteOutcome").value("COMPLETED_ALL_BATCH_RETURNS"));
            InOrder inOrder = inOrder(definitionService, suiteService);
            inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
            inOrder.verify(suiteService).execute(same(req));
        }

        @Test
        void p60_m59() throws Exception {
            when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId)))
                    .thenThrow(new NotFoundException("missing"));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(""));
            verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m60() throws Exception {
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute", "not-a-uuid")
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            verify(definitionService, never()).materializeToSuiteRequest(any(), any());
            verify(suiteService, never()).execute(any());
        }
    }

    @Nested
    @DisplayName("P60 slice - RuntimeTraceRegressionSuiteDefinitionController (runs under /api/v1)")
    @WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
    @ContextConfiguration(classes = RagWebMvcTestApplication.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
    @ActiveProfiles("test")
    @TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
    class SliceDefinitionRunsV1 {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private static final ObjectMapper PREVIEW_TEST_MAPPER =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        @Autowired
        private Environment environment;

        @Autowired
        private MockMvc mockMvc;

        private String productBase() {
            return RagApiTestPaths.productBasePath(environment);
        }

        @MockitoBean
        private RuntimeTraceRegressionSuiteDefinitionService definitionService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteService suiteService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunExportService runExportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportService runImportService;

        @MockitoBean
        private RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

        @MockitoBean
        private DefinitionRunZipServiceBundle runZipServices;

        private UUID userId;
        private UUID definitionId;
        private UUID runId;
        private UUID conversationId;

        @BeforeEach
        void setUp() {
            DefinitionRunZipBundleStubbing.linkMockBundleToZipServices(
                    runZipServices, runExportService, runImportService, runImportPreviewService);
            userId = UUID.randomUUID();
            definitionId = UUID.randomUUID();
            runId = UUID.randomUUID();
            conversationId = UUID.randomUUID();
            RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void clearSecurity() {
            SecurityContextHolder.clearContext();
        }

        private static RuntimeTraceRegressionSuiteDefinitionSnapshot minimalDefinitionSnapshot(UUID defId) {
            UUID tid = UUID.randomUUID();
            return new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                    defId,
                    "suite",
                    null,
                    1,
                    Instant.parse("2024-03-01T12:00:00Z"),
                    Instant.parse("2024-03-02T12:00:00Z"),
                    List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(tid))));
        }

        private String expectedLocation(UUID createdRunId) {
            return productBase() + "/runtime-trace-regression-suite-runs/" + createdRunId;
        }

        private static RuntimeTraceRegressionSuiteResult resultOf(RuntimeTraceRegressionSuiteOutcome outcome) {
            return new RuntimeTraceRegressionSuiteResult(
                    outcome, new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0), List.of());
        }

        private void verifyP50NeverMutateOrGlobalRunReads() {
            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).deleteRunForUser(any(), any());
        }

        @Test
        void p60_m61() throws Exception {
            UUID createdRunId = UUID.randomUUID();
            RuntimeTraceRegressionSuiteRequest req =
                    new RuntimeTraceRegressionSuiteRequest(
                            userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
            when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
            when(suiteService.execute(same(req)))
                    .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS));
            when(runPersistenceService.createRun(
                            eq(userId),
                            eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                            eq(Optional.of(definitionId)),
                            any()))
                    .thenReturn(createdRunId);

            MvcResult mvcResult =
                    mockMvc.perform(
                                    post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                            .contentType(MediaType.APPLICATION_JSON))
                            .andExpect(status().isCreated())
                            .andExpect(header().string(HttpHeaders.LOCATION, expectedLocation(createdRunId)))
                            .andReturn();

            assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
            verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
            verify(suiteService, times(1)).execute(same(req));
            verify(runPersistenceService, times(1))
                    .createRun(
                            eq(userId),
                            eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                            eq(Optional.of(definitionId)),
                            any());
        }

        @Test
        void p60_m62() throws Exception {
            when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId)))
                    .thenThrow(new NotFoundException("missing"));
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(""))
                    .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

            verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        }

        @Test
        void p60_m63() throws Exception {
            mockMvc.perform(
                            post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                    .queryParam("x", "1")
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""))
                    .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

            verify(definitionService, never()).materializeToSuiteRequest(any(), any());
            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        }

        @Test
        void p60_m64() throws Exception {
            RuntimeTraceRegressionSuiteRequest req =
                    new RuntimeTraceRegressionSuiteRequest(
                            userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
            when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
            when(suiteService.execute(same(req)))
                    .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED));

            MvcResult mvcResult =
                    mockMvc.perform(
                                    post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                            .contentType(MediaType.APPLICATION_JSON))
                            .andExpect(status().isBadRequest())
                            .andReturn();

            assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
            assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
            verify(suiteService, times(1)).execute(same(req));
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        }

        @Test
        void p60_m65() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());

            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"runs\":[]}", true));

            verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
            verify(runPersistenceService, times(1)).listSummariesForUserAndDefinition(eq(userId), eq(definitionId));
            verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
            verifyP50NeverMutateOrGlobalRunReads();
        }

        @Test
        void p60_m66() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            RuntimeTraceRegressionSuiteRunSnapshot snap =
                    new RuntimeTraceRegressionSuiteRunSnapshot(
                            new RuntimeTraceRegressionSuiteRunId(runId),
                            userId,
                            RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                            Optional.of(definitionId),
                            RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                            new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                            Instant.parse("2024-03-01T12:00:00Z"),
                            List.of());
            when(runPersistenceService.loadByIdForUserAndDefinition(runId, userId, definitionId))
                    .thenReturn(Optional.of(snap));

            MvcResult result =
                    mockMvc.perform(
                                    get(
                                                    productBase()
                                                            + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                                    definitionId,
                                                    runId))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.id").value(runId.toString()))
                            .andExpect(jsonPath("$.definitionId").value(definitionId.toString()))
                            .andExpect(jsonPath("$.entries").isArray())
                            .andExpect(jsonPath("$.entries", hasSize(0)))
                            .andReturn();

            JsonNode root = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            Set<String> names = new HashSet<>();
            root.fieldNames().forEachRemaining(names::add);
            assertThat(names)
                    .hasSize(11)
                    .isEqualTo(
                            Set.of(
                                    "id",
                                    "sourceType",
                                    "definitionId",
                                    "suiteOutcome",
                                    "createdAt",
                                    "requestedEntryCount",
                                    "processedEntryCount",
                                    "batchReturnedCount",
                                    "executionFailedCount",
                                    "batchNotAttemptedSubcount",
                                    "entries"));
            verifyP50NeverMutateOrGlobalRunReads();
        }

        @Test
        void p60_m67() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
            mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            r ->
                                    assertThat(r.getResolvedException())
                                            .isInstanceOf(NotFoundException.class)
                                            .hasMessage("definition not found"));
            verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
            verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
        }

        @Test
        void p60_m68() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            when(runPersistenceService.loadByIdForUserAndDefinition(runId, userId, definitionId))
                    .thenReturn(Optional.empty());

            mockMvc.perform(
                            get(
                                            productBase()
                                                    + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                            definitionId,
                                            runId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            r ->
                                    assertThat(r.getResolvedException())
                                            .isInstanceOf(NotFoundException.class)
                                            .hasMessage("run not found"));
            verifyP50NeverMutateOrGlobalRunReads();
        }

        @Test
        void p60_m69() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            byte[] body = new byte[] {10, 20, 30};
            String filename = "runtime-trace-regression-suite-definition-run_" + definitionId + "_" + runId + ".zip";
            RuntimeTraceRegressionSuiteRunExportArtifact artifact =
                    new RuntimeTraceRegressionSuiteRunExportArtifact(
                            filename, RuntimeTraceRegressionSuiteRunExportArtifact.MEDIA_TYPE_ZIP, body, body.length);
            when(runExportService.exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId))).thenReturn(artifact);

            MvcResult result =
                    mockMvc.perform(
                                    get(
                                                    productBase()
                                                            + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                                    definitionId,
                                                    runId))
                            .andExpect(status().isOk())
                            .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(body);
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/zip");
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                    .isEqualTo("attachment; filename=\"" + filename + "\"");
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(Long.toString(body.length));
            verify(runExportService, times(1)).exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId));
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
        }

        @Test
        void p60_m70() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            UUID createdId = UUID.randomUUID();
            byte[] zip = new byte[] {9, 9, 9};
            when(runImportService.importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId)))
                    .thenReturn(createdId);

            MvcResult result =
                    mockMvc.perform(
                                    post(
                                                    productBase()
                                                            + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                    definitionId)
                                            .contentType("application/zip")
                                            .content(zip))
                            .andExpect(status().isCreated())
                            .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                    .isEqualTo(productBase() + "/runtime-trace-regression-suite-runs/" + createdId);
            verify(runImportService, times(1)).importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId));
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
            verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m71() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            byte[] zip = RunImportZipTestUtil.buildSavedDefinitionScopedEmptyRunZip(runId, userId, definitionId);
            byte[] runJson = RunImportZipTestUtil.extractRunJsonBytes(zip);
            RuntimeTraceRegressionSuiteRunDetailDto detail =
                    PREVIEW_TEST_MAPPER.readValue(runJson, RuntimeTraceRegressionSuiteRunDetailDto.class);
            RuntimeTraceRegressionSuiteRunImportPreviewResponseDto dto =
                    new RuntimeTraceRegressionSuiteRunImportPreviewResponseDto(detail, true, List.of());
            when(runImportPreviewService.previewImportZipForDefinition(any(byte[].class), eq(definitionId)))
                    .thenReturn(dto);

            mockMvc.perform(
                            post(
                                            productBase()
                                                    + "/runtime-trace-regression-suite-definitions/{defId}/runs/import/preview",
                                            definitionId)
                                    .contentType("application/zip")
                                    .content(zip))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.importable").value(true))
                    .andExpect(jsonPath("$.warnings").isArray())
                    .andExpect(jsonPath("$.run.id").value(runId.toString()));

            verify(runImportPreviewService, times(1)).previewImportZipForDefinition(any(byte[].class), eq(definitionId));
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
            verify(runPersistenceService, never()).loadByIdForUser(any(), any());
            verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
            verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
            verify(runImportService, never()).importRunZip(any(), any());
            verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
            verify(suiteService, never()).execute(any());
        }

        @Test
        void p60_m72() throws Exception {
            when(definitionService.loadByIdForUser(definitionId, userId))
                    .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
            when(runPersistenceService.deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId)))
                    .thenReturn(true);

            MvcResult result =
                    mockMvc.perform(
                                    delete(
                                                    productBase()
                                                            + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                                    definitionId,
                                                    runId))
                            .andExpect(status().isNoContent())
                            .andReturn();

            assertThat(result.getResponse().getContentAsByteArray().length).isZero();
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
            verify(runPersistenceService, times(1)).deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId));
            verify(runPersistenceService, never()).deleteRunForUser(any(), any());
            verify(suiteService, never()).execute(any());
            verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        }
    }
}
