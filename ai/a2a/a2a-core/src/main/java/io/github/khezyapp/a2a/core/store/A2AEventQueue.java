package io.github.khezyapp.a2a.core.store;

import java.util.function.Consumer;
import org.a2aproject.sdk.spec.EventKind;

/** Per-context event stream port. */
public interface A2AEventQueue {

    void publish(String contextId, EventKind event);

    Subscription subscribe(String contextId, Consumer<EventKind> listener);
}
