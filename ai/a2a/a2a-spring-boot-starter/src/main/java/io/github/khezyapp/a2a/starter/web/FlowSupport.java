package io.github.khezyapp.a2a.starter.web;

import java.util.concurrent.Flow;

/**
 * Small {@link Flow} helpers for the JSON-RPC adapter.
 */
final class FlowSupport {

    private FlowSupport() {
    }

    /**
     * Creates a publisher that emits one item and completes.
     *
     * @param item the item
     * @param <T> the item type
     * @return the single-item publisher
     */
    static <T> Flow.Publisher<T> fromItem(final T item) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean completed;

            @Override
            public void request(final long n) {
                if (n > 0 && !completed) {
                    completed = true;
                    subscriber.onNext(item);
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                completed = true;
            }
        });
    }
}
