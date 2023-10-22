package ru.mtuci.test;

import lombok.Getter;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;
import ru.mtuci.net.SafeEcClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestSafeEcClient extends SafeEcClient
{
    private static final Map<String, Response> answers = new HashMap<>();
    @Getter
    private static final List<Request> requests = new ArrayList<>();

    public TestSafeEcClient()
    {
        super("localhost", 7999);
    }

    @Override
    public void open()
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public Future<Response> send(Request request)
    {
        requests.add(request);
        return new Future<>()
        {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning)
            {
                return false;
            }

            @Override
            public boolean isCancelled()
            {
                return false;
            }

            @Override
            public boolean isDone()
            {
                return true;
            }

            @Override
            public Response get()
            {
                return answers.computeIfAbsent(request.id(), r -> new Response(r, Response.Type.SUCCESS, null, null));
            }

            @Override
            public Response get(long timeout, TimeUnit unit)
            {
                return get();
            }
        };
    }

    public static List<Request> test(Runnable action)
    {
        try (MockedStatic<SafeEcClient> mock = Mockito.mockStatic(SafeEcClient.class))
        {
            mock.when(() -> SafeEcClient.newInstance(null, null)).thenReturn(new TestSafeEcClient());
            mock.when(() -> SafeEcClient.withClient(action, null, null)).thenCallRealMethod();
            mock.when(SafeEcClient::getInstance).thenCallRealMethod();
            withClient(action, null, null);
        }
        ArrayList<Request> result = new ArrayList<>(requests);
        requests.clear();
        return result;
    }

    public static void addAnswer(String reqId, Response response)
    {
        answers.put(reqId, response);
    }
}
