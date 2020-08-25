import java.io.IOException;

public class Main {
    static Webserver server;

    public static void main(String[] args) throws IOException {
        server = new Webserver();
        server.init(8002, true);
        server.addRoute("/", Main::home);
        server.start();
    }

    static void home() {
        server.render(200, Main.class.getResourceAsStream("public/index.html"));
    }
}
