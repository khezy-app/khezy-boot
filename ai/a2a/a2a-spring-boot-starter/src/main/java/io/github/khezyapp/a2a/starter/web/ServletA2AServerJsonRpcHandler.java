package io.github.khezyapp.a2a.starter.web;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/**
 * Servlet transport for the transport-neutral {@link A2AServerJsonRpcHandler}.
 *
 * <p>Implements only the two write hooks: a JSON body written to the response writer, and
 * an SSE stream with {@code request(1)} back-pressure driven by the servlet writer.</p>
 */
public class ServletA2AServerJsonRpcHandler
        extends A2AServerJsonRpcHandler<HttpServletRequest, HttpServletResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(ServletA2AServerJsonRpcHandler.class);

    /**
     * Creates the servlet handler.
     *
     * @param jsonRpcHandler the SDK JSON-RPC handler
     */
    public ServletA2AServerJsonRpcHandler(final JSONRPCHandler jsonRpcHandler) {
        super(jsonRpcHandler);
    }

    @Override
    protected void writeJsonResponse(final HttpServletResponse response, final String body) {
        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            final var writer = response.getWriter();
            writer.write(body);
            writer.flush();
        } catch (final IOException e) {
            LOG.warn("Failed to write the A2A JSON-RPC response", e);
        }
    }

    @Override
    protected void writeSse(final HttpServletResponse response,
                            final Flow.Publisher<? extends A2AResponse<?>> publisher) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        final var done = new CountDownLatch(1);
        try {
            final var writer = response.getWriter();
            final var eventId = new AtomicLong();
            publisher.subscribe(new Flow.Subscriber<A2AResponse<?>>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(1);
                }

                @Override
                public void onNext(final A2AResponse<?> item) {
                    try {
                        writer.write(ResponseFormatter.formatResponseAsSse(item, eventId.getAndIncrement()));
                        writer.flush();
                        if (writer.checkError()) {
                            subscription.cancel();
                            done.countDown();
                            return;
                        }
                        subscription.request(1);
                    } catch (final RuntimeException e) {
                        subscription.cancel();
                        done.countDown();
                    }
                }

                @Override
                public void onError(final Throwable throwable) {
                    LOG.warn("A2A event stream failed", throwable);
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });
            done.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            LOG.warn("Failed to write the A2A SSE response", e);
        }
    }
}
