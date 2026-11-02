package dev.clipboard.sync.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Preserves reports for partially completed mutations, including canceled IDE tasks. */
public final class CompletionRelay<T> {
    private final Consumer<T> consumer;
    private final AtomicBoolean delivered = new AtomicBoolean();
    public CompletionRelay(Consumer<T> consumer) { this.consumer = consumer; }
    public void complete(T value, boolean cancelled) {
        if (value != null && (!cancelled || value instanceof SyncModel.ApplyResult) && delivered.compareAndSet(false, true)) consumer.accept(value);
    }
}
