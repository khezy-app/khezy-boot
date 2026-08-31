package io.github.khezyapp.a2a.coredef.store;

import io.github.khezyapp.a2a.core.store.PushNotificationSender;
import org.a2aproject.sdk.spec.Task;

/**
 * Do-nothing {@link PushNotificationSender}. Replace with a real implementation in
 * applications that need push notifications on noteworthy task states.
 */
public final class NoopPushNotificationSender implements PushNotificationSender {

    @Override
    public void send(final Task task) {
        // intentionally no-op
    }
}
