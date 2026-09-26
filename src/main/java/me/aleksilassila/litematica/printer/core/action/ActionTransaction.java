package me.aleksilassila.litematica.printer.core.action;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeEpoch;

import java.util.Objects;

public final class ActionTransaction {
    private final ActionTicket ticket;
    private ActionResult state = ActionResult.ADMITTED;
    private int attempts;
    private long retryAtTick = Long.MIN_VALUE;

    ActionTransaction(ActionTicket ticket) {
        this.ticket = Objects.requireNonNull(ticket, "ticket");
    }

    public ActionTicket ticket() {
        return this.ticket;
    }

    public ActionRequest request() {
        return this.ticket.request();
    }

    public synchronized ActionResult state() {
        return this.state;
    }

    public synchronized int attempts() {
        return this.attempts;
    }

    public synchronized ActionResult poll(RuntimeEpoch currentEpoch, long currentTick, long nowNanos) {
        if (this.isTerminal()) {
            return this.state;
        }
        if (!this.belongsTo(currentEpoch)) {
            return this.transition(ActionResult.STALE);
        }
        long deadline = this.request().deadlineNanos();
        if (deadline > 0L && nowNanos >= deadline) {
            return this.transition(ActionResult.FAILED);
        }
        if (this.state == ActionResult.RETRY && currentTick >= this.retryAtTick) {
            return this.transition(ActionResult.ADMITTED);
        }
        return this.state;
    }

    public synchronized ActionResult markSent(RuntimeEpoch currentEpoch) {
        if (this.isTerminal()) {
            return this.state;
        }
        if (!this.belongsTo(currentEpoch)) {
            return this.transition(ActionResult.STALE);
        }
        this.attempts++;
        if (this.request().confirmation() == ConfirmationPolicy.NONE) {
            return this.transition(ActionResult.CONFIRMED);
        }
        return this.transition(ActionResult.WAITING_CONFIRMATION);
    }

    public synchronized ActionResult confirm(RuntimeEpoch currentEpoch) {
        if (this.isTerminal()) {
            return this.state;
        }
        if (!this.belongsTo(currentEpoch)) {
            return this.transition(ActionResult.STALE);
        }
        if (this.state != ActionResult.WAITING_CONFIRMATION
                && this.state != ActionResult.SENT) {
            throw new IllegalStateException("action is not waiting for confirmation: " + this.state);
        }
        return this.transition(ActionResult.CONFIRMED);
    }

    public synchronized ActionResult reject(RuntimeEpoch currentEpoch, long currentTick) {
        if (this.isTerminal()) {
            return this.state;
        }
        if (!this.belongsTo(currentEpoch)) {
            return this.transition(ActionResult.STALE);
        }
        if (this.state == ActionResult.ADMITTED || this.state == ActionResult.RETRY) {
            this.attempts++;
        }
        if (this.attempts < this.request().retry().maxAttempts()) {
            this.retryAtTick = currentTick + this.request().retry().backoffTicks();
            return this.transition(ActionResult.RETRY);
        }
        return this.transition(ActionResult.FAILED);
    }

    public synchronized ActionResult stale() {
        if (!this.isTerminal()) {
            this.transition(ActionResult.STALE);
        }
        return this.state;
    }

    public synchronized boolean isTerminal() {
        return this.state == ActionResult.CONFIRMED
                || this.state == ActionResult.STALE
                || this.state == ActionResult.FAILED;
    }

    private boolean belongsTo(RuntimeEpoch currentEpoch) {
        return this.request().epoch().equals(Objects.requireNonNull(currentEpoch, "currentEpoch"));
    }

    private ActionResult transition(ActionResult next) {
        this.state = next;
        return next;
    }
}
