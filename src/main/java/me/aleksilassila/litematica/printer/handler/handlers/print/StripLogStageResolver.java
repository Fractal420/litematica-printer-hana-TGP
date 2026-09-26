package me.aleksilassila.litematica.printer.handler.handlers.print;

final class StripLogStageResolver {
    enum Observation {
        COMPLETE,
        SOURCE_LOG,
        REPLACEABLE,
        BLOCKED
    }

    private StripLogStageResolver() {
    }

    static StripLogTaskStage resolve(
            StripLogTaskStage stage,
            Observation observation,
            boolean stalled,
            boolean retryReady
    ) {
        if (observation == Observation.COMPLETE) return StripLogTaskStage.COMPLETE;
        if (observation == Observation.SOURCE_LOG) {
            return stage == StripLogTaskStage.WAIT_STRIP_CONFIRM && !stalled
                    ? StripLogTaskStage.WAIT_STRIP_CONFIRM
                    : StripLogTaskStage.STRIP_LOG;
        }
        if (observation == Observation.REPLACEABLE) {
            if (stage == StripLogTaskStage.RETRY_WAIT && !retryReady) return stage;
            return stage == StripLogTaskStage.WAIT_LOG_CONFIRM && !stalled
                    ? StripLogTaskStage.WAIT_LOG_CONFIRM
                    : StripLogTaskStage.PLACE_LOG;
        }
        return StripLogTaskStage.COMPLETE;
    }
}
