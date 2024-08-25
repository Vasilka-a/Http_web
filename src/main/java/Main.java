public class Main {
    public static void main(String[] args) {
        final var server = new Server(64);
        server.addHandler("GET", "/default_get.html", (request, responseStream) -> {
            // TODO: handlers code
        });
        server.addHandler("POST", "/default_get.html", (request, responseStream) -> {
            // TODO: handlers code
        });
        server.start(9999);
    }
}
