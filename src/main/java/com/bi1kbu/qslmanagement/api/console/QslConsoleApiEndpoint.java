package com.bi1kbu.qslmanagement.api.console;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.bi1kbu.qslmanagement.api.QslApiException;
import com.bi1kbu.qslmanagement.api.QslApiResponses;
import com.bi1kbu.qslmanagement.api.QslApiSupport;
import com.bi1kbu.qslmanagement.api.QslAiService;
import com.bi1kbu.qslmanagement.api.QslCardRequestService;
import com.bi1kbu.qslmanagement.api.QslConsoleActionService;
import com.bi1kbu.qslmanagement.api.QslImportExportJobService;
import com.bi1kbu.qslmanagement.api.QslLegacyMigrationService;
import com.bi1kbu.qslmanagement.api.QslLogSyncService;
import com.bi1kbu.qslmanagement.api.QslMigrationStateService;
import com.bi1kbu.qslmanagement.api.QslNotificationMailService;
import com.bi1kbu.qslmanagement.api.QslOverviewService;
import com.bi1kbu.qslmanagement.api.QslQrzAddressLookupService;
import com.bi1kbu.qslmanagement.api.QslRequestIdentitySupport;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.PluginContext;

@Component
public class QslConsoleApiEndpoint implements CustomEndpoint {

    private final QslOverviewService overviewService;
    private final QslConsoleActionService actionService;
    private final QslImportExportJobService importExportJobService;
    private final QslLegacyMigrationService legacyMigrationService;
    private final QslMigrationStateService migrationStateService;
    private final QslNotificationMailService notificationMailService;
    private final QslAiService aiService;
    private final QslQrzAddressLookupService qrzAddressLookupService;
    private final QslCardRequestService qslCardRequestService;
    private final QslLogSyncService logSyncService;
    private final PluginContext pluginContext;

    @Autowired
    public QslConsoleApiEndpoint(
        QslOverviewService overviewService,
        QslConsoleActionService actionService,
        QslImportExportJobService importExportJobService,
        QslLegacyMigrationService legacyMigrationService,
        QslMigrationStateService migrationStateService,
        QslNotificationMailService notificationMailService,
        QslAiService aiService,
        QslQrzAddressLookupService qrzAddressLookupService,
        QslCardRequestService qslCardRequestService,
        QslLogSyncService logSyncService,
        PluginContext pluginContext
    ) {
        this.overviewService = overviewService;
        this.actionService = actionService;
        this.importExportJobService = importExportJobService;
        this.legacyMigrationService = legacyMigrationService;
        this.migrationStateService = migrationStateService;
        this.notificationMailService = notificationMailService;
        this.aiService = aiService;
        this.qrzAddressLookupService = qrzAddressLookupService;
        this.qslCardRequestService = qslCardRequestService;
        this.logSyncService = logSyncService;
        this.pluginContext = pluginContext;
    }

    QslConsoleApiEndpoint(
        QslOverviewService overviewService,
        QslConsoleActionService actionService,
        QslImportExportJobService importExportJobService,
        QslLegacyMigrationService legacyMigrationService,
        QslMigrationStateService migrationStateService,
        QslNotificationMailService notificationMailService,
        QslAiService aiService
    ) {
        this(
            overviewService,
            actionService,
            importExportJobService,
            legacyMigrationService,
            migrationStateService,
            notificationMailService,
            aiService,
            null,
            null,
            null,
            PluginContext.builder().name("qsl-management").version("0.0.0").build()
        );
    }

