package ru.mtuci.plugins;

public interface Plugin {
    default Result check(Request.OID oid) { return new Result.Undefined(); }
    default Result check(Request.Named named) { return new Result.Undefined(); }
    default Result check(Request.Params params) { return new Result.Undefined(); }

    /**
     * Приоритет выполнения плагина. Если плагину нужно делать какие-то тяжелые вычисления, то лучше задать ему приоритет
     * LOW, чтобы более легковесные плагины выполнились раньше и, вероятно, обнаружили проблему без выполнения тяжелых вычислений.
     */
    Priority priority();
    String name();
    default String description() {
        return "";
    }
}
