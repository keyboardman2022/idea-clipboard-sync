package dev.clipboard.sync.core;

import java.util.function.BooleanSupplier;

/** A UI scheduler keeps calling tick until mandatory work runs, or its view becomes obsolete. */
public final class DeferredCompletion {
    private final BooleanSupplier ready, relevant;
    private final Runnable action;
    private boolean finished;
    public DeferredCompletion(BooleanSupplier ready, BooleanSupplier relevant, Runnable action) { this.ready=ready; this.relevant=relevant; this.action=action; }
    public boolean tick() {
        if (finished) return true;
        if (!relevant.getAsBoolean()) { finished=true; return true; }
        if (ready.getAsBoolean()) { finished=true; action.run(); }
        return finished;
    }
}