    QslConsoleApiEndpoint(
        QslOverviewService overviewService,
        QslConsoleActionService actionService,
        QslImportExportJobService importExportJobService,
        QslLegacyMigrationService legacyMigrationService,
        QslMigrationStateService migrationStateService,
        QslNotificationMailService notificationMailService,
        QslAiService aiService,
        QslQrzAddressLookupService qrzAddressLookupService
    ) {
        this(
            overviewService,
            actionService,
            importExportJobService,
            legacyMigrationService,
            migrationStateService,
            notificationMailService,
            aiService,
            qrzAddressLookupService,
            null,
            null,
            PluginContext.builder().name("qsl-management").version("0.0.0").build()
        );
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route(GET("/reports/summary"), this::reportSummary)
            .andRoute(POST("/mail-send-confirms/{cardRecordName}/confirm"), this::confirmMailSend)
            .andRoute(POST("/receipt-confirms/{cardRecordName}/confirm"), this::confirmReceipt)
            .andRoute(POST("/mail-receive-confirms/confirm"), this::confirmMailReceive)
            .andRoute(POST("/mail-receive-confirms/{cardRecordName}/received-date"), this::updateMailReceiveDate)
            .andRoute(POST("/mail-receive-confirms/{cardRecordName}/received-record-code/migrate"),
                this::migrateReceivedRecordCode)
            .andRoute(POST("/receive-records/{receivedRecordCode}/link-outbound-card"),
                this::linkReceiveRecordToOutboundCard)
            .andRoute(POST("/receive-records/{receivedRecordCode}/create-online-card"),
                this::createOnlineCardFromReceiveRecord)
            .andRoute(POST("/card-mutations/{cardRecordName}/resend"), this::resendCard)
            .andRoute(POST("/card-mutations/{cardRecordName}/mark-error"), this::markCardIssueError)
            .andRoute(POST("/card-mutations/{cardRecordName}/mark-resend"), this::markCardAsResend)
            .andRoute(POST("/exchange-requests/{name}/approve"), this::approveExchangeRequest)
            .andRoute(POST("/exchange-requests/{name}/reject"), this::rejectExchangeRequest)
            .andRoute(POST("/exchange-requests/{name}/create-card"), this::createExchangeRequestCard)
            .andRoute(POST("/exchange-requests/{name}/mark-card-created"), this::markExchangeRequestCardCreated)
            .andRoute(POST("/exchange-requests/{name}/notify"), this::notifyExchangeRequest)
            .andRoute(POST("/qsl-card-requests/{name}/approve"), this::approveQslCardRequest)
            .andRoute(POST("/qsl-card-requests/{name}/reject"), this::rejectQslCardRequest)
            .andRoute(POST("/qsl-card-requests/{name}/retry-card-creation"), this::retryQslCardRequestCards)
            .andRoute(POST("/online-card-imports"), this::importOnlineCards)
            .andRoute(POST("/ai-config-tests"), this::testAiConfig)
            .andRoute(POST("/ai-address-normalizations/preview"), this::previewAiAddressNormalizations)
            .andRoute(POST("/ai-address-normalizations/apply"), this::applyAiAddressNormalizations)
            .andRoute(POST("/ai-online-import-parses"), this::parseAiOnlineImport)
            .andRoute(POST("/qrz-credential-tests"), this::testQrzCredential)
            .andRoute(POST("/qrz-address-lookups/preview"), this::previewQrzAddressLookup)
            .andRoute(POST("/notification-mails/apply-policy"), this::applyNotificationMailPolicy)
            .andRoute(POST("/notification-mails/send"), this::sendNotificationMail)
            .andRoute(POST("/notification-mails/batch-send"), this::batchSendNotificationMail)
            .andRoute(POST("/notification-mails/test"), this::sendTestNotificationMail)
            .andRoute(POST("/imports/precheck"), this::importPrecheck)
            .andRoute(POST("/imports/jobs"), this::createImportJob)
            .andRoute(GET("/imports/jobs/{jobName}"), this::getImportJob)
            .andRoute(GET("/imports/jobs/{jobName}/errors"), this::getImportJobErrors)
            .andRoute(GET("/imports/jobs/{jobName}/errors/download"), this::downloadImportJobErrors)
            .andRoute(GET("/migration-state"), this::getMigrationState)
            .andRoute(POST("/migrations/precheck"), this::precheckMigrations)
            .andRoute(POST("/legacy-migrations/precheck"), this::precheckLegacyMigration)
            .andRoute(POST("/legacy-migrations/execute"), this::executeLegacyMigration)
            .andRoute(POST("/exports/jobs"), this::createExportJob)
            .andRoute(GET("/exports/jobs/{jobName}"), this::getExportJob)
            .andRoute(GET("/exports/jobs/{jobName}/download"), this::downloadExportJob)
            .andRoute(GET("/log-sync/sources"), this::listLogSyncSources)
            .andRoute(GET("/log-sync/profiles"), this::listLogSyncProfiles)
            .andRoute(POST("/log-sync/profiles"), this::saveLogSyncProfile)
            .andRoute(POST("/log-sync/profiles/{profileName}/credential"), this::saveLogSyncCredential)
            .andRoute(POST("/log-sync/profiles/{profileName}/credential/clear"),
                this::clearLogSyncCredential)
            .andRoute(POST("/log-sync/profiles/{profileName}/test"), this::testLogSyncConnection)
            .andRoute(POST("/log-sync/profiles/{profileName}/sync"), this::runLogSync)
            .andRoute(POST("/log-sync/profiles/{profileName}/force-stop"), this::forceStopLogSync)
            .andRoute(GET("/log-sync/profiles/{profileName}/jobs"), this::listLogSyncJobs)
            .andRoute(POST("/log-sync/profiles/{profileName}/reset-cursor"), this::resetLogSyncCursor)
            .andRoute(DELETE("/log-sync/profiles/{profileName}"), this::deleteLogSyncProfile);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.qsl-management.bi1kbu.com/v1alpha1");
    }

