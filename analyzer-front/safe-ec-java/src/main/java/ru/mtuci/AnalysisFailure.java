package ru.mtuci;

import lombok.Getter;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

@Getter
public class AnalysisFailure {
    private final String message;
    private final Throwable cause;
    
    public AnalysisFailure(String template, Object... args) {
        FormattingTuple formattingTuple = MessageFormatter.arrayFormat(template, args);
        this.message = formattingTuple.getMessage();
        this.cause = formattingTuple.getThrowable();
    }
}
