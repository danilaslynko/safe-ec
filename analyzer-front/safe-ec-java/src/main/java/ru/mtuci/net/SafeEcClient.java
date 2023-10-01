package ru.mtuci.net;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.mtuci.Utils;

import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class SafeEcClient implements AutoCloseable
{
    private static final ThreadLocal<Integer> contexts = ThreadLocal.withInitial(() -> 0);

    @RequiredArgsConstructor
    private static class ResponseFuture extends CompletableFuture<Response>
    {
        @Getter
        private final String requestId;
        private final Object monitor = new Object();
        private Response response;

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
            synchronized (monitor)
            {
                return response != null;
            }
        }

        @Override
        public Response get() throws InterruptedException
        {
            synchronized (monitor)
            {
                while (response == null)
                    monitor.wait();
            }

            return response;
        }

        @Override
        public Response get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException
        {
            long elapsedMillis = 0;
            long totalTimeoutMillis = unit.toMillis(timeout);
            long startTime = System.currentTimeMillis();
            synchronized (monitor)
            {
                while (response == null && elapsedMillis < totalTimeoutMillis)
                {
                    long timeoutMillis = totalTimeoutMillis - elapsedMillis;
                    monitor.wait(timeoutMillis);
                    elapsedMillis = System.currentTimeMillis() - startTime;
                }
            }

            if (response == null)
                throw new TimeoutException();

            return response;
        }

        void resolve(Response response)
        {
            synchronized (monitor)
            {
                this.response = response;
                this.monitor.notifyAll();
            }
        }
    }

    private static SafeEcClient INSTANCE;

    public static SafeEcClient getInstance()
    {
        if (contexts.get() == 0)
            throw new IllegalStateException("SafeEcClient is accessible only inside the `withContext` method");

        return INSTANCE;
    }

    public static void withClient(Runnable action)
    {
        if (contexts.get() == 0)
        {
            INSTANCE = newInstance();
            INSTANCE.open();
        }

        contexts.set(contexts.get() + 1);
        action.run();
        contexts.set(contexts.get() - 1);
        if (contexts.get() == 0)
        {
            INSTANCE.close();
            INSTANCE = null;
        }
    }

    protected static SafeEcClient newInstance()
    {
        return new SafeEcClient(System.getProperty("server.host"), Integer.getInteger("server.port", 7999));
    }

    private static final int DOLLAR_SIGN_BYTE = 36;

    private final Lock readLock;
    private final Lock writeLock;

    private final Map<String, ResponseFuture> pending = new WeakHashMap<>();

    private final String host;
    private final int port;

    private Socket client;
    private Thread receiver;

    protected SafeEcClient(String host, Integer port)
    {
        this.host = host;
        this.port = port;

        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.readLock = readWriteLock.readLock();
        this.writeLock = readWriteLock.writeLock();
    }

    @SneakyThrows
    private <T> T read(Action<T> action)
    {
        readLock.lock();
        try
        {
            return action.run();
        }
        finally
        {
            readLock.unlock();
        }
    }

    @SneakyThrows
    private void write(Action<?> action)
    {
        writeLock.lock();
        try
        {
            action.run();
        }
        finally
        {
            writeLock.unlock();
        }
    }

    @FunctionalInterface
    private interface Action<T>
    {
        T run() throws Exception;
    }

    private void ensureOpen()
    {
        read(() -> {
            if (client == null)
                open();

            return null;
        });
    }

    public void open()
    {
        write(() -> {
            if (client == null)
            {
                client = new Socket(InetAddress.getByAddress(host.getBytes(StandardCharsets.UTF_8)), port);
                client.setSoTimeout(10_000);

                receiver = new Thread(() -> {
                    try
                    {
                        while (client != null)
                        {
                            Response response = receive();
                            log.info("Received from server: {}", response);
                            ResponseFuture future = pending.get(response.reqId());
                            future.resolve(response);
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("Got exception in reader", e);
                    }
                });
                receiver.start();
            }

            return null;
        });
    }

    public Future<Response> send(Request request)
    {
        return read(() -> {
            ensureOpen();

            String jsonRequest = Utils.toJson(request);
            log.info("Sending request: {}", jsonRequest);

            byte[] encoded = Base64.getEncoder().encode(jsonRequest.getBytes(StandardCharsets.UTF_8));
            var out = client.getOutputStream();
            out.write(encoded);
            out.write(DOLLAR_SIGN_BYTE);
            out.flush();
            ResponseFuture future = new ResponseFuture(request.id());
            pending.put(request.id(), future);
            return future;
        });
    }

    private Response receive()
    {
        return read(() -> {
            var inputStream = client.getInputStream();
            var stringBuilder = new StringBuilder();
            int next = inputStream.read();
            while (next != -1 && next != DOLLAR_SIGN_BYTE)
            {
                stringBuilder.append((char) next);
                next = inputStream.read();
            }
            var decoded = Base64.getDecoder().decode(stringBuilder.toString());
            return Utils.fromJson(decoded, Response.class);
        });
    }

    @Override
    public void close()
    {
        write(() -> {
            if (client != null)
            {
                client.close();
                client = null;
            }
            if (receiver != null)
            {
                receiver.interrupt();
                receiver.join();
                receiver = null;
            }

            return null;
        });
    }
}
