package me.aleksilassila.litematica.printer.printer;

import java.util.HashMap;
import java.util.Map;

final class InteractionScreenSessions {
    private static final long PRINT_SIGN_EDIT_ARM_TIMEOUT_NANOS = 30_000_000_000L;
    private static final long PRINT_SIGN_EDIT_RESPONSE_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long PRINT_SIGN_EDIT_PRUNE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long TASK_ANVIL_SCREEN_RESPONSE_TIMEOUT_NANOS = 5_000_000_000L;
    private static final int MAX_PENDING_TASK_ANVIL_SCREENS = 64;

    private final Map<Long, Long> pendingPrintSignEdits = new HashMap<>();
    private long nextPrintSignEditPruneNanos;
    private int pendingTaskAnvilScreens;
    private long taskAnvilScreenSuppressionDeadlineNanos;
    private long manualAnvilScreenAllowanceDeadlineNanos;

    void armPrintSignEdit(long blockKey, long nowNanos) {
        this.pruneExpiredPrintSignEdits(nowNanos);
        this.pendingPrintSignEdits.put(blockKey, nowNanos + PRINT_SIGN_EDIT_ARM_TIMEOUT_NANOS);
    }

    void confirmPrintSignEditSent(long blockKey, long nowNanos) {
        this.pendingPrintSignEdits.replace(
                blockKey,
                nowNanos + PRINT_SIGN_EDIT_RESPONSE_TIMEOUT_NANOS
        );
    }

    void cancelPrintSignEdit(long blockKey) {
        this.pendingPrintSignEdits.remove(blockKey);
    }

    boolean consumePrintSignEdit(long blockKey, long nowNanos) {
        Long deadline = this.pendingPrintSignEdits.remove(blockKey);
        return deadline != null && deadline >= nowNanos;
    }

    void armTaskAnvilScreen(long nowNanos) {
        this.pendingTaskAnvilScreens = Math.min(
                MAX_PENDING_TASK_ANVIL_SCREENS,
                this.pendingTaskAnvilScreens + 1
        );
        this.taskAnvilScreenSuppressionDeadlineNanos =
                nowNanos + TASK_ANVIL_SCREEN_RESPONSE_TIMEOUT_NANOS;
    }

    boolean consumeTaskAnvilScreenSuppression(long nowNanos) {
        if (this.pendingTaskAnvilScreens <= 0) {
            return false;
        }
        if (this.taskAnvilScreenSuppressionDeadlineNanos < nowNanos) {
            this.clearTaskAnvilScreenSuppressions();
            return false;
        }
        this.pendingTaskAnvilScreens--;
        if (this.pendingTaskAnvilScreens == 0) {
            this.taskAnvilScreenSuppressionDeadlineNanos = 0L;
        }
        return true;
    }

    void prioritizeManualAnvilScreen(long nowNanos) {
        this.clearTaskAnvilScreenSuppressions();
        this.manualAnvilScreenAllowanceDeadlineNanos =
                nowNanos + TASK_ANVIL_SCREEN_RESPONSE_TIMEOUT_NANOS;
    }

    boolean consumeManualAnvilScreenAllowance(long nowNanos) {
        if (!this.hasManualAnvilScreenAllowance(nowNanos)) {
            return false;
        }
        this.manualAnvilScreenAllowanceDeadlineNanos = 0L;
        return true;
    }

    void reset() {
        this.pendingPrintSignEdits.clear();
        this.nextPrintSignEditPruneNanos = 0L;
        this.clearTaskAnvilScreenSuppressions();
        this.manualAnvilScreenAllowanceDeadlineNanos = 0L;
    }

    boolean hasManualAnvilScreenAllowance(long nowNanos) {
        if (this.manualAnvilScreenAllowanceDeadlineNanos == 0L) {
            return false;
        }
        if (this.manualAnvilScreenAllowanceDeadlineNanos < nowNanos) {
            this.manualAnvilScreenAllowanceDeadlineNanos = 0L;
            return false;
        }
        return true;
    }

    private void clearTaskAnvilScreenSuppressions() {
        this.pendingTaskAnvilScreens = 0;
        this.taskAnvilScreenSuppressionDeadlineNanos = 0L;
    }

    private void pruneExpiredPrintSignEdits(long nowNanos) {
        if (nowNanos < this.nextPrintSignEditPruneNanos) {
            return;
        }
        this.nextPrintSignEditPruneNanos = nowNanos + PRINT_SIGN_EDIT_PRUNE_INTERVAL_NANOS;
        this.pendingPrintSignEdits.entrySet().removeIf(entry -> entry.getValue() < nowNanos);
    }
}
