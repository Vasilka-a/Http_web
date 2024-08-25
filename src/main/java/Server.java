import org.apache.http.NameValuePair;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final String GET = "GET";
    public static final String POST = "POST";
    private static final String FORM_ENCTYPE = "application/x-www-form-urlencoded";
    final List<String> validPaths = List.of("/catcat.png", "/classic.html", "/links.html", "/events.html", "/events.js");
    private final ExecutorService threadPool;
    private final HashMap<String, Map<String, Handler>> handlers;
    protected String path;
    private List<NameValuePair> param = null;
    private List<NameValuePair> bodyParams;

    public Server(int poolSize) {
        threadPool = Executors.newFixedThreadPool(poolSize);
        handlers = new HashMap<>();
    }

    public void start(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.execute(() -> listen(socket));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void listen(Socket socket) {
        final var allowedMethods = List.of(GET, POST);
        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            final var limit = 4096;

            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);
            // ищем request line
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                badRequest(out);
                return;
            }
            // читаем request line
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                return;
            }

            final var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                badRequest(out);
                socket.close();
                return;
            }

            final var pathQuery = requestLine[1];

            if (!pathQuery.contains("?")) {
                path = pathQuery;
            } else {
                path = pathQuery.substring(0, pathQuery.indexOf("?"));
                param = Request.getQueryParams(pathQuery.substring(
                        pathQuery.indexOf("?") + 1));
            }

            if (!path.startsWith("/")) {
                badRequest(out);
                return;
            }
            // ищем заголовки
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                badRequest(out);
                return;
            }
            // отматываем на начало буфера
            in.reset();
            // пропускаем requestLine
            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));

            // для GET тела нет
            if (!method.equals(GET)) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final var contentLength = extractHeader(headers, "Content-Length");
                final var contentType = extractHeader(headers, "Content-Type");

                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);
                    final var body = new String(bodyBytes);
                    if (contentType.isPresent() && contentType.get().equals(FORM_ENCTYPE)) {
                        bodyParams = Request.getPostParams(body);
                    } else {
                        System.out.println(body);
                    }
                }
            }

            Request request = new Request(method, path, param, headers, bodyParams);
            printRequest(request);

            if (!handlers.containsKey(request.getMethod())) {
                badRequest(out);
            }

            Map<String, Handler> handlerMap = handlers.get(request.getMethod());
            for (String handlerPath : handlerMap.keySet()) {
                if (handlerPath.equals(request.getPath())) {
                    handlerMap.get(request.getPath()).handle(request, out);
                } else {
                    if (!validPaths.contains(request.getPath())) {
                        badRequest(out);
                    } else {
                        defaultHandler(out, path);
                    }
                }
            }
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void printRequest(Request request) {
        System.out.println(request);
        if (param != null) {
            System.out.println("Query parameters by name: ");
            Request.getQueryParam(param, "tittle");
        }
        if (bodyParams != null) {
            System.out.println("Body by name: ");
            Request.getPostParam("image", bodyParams);
        }
    }

    protected void defaultHandler(BufferedOutputStream out, String path) throws IOException {
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            return;
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    protected void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new HashMap<>());
        }
        handlers.get(method).put(path, handler);
    }
}