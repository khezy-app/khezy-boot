package io.github.khezyapp.a2a.core.store;

import org.a2aproject.sdk.spec.Task;

/** Port for sending push notifications when a task reaches a noteworthy state. */
public interface PushNotificationSender {

    void send(Task task);
}
