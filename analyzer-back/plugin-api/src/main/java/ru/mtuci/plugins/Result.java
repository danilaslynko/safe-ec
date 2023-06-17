package ru.mtuci.plugins;

public sealed interface Result permits Result.TechError, Result.Validated, Result.Vulnerable, Result.Undefined {
    /**
     * Кривая прошла проверки
     */
    record Validated() implements Result {}

    /**
     * Во время валидации произошла техническая ошибка, результат узнать невозможно
     */
    record TechError(String message) implements Result {}

    /**
     * Кривая признана уязвимой по результатам проверок
     */
    record Vulnerable(String message) implements Result {}

    /**
     * Если результат анализа не позволяет однозначно определить уязвимость или безопасность кривой
     */
    record Undefined() implements Result {}

    static Result validated(String message) {
        if (message == null || message.trim().isEmpty())
            return new Validated();

        return new Vulnerable(message);
    }
}
