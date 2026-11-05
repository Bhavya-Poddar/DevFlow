package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import com.devflow.devflow.security.GitHubWebhookRequestSizeFilter;

class GitHubWebhookRequestSizeFilterTests {
    @Test
    void rejectsDeclaredOversizeBeforeReadingRequestBody() throws Exception {
        GitHubWebhookRequestSizeFilter filter = new GitHubWebhookRequestSizeFilter(8);
        CountingRequest request = webhookRequest("123456789");
        request.addHeader("Content-Length", "9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(response.getStatus() == 413);
        assertFalse(chainCalled.get());
        assertTrue(request.bytesRead() == 0); // Content-Length rejected before reading the body.
    }

    @Test
    void rejectsUnknownLengthAsSoonAsItReadsTheFirstByteOverTheLimit() throws Exception {
        GitHubWebhookRequestSizeFilter filter = new GitHubWebhookRequestSizeFilter(8);
        CountingRequest request = webhookRequest("123456789-more-data");
        request.useUnknownContentLength();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(response.getStatus() == 413);
        assertFalse(chainCalled.get());
        // Only max + 1 bytes are consumed to prove the body is too large.
        assertTrue(request.bytesRead() == 9);
    }

    @Test
    void replaysAnInLimitBodyUnchangedForRawBodySignatureVerification() throws Exception {
        byte[] expected = "{\"event\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        GitHubWebhookRequestSizeFilter filter = new GitHubWebhookRequestSizeFilter(expected.length);
        CountingRequest request = webhookRequest(new String(expected, StandardCharsets.UTF_8));
        request.useUnknownContentLength();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> forwardedBody = new AtomicReference<>();

        filter.doFilter(request, response, (wrapped, res) -> forwardedBody.set(wrapped.getInputStream().readAllBytes()));

        assertArrayEquals(expected, forwardedBody.get());
        assertTrue(response.getStatus() < 400);
    }

    @Test
    void doesNotApplyTheWebhookLimitToOtherEndpoints() throws Exception {
        GitHubWebhookRequestSizeFilter filter = new GitHubWebhookRequestSizeFilter(8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/issues");
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertTrue(response.getStatus() < 400);
    }

    private CountingRequest webhookRequest(String content) {
        CountingRequest request = new CountingRequest();
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static class CountingRequest extends MockHttpServletRequest {
        private int bytesRead;
        private boolean unknownContentLength;

        private CountingRequest() {
            super("POST", "/api/integrations/github/webhook");
        }

        @Override
        public ServletInputStream getInputStream() {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                @Override public int read() throws IOException {
                    int value = delegate.read();
                    if (value >= 0) bytesRead++;
                    return value;
                }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    int count = delegate.read(bytes, offset, length);
                    if (count > 0) bytesRead += count;
                    return count;
                }
                @Override public boolean isFinished() { return delegate.isFinished(); }
                @Override public boolean isReady() { return delegate.isReady(); }
                @Override public void setReadListener(ReadListener readListener) { delegate.setReadListener(readListener); }
            };
        }

        private int bytesRead() {
            return bytesRead;
        }

        private void useUnknownContentLength() {
            unknownContentLength = true;
        }

        @Override
        public long getContentLengthLong() {
            return unknownContentLength ? -1 : super.getContentLengthLong();
        }
    }
}