    private Mono<ServerResponse> reportSummary(ServerRequest request) {
        return ensureAuthenticated(request)
            .then(overviewService.calculateReportSummary())
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> confirmMailSend(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> actionService.confirmMailSend(
                cardRecordName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> confirmReceipt(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ReceiptConfirmRequest.class)
                .defaultIfEmpty(new ReceiptConfirmRequest(""))
                .flatMap(payload -> actionService.confirmReceipt(
                    cardRecordName,
                    payload.receiptRemarks(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .map(cardRecord -> new ReceiptConfirmResponse(
                cardRecord.getMetadata().getName(),
                QslApiSupport.normalizeCallSign(cardRecord.getSpec().getCallSign()),
                QslApiSupport.normalizeCardType(cardRecord.getSpec().getCardType()),
                "已确认签收",
                QslApiSupport.nowText()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> confirmMailReceive(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(MailReceiveConfirmRequest.class)
                .switchIfEmpty(Mono.just(new MailReceiveConfirmRequest("", "QSO", "", "", "", "", "")))
                .flatMap(payload -> actionService.confirmMailReceive(
                    new QslConsoleActionService.MailReceiveConfirmCommand(
                        payload.callSign(),
                        payload.cardType(),
                        payload.sceneType(),
                        payload.receiptRemarks(),
                        payload.receivedDate(),
                        payload.offlineActivityName(),
                        payload.targetCardRecordName()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> updateMailReceiveDate(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ReceivedDateUpdateRequest.class)
                .defaultIfEmpty(new ReceivedDateUpdateRequest(""))
                .flatMap(payload -> actionService.updateMailReceiveDate(
                    cardRecordName,
                    payload.receivedDate(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> migrateReceivedRecordCode(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ReceivedRecordCodeMigrateRequest.class)
                .defaultIfEmpty(new ReceivedRecordCodeMigrateRequest("", ""))
                .flatMap(payload -> actionService.migrateReceivedRecordCode(
                    cardRecordName,
                    new QslConsoleActionService.ReceivedRecordCodeMigrateCommand(
                        payload.receivedRecordCode(),
                        payload.targetCardRecordName()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> linkReceiveRecordToOutboundCard(ServerRequest request) {
        var receivedRecordCode = request.pathVariable("receivedRecordCode");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ReceiveRecordOutboundLinkRequest.class)
                .defaultIfEmpty(new ReceiveRecordOutboundLinkRequest(""))
                .flatMap(payload -> actionService.linkReceiveRecordToOutboundCard(
                    receivedRecordCode,
                    new QslConsoleActionService.ReceiveRecordOutboundLinkCommand(
                        payload.targetCardRecordName()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> createOnlineCardFromReceiveRecord(ServerRequest request) {
        var receivedRecordCode = request.pathVariable("receivedRecordCode");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> actionService.createOnlineCardForUnmatchedReceiveRecord(
                receivedRecordCode,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> resendCard(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> actionService.resendCard(
                cardRecordName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> markCardIssueError(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(CardIssueErrorRequest.class)
                .defaultIfEmpty(new CardIssueErrorRequest(""))
                .flatMap(payload -> actionService.markCardIssueError(
                    cardRecordName,
                    payload.remarks(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> markCardAsResend(ServerRequest request) {
        var cardRecordName = request.pathVariable("cardRecordName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> actionService.markCardAsResend(
                cardRecordName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> approveExchangeRequest(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ExchangeRejectRequest.class)
                .defaultIfEmpty(new ExchangeRejectRequest(""))
                .flatMap(payload -> actionService.reviewExchangeRequest(
                    requestName,
                    true,
                    payload.reason(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> rejectExchangeRequest(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ExchangeRejectRequest.class)
                .defaultIfEmpty(new ExchangeRejectRequest("审批拒绝"))
                .flatMap(payload -> actionService.reviewExchangeRequest(
                    requestName,
                    false,
                    payload.reason(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> createExchangeRequestCard(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> actionService.createCardForApprovedExchangeRequest(
                requestName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> markExchangeRequestCardCreated(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> actionService.markExchangeRequestCardCreated(
                requestName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> notifyExchangeRequest(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> notificationMailService.sendExchangeReviewMail(
                requestName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp(),
                "换卡申请审核-手动发送"
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> approveQslCardRequest(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ExchangeRejectRequest.class)
                .defaultIfEmpty(new ExchangeRejectRequest(""))
                .flatMap(payload -> qslCardRequestService.approve(
                    requestName,
                    payload.reason(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> rejectQslCardRequest(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ExchangeRejectRequest.class)
                .defaultIfEmpty(new ExchangeRejectRequest("审批拒绝"))
                .flatMap(payload -> qslCardRequestService.reject(
                    requestName,
                    payload.reason(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> retryQslCardRequestCards(ServerRequest request) {
        var requestName = request.pathVariable("name");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> qslCardRequestService.retryCardCreation(
                requestName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> importOnlineCards(ServerRequest request) {
        return importOnlineCards(request, "手工文本导入");
    }

    private Mono<ServerResponse> importOnlineCards(ServerRequest request, String defaultSource) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(Bh6syxImportRequest.class)
                .defaultIfEmpty(new Bh6syxImportRequest("", defaultSource, List.of()))
                .flatMap(payload -> actionService.importBh6syxCards(
                    new QslConsoleActionService.Bh6syxImportCommand(
                        payload.defaultCardVersion(),
                        payload.source() == null || payload.source().isBlank() ? defaultSource : payload.source(),
                        payload.rows() == null
                            ? List.of()
                            : payload.rows().stream()
                                .map(row -> new QslConsoleActionService.Bh6syxImportRow(
                                    row.callSign(),
                                    row.status(),
                                    row.recipientName(),
                                    row.telephone(),
                                    row.address(),
                                    row.postalCode(),
                                    row.email(),
                                    row.cardVersion()
                                ))
                                .toList()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> testAiConfig(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(AiConfigTestRequest.class)
                .defaultIfEmpty(new AiConfigTestRequest(
                    new AiRuntimeConfigRequest(Boolean.FALSE, "", "", "", "", null, null, null),
                    "",
                    Boolean.FALSE
                ))
                .flatMap(payload -> aiService.testConfig(
                    new QslAiService.AiConfigTestCommand(
                        payload.config() == null ? "" : payload.config().provider(),
                        payload.config() == null ? "" : payload.config().baseUrl(),
                        payload.config() == null ? "" : payload.config().model(),
                        payload.config() == null ? "" : payload.config().secretName(),
                        payload.config() == null ? null : payload.config().temperature(),
                        payload.config() == null ? null : payload.config().timeoutSeconds(),
                        payload.config() == null ? null : payload.config().maxConcurrentRequests(),
                        payload.apiKey(),
                        payload.saveApiKey()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> previewAiAddressNormalizations(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(ignored -> request.bodyToMono(AddressNormalizationPreviewRequest.class)
                .defaultIfEmpty(new AddressNormalizationPreviewRequest(List.of()))
                .flatMap(payload -> aiService.previewAddressNormalizations(
                    new QslAiService.AddressNormalizationPreviewCommand(
                        payload.rows() == null ? List.of() : payload.rows()
                    )
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> applyAiAddressNormalizations(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(AddressNormalizationApplyRequest.class)
                .defaultIfEmpty(new AddressNormalizationApplyRequest(List.of()))
                .flatMap(payload -> aiService.applyAddressNormalizations(
                    new QslAiService.AddressNormalizationApplyCommand(
                        payload.rows() == null ? List.of() : payload.rows()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> parseAiOnlineImport(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(ignored -> request.bodyToMono(OnlineImportParseRequest.class)
                .defaultIfEmpty(new OnlineImportParseRequest("", "", Boolean.FALSE))
                .flatMap(payload -> aiService.parseOnlineImport(
                    new QslAiService.OnlineImportParseCommand(
                        payload.limitToSingle() == null
                            ? "batch"
                            : (payload.limitToSingle() ? "single" : "batch"),
                        payload.text(),
                        payload.defaultCardVersion()
                    )
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> testQrzCredential(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(QrzCredentialTestRequest.class)
                .defaultIfEmpty(new QrzCredentialTestRequest(
                    "", Boolean.FALSE, "", "", "", "", "", "", null, Boolean.FALSE, ""
                ))
                .flatMap(payload -> qrzAddressLookupService.testAndSaveCredential(
                    new QslQrzAddressLookupService.QrzCredentialCommand(
                        payload.provider(),
                        payload.enabled(),
                        payload.username(),
                        payload.password(),
                        payload.cookie(),
                        payload.secretName(),
                        payload.baseUrl(),
                        payload.lookupUrlTemplate(),
                        payload.timeoutSeconds(),
                        payload.saveCredential(),
                        payload.testCallSign()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> listLogSyncSources(ServerRequest request) {
        return ensureAuthenticated(request)
            .then(Mono.fromSupplier(logSyncService::supportedSources))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> listLogSyncProfiles(ServerRequest request) {
        return ensureAuthenticated(request)
            .then(logSyncService.listProfiles())
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> saveLogSyncProfile(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(LogSyncProfileRequest.class)
                .defaultIfEmpty(new LogSyncProfileRequest(
                    "", "", "", null, null, null, "", "", null
                ))
                .flatMap(payload -> logSyncService.saveProfile(
                    new QslLogSyncService.SaveProfileCommand(
                        payload.sourceType(),
                        payload.myCallSign(),
                        payload.accountName(),
                        payload.enabled(),
                        payload.autoSyncEnabled(),
                        payload.syncIntervalMinutes(),
                        payload.defaultSceneType(),
                        payload.conflictStrategy(),
                        payload.maxRecordsPerSync()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> saveLogSyncCredential(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(LogSyncCredentialRequest.class)
                .defaultIfEmpty(new LogSyncCredentialRequest("", ""))
                .flatMap(payload -> logSyncService.saveCredential(
                    profileName,
                    new QslLogSyncService.SaveCredentialCommand(
                        payload.accountName(),
                        payload.password()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> clearLogSyncCredential(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> logSyncService.clearCredential(
                profileName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> resetLogSyncCursor(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> logSyncService.resetCursor(
                profileName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> deleteLogSyncProfile(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> logSyncService.deleteProfile(
                profileName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .then(QslApiResponses.ok(Boolean.TRUE))
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> testLogSyncConnection(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> logSyncService.testConnection(
                profileName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> runLogSync(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> logSyncService.startSync(
                profileName,
                "MANUAL",
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> forceStopLogSync(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> logSyncService.forceStop(
                profileName,
                authenticatedOperator.name(),
                authenticatedOperator.clientIp()
            ))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> listLogSyncJobs(ServerRequest request) {
        var profileName = request.pathVariable("profileName");
        return ensureAuthenticated(request)
            .then(logSyncService.listJobs(profileName))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> previewQrzAddressLookup(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(ignored -> request.bodyToMono(QrzAddressLookupRequest.class)
                .defaultIfEmpty(new QrzAddressLookupRequest("", ""))
                .flatMap(payload -> qrzAddressLookupService.lookupAddress(
                    new QslQrzAddressLookupService.QrzAddressLookupCommand(
                        payload.provider(),
                        payload.callSign()
                    )
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> sendNotificationMail(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(NotificationMailSendRequest.class)
                .flatMap(payload -> notificationMailService.sendSingle(
                    payload.cardRecordName(),
                    payload.scene(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp(),
                    payload.source()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> applyNotificationMailPolicy(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(NotificationMailSendRequest.class)
                .flatMap(payload -> {
                    var scene = QslNotificationMailService.MailScene.fromCode(payload.scene());
                    if (scene == null) {
                        return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST,
                            "QSL-400-0001", "邮件场景不支持"));
                    }
                    return notificationMailService.applyAutomaticPolicy(
                        payload.cardRecordName(),
                        scene,
                        authenticatedOperator.name(),
                        authenticatedOperator.clientIp(),
                        payload.source()
                    );
                }))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> batchSendNotificationMail(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(NotificationMailBatchSendRequest.class)
                .flatMap(payload -> notificationMailService.sendBatch(
                    payload.cardRecordNames(),
                    payload.scene(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp(),
                    payload.source()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> sendTestNotificationMail(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(NotificationMailTestRequest.class)
                .defaultIfEmpty(new NotificationMailTestRequest("created"))
                .flatMap(payload -> notificationMailService.sendTestMail(
                    payload.scene(),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> importPrecheck(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(ignored -> request.bodyToMono(ImportJobRequest.class)
                .defaultIfEmpty(new ImportJobRequest(
                    "csv",
                    "skip",
                    "",
                    List.of()
                ))
                .flatMap(payload -> importExportJobService.precheckImport(toImportCommand(payload))))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> createImportJob(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ImportJobRequest.class)
                .defaultIfEmpty(new ImportJobRequest(
                    "csv",
                    "skip",
                    "",
                    List.of()
                ))
                .flatMap(payload -> importExportJobService.executeImportJob(
                    toImportCommand(payload),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> getImportJob(ServerRequest request) {
        var jobName = request.pathVariable("jobName");
        return ensureAuthenticated(request)
            .then(importExportJobService.getJob(jobName))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> getImportJobErrors(ServerRequest request) {
        var jobName = request.pathVariable("jobName");
        return ensureAuthenticated(request)
            .then(importExportJobService.getJobErrors(jobName))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> downloadImportJobErrors(ServerRequest request) {
        var jobName = request.pathVariable("jobName");
        return ensureAuthenticated(request)
            .then(importExportJobService.buildImportErrorDownload(jobName))
            .flatMap(payload -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .bodyValue(payload.content()))
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> getMigrationState(ServerRequest request) {
        return ensureAuthenticated(request)
            .then(migrationStateService.getMigrationState(pluginContext.getVersion()))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> precheckMigrations(ServerRequest request) {
        return ensureAuthenticated(request)
            .then(migrationStateService.precheckMigrations(pluginContext.getVersion()))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> precheckLegacyMigration(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(ignored -> legacyMigrationService.precheckLegacyMigration())
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> executeLegacyMigration(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(LegacyMigrationRequest.class)
                .defaultIfEmpty(new LegacyMigrationRequest("current-storage", ""))
                .flatMap(payload -> legacyMigrationService.executeLegacyMigration(
                    new QslLegacyMigrationService.LegacyMigrationCommand(
                        payload.mode(),
                        payload.confirmText()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> createExportJob(ServerRequest request) {
        return ensureAuthenticated(request)
            .flatMap(authenticatedOperator -> request.bodyToMono(ExportJobRequest.class)
                .defaultIfEmpty(new ExportJobRequest("", "csv"))
                .flatMap(payload -> importExportJobService.createExportJob(
                    new QslImportExportJobService.CreateExportJobCommand(
                        payload.dataset(),
                        payload.format()
                    ),
                    authenticatedOperator.name(),
                    authenticatedOperator.clientIp()
                )))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> getExportJob(ServerRequest request) {
        var jobName = request.pathVariable("jobName");
        return ensureAuthenticated(request)
            .then(importExportJobService.getJob(jobName))
            .flatMap(QslApiResponses::ok)
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<ServerResponse> downloadExportJob(ServerRequest request) {
        var jobName = request.pathVariable("jobName");
        return ensureAuthenticated(request)
            .then(importExportJobService.buildExportDownload(jobName))
            .flatMap(payload -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .bodyValue(payload.content()))
            .onErrorResume(QslApiResponses::handleError);
    }

    private Mono<AuthenticatedOperator> ensureAuthenticated(ServerRequest request) {
        return request.principal()
            .switchIfEmpty(Mono.error(new QslApiException(HttpStatus.UNAUTHORIZED, "QSL-401-0001", "未认证")))
            .flatMap(principal -> {
                var principalName = principal.getName();
                if (principalName == null || principalName.isBlank()
                    || "anonymousUser".equalsIgnoreCase(principalName)) {
                    return Mono.error(new QslApiException(HttpStatus.UNAUTHORIZED, "QSL-401-0001", "未认证"));
                }
                return Mono.just(new AuthenticatedOperator(
                    principalName,
                    QslRequestIdentitySupport.resolveClientIp(request)
                ));
            });
    }

    private QslImportExportJobService.ExecuteImportJobCommand toImportCommand(ImportJobRequest payload) {
        return new QslImportExportJobService.ExecuteImportJobCommand(
            payload.format(),
            payload.strategy(),
            payload.sourceFile(),
            payload.datasets() == null
                ? List.of()
                : payload.datasets().stream()
                    .map(item -> new QslImportExportJobService.ImportDatasetPayload(
                        item.dataset(),
                        item.rows() == null ? List.of() : item.rows()
                    ))
                    .toList()
        );
    }

    private record AuthenticatedOperator(String name, String clientIp) {
    }

    private record MailReceiveConfirmRequest(
        String callSign,
        String cardType,
        String sceneType,
        String receiptRemarks,
        String receivedDate,
        String offlineActivityName,
        String targetCardRecordName
    ) {
    }

    private record ReceiptConfirmRequest(String receiptRemarks) {
    }

    private record ReceiptConfirmResponse(
        String cardRecordName,
        String callSign,
        String cardType,
        String message,
        String handledAt
    ) {
    }

    private record ReceivedDateUpdateRequest(String receivedDate) {
    }

    private record ReceivedRecordCodeMigrateRequest(String receivedRecordCode, String targetCardRecordName) {
    }

    private record ReceiveRecordOutboundLinkRequest(String targetCardRecordName) {
    }

    private record CardIssueErrorRequest(String remarks) {
    }

    private record ExchangeRejectRequest(String reason) {
    }

    private record Bh6syxImportRequest(
        String defaultCardVersion,
        String source,
        List<Bh6syxImportRowRequest> rows
    ) {
    }

    private record Bh6syxImportRowRequest(
        String callSign,
        String status,
        String recipientName,
        String telephone,
        String address,
        String postalCode,
        String email,
        String cardVersion
    ) {
    }

    private record AiConfigTestRequest(
        AiRuntimeConfigRequest config,
        String apiKey,
        Boolean saveApiKey
    ) {
    }

    private record AiRuntimeConfigRequest(
        Boolean enabled,
        String provider,
        String baseUrl,
        String model,
        String secretName,
        Double temperature,
        Integer timeoutSeconds,
        Integer maxConcurrentRequests
    ) {
    }

    private record AddressNormalizationPreviewRequest(List<QslAiService.AddressNormalizationInput> rows) {
    }

    private record AddressNormalizationApplyRequest(List<QslAiService.AddressNormalizationApplyItem> rows) {
    }

    private record OnlineImportParseRequest(String text, String defaultCardVersion, Boolean limitToSingle) {
    }

    private record QrzCredentialTestRequest(
        String provider,
        Boolean enabled,
        String username,
        String password,
        String cookie,
        String secretName,
        String baseUrl,
        String lookupUrlTemplate,
        Integer timeoutSeconds,
        Boolean saveCredential,
        String testCallSign
    ) {
    }

    private record QrzAddressLookupRequest(String provider, String callSign) {
    }

    private record ImportJobRequest(
        String format,
        String strategy,
        String sourceFile,
        List<ImportDatasetRequest> datasets
    ) {
    }

    private record ImportDatasetRequest(
        String dataset,
        List<Map<String, String>> rows
    ) {
    }

    private record LegacyMigrationRequest(String mode, String confirmText) {
    }

    private record ExportJobRequest(String dataset, String format) {
    }

    private record NotificationMailSendRequest(String cardRecordName, String scene, String source) {
    }

    private record NotificationMailBatchSendRequest(List<String> cardRecordNames, String scene, String source) {
    }

    private record LogSyncProfileRequest(
        String sourceType,
        String myCallSign,
        String accountName,
        Boolean enabled,
        Boolean autoSyncEnabled,
        Integer syncIntervalMinutes,
        String defaultSceneType,
        String conflictStrategy,
        Integer maxRecordsPerSync
    ) {
    }

    private record LogSyncCredentialRequest(String accountName, String password) {
    }

    private record NotificationMailTestRequest(String scene) {
    }
}
