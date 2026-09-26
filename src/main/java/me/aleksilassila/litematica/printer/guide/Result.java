package me.aleksilassila.litematica.printer.guide;

import me.aleksilassila.litematica.printer.printer.action.Action;

import java.util.Optional;

public record Result(Action action, boolean passToNext, boolean skipOtherGuide) {

    public static final Result PASS = new Result(null, true, false);

    public static final Result EMPTY = PASS;

    public static final Result SKIP = new Result(null, false, true);

    public static Result success(Action action) {
        return new Result(action, false, false);
    }

    public static Result success() {
        return new Result(null, false, false);
    }

    public static Result resultIf(boolean condition, Action action) {
        return condition ? success(action) : PASS;
    }

    public static Result resultIf(boolean condition, java.util.function.Supplier<Action> supplier) {
        return condition ? success(supplier.get()) : PASS;
    }

    public Optional<Action> toOptional() {
        return Optional.ofNullable(action);
    }

    public boolean hasAction() {
        return action != null;
    }

    public void ifHasAction(java.util.function.Consumer<Action> consumer) {
        if (action != null) {
            consumer.accept(action);
        }
    }

    public Result or(Result other) {
        return passToNext ? other : this;
    }

    public Result or(java.util.function.Supplier<Result> supplier) {
        return passToNext ? supplier.get() : this;
    }
}
