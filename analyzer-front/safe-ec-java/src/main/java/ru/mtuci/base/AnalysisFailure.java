package ru.mtuci.base;

import com.contrastsecurity.sarif.Message;
import com.contrastsecurity.sarif.Result;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.helpers.MessageFormatter;

import java.util.function.Consumer;

@Getter
@Setter
public class AnalysisFailure implements Consumer<Result>
{
    private final String message;
    private final Throwable cause;
    private LocationMeta meta;

    public AnalysisFailure(String template, Object... args)
    {
        var formattingTuple = MessageFormatter.arrayFormat(template, args);
        this.message = formattingTuple.getMessage();
        this.cause = formattingTuple.getThrowable();
    }

    @Override
    public void accept(Result result)
    {
        if (meta != null)
            meta.accept(result);

        result.withLevel(Result.Level.ERROR)
                .withMessage(new Message().withText(message));
    }
}
