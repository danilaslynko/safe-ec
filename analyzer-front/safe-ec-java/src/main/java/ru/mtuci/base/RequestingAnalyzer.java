package ru.mtuci.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;
import ru.mtuci.net.SafeEcClient;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class RequestingAnalyzer extends Analyzer
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    public record RequestDto(Future<Response> response, Supplier<LocationMeta> meta)
    {
        public static RequestDto of(Future<Response> response)
        {
            return new RequestDto(response, null);
        }
    }

    protected RequestingAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    public final void analyze()
    {
        try
        {
            List<RequestDto> requests = makeRequests();
            for (RequestDto request : requests)
            {
                Response response = request.response().get();
                var failure = switch (response.type())
                {
                    case SUCCESS -> null;
                    case ERROR ->
                            addError("EC check request failed: reqId={}, info='{}'", response.reqId(), response.info());
                    case VULNERABLE -> addError(response.info());
                };

                if (failure != null)
                {
                    var meta = request.meta() == null ? simpleMeta() : request.meta().get();
                    failure.setMeta(meta);
                }
            }
        }
        catch (Exception e)
        {
            log.error("{} failed", getClass().getSimpleName(), e);
            var failure = addError("{} failed", getClass().getSimpleName(), e);
            failure.setMeta(simpleMeta());
        }
    }

    protected LocationMeta simpleMeta()
    {
        return baseMeta().build();
    }

    protected LocationMeta.LocationMetaBuilder baseMeta()
    {
        return LocationMeta.builder().path(this.path.toString());
    }

    protected Future<Response> request(Request.Type type, Object data)
    {
        return request(Request.of(type, data));
    }

    protected Future<Response> request(Request request)
    {
        return SafeEcClient.getInstance().send(request);
    }

    public List<Future<Response>> makeFutureResponses()
    {
        return Collections.emptyList();
    }

    public List<RequestDto> makeRequests()
    {
        return makeFutureResponses().stream().map(RequestDto::of).collect(Collectors.toList());
    }
}
