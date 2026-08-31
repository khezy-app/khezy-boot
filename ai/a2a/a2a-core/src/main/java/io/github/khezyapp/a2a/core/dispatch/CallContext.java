package io.github.khezyapp.a2a.core.dispatch;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import java.util.Map;

/** Transport-level call context: who is calling and with what attributes. */
public interface CallContext {

    CallerIdentity caller();

    Map<String, Object> attributes();
}
