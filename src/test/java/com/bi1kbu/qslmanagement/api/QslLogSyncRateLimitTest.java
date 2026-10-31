package com.bi1kbu.qslmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 外部日志同步的「上游限流」处理测试。
 *
 * <p>LoTW 对同一账号的报表下载限制为每分钟一次，超限返回 503 的 {@code Page Request Limit}
 * 页面。这类响应既不是凭据错误也不是网络故障，必须能单独识别出来，否则用户会不断去改密码。
 */
class QslLogSyncRateLimitTest {

    private static final String LIMIT_PAGE = """
        <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" \
        "http://www.w3.org/TR/html4/loose.dtd">
        <HTML><HEAD><TITLE>Error 503</TITLE></HEAD>
        <BODY><H3>Error 503</H3>
        <B>Page Request Limit!</B><br>
        <P>Only one page request per user at a time. Please wait one minute and try again.
        </BODY></HTML>
        """;

    @Test
    void shouldRecognizePageRequestLimitPage() {
        assertThat(QslLotwLogSyncSource.looksLikeRequestLimit(LIMIT_PAGE)).isTrue();
        assertThat(QslLotwLogSyncSource.looksLikeRequestLimit("PAGE REQUEST LIMIT")).isTrue();
        assertThat(QslLotwLogSyncSource.looksLikeRequestLimit("Only one page request per user")).isTrue();
        assertThat(QslLotwLogSyncSource.looksLikeRequestLimit("")).isFalse();
        assertThat(QslLotwLogSyncSource.looksLikeRequestLimit(null)).isFalse();
        assertThat(QslLotwLogSyncSource.looksLikeRequestLimit("<EOH><APP_LoTW_LASTQSORX:19>2026-10-30 12:34:56"))
            .isFalse();
    }

    @Test
    void shouldReportRateLimitAsTooManyRequestsAfterRetry() throws Exception {
        var httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn(LIMIT_PAGE);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var source = new QslLotwLogSyncSource(httpClient, Duration.ZERO);
        var request = new QslLogSyncSource.FetchRequest("BG2GVP", "bg2gvp", "secret", "", 250);

        assertThatThrownBy(() -> source.fetch(request).block())
            .isInstanceOf(QslApiException.class)
            .satisfies(error -> {
                var api = (QslApiException) error;
                // 429：上游限流，不是凭据错误（QSL-401）也不是网关错误（QSL-502）。
                assertThat(api.getCode()).isEqualTo("QSL-429-LS-0001");
                assertThat(api.getMessage()).contains("Page Request Limit");
            });
        // 命中限流会自动重试一次，所以实际发出了两次请求。
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldNotTreatRateLimitBodyAsCredentialProblem() throws Exception {
        var httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        // 状态码被前置代理改写成 200，只能靠正文识别限流。
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(LIMIT_PAGE);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var source = new QslLotwLogSyncSource(httpClient, Duration.ZERO);
        var request = new QslLogSyncSource.FetchRequest("BG2GVP", "bg2gvp", "secret", "", 250);

        assertThatThrownBy(() -> source.fetch(request).block())
            .isInstanceOf(QslApiException.class)
            .satisfies(error -> assertThat(((QslApiException) error).getCode())
                .isEqualTo("QSL-429-LS-0001"));
    }

    @Test
    void shouldUseConfiguredAccountNameAsLotwLogin() {
        // 官方说明 login 是 LoTW 用户名，多数等于主呼号但不总是，所以优先用配置的账号。
        assertThat(QslLotwLogSyncSource.resolveLoginName(
            new QslLogSyncSource.FetchRequest("BG2GVP", "bg2gvp-user", "secret", "", 250)))
            .isEqualTo("bg2gvp-user");
        // 账号留空时回落到本台呼号。
        assertThat(QslLotwLogSyncSource.resolveLoginName(
            new QslLogSyncSource.FetchRequest("bg2gvp", "  ", "secret", "", 250)))
            .isEqualTo("BG2GVP");
        assertThat(QslLotwLogSyncSource.resolveLoginName(null)).isEmpty();
    }

    @Test
    void shouldDeclareRequestIntervalForLotwOnly() {
        assertThat(new QslLotwLogSyncSource().minRequestIntervalSeconds()).isEqualTo(60);
        // QRZ Logbook 没有这条限制，默认值为 0。
        assertThat(new QslQrzLogbookSyncSource().minRequestIntervalSeconds()).isZero();
    }

    @Test
    void shouldKeyRequestIntervalBySourceAndAccount() {
        var lotw = new QslLotwLogSyncSource();
        assertThat(QslLogSyncService.requestKey(lotw, "bg2gvp")).isEqualTo("LOTW|BG2GVP");
        // 大小写与首尾空白不影响归并，否则同一个人换个写法就能绕过间隔。
        assertThat(QslLogSyncService.requestKey(lotw, " bg2gvp ")).isEqualTo("LOTW|BG2GVP");
        assertThat(QslLogSyncService.requestKey(new QslQrzLogbookSyncSource(), "AJ3X-4Y7Q")).isEqualTo("QRZ_LOGBOOK|AJ3X-4Y7Q");
    }
}
