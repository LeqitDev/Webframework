import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Webserver {
    ServerSocket serverSocket;
    Socket clientSocket;
    Map<String, Runnable> routes;
    boolean DEBUG_MODE = false;

    public void init(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        routes = new HashMap<>();
    }

    public void init(int port, boolean debug_mode) throws IOException {
        serverSocket = new ServerSocket(port);
        this.DEBUG_MODE = debug_mode;
        routes = new HashMap<>();
    }

    public void start() throws IOException {
        System.out.println("Server online @ http://localhost:" + serverSocket.getLocalPort() + "/");
        while (true) {
            clientSocket = serverSocket.accept();
            boolean failed = false;
            if (clientSocket.isConnected() && !clientSocket.isClosed()) {
                InputStream inputStream = clientSocket.getInputStream();

                while (inputStream.available() < 1) {
                    if (clientSocket.isInputShutdown()) {
                        failed = true;
                        break;
                    }
                }

                if (!failed) {
                    byte[] incomingBytes = new byte[inputStream.available()];
                    inputStream.read(incomingBytes);
                    Request request = new Request(new String(incomingBytes));
                    System.out.println("Requesting " + request.route + "!");
                    if (request.args.size() > 0) {
                        System.out.println("There are also these arguments: ");
                        request.args.forEach((s, s2) -> {
                            System.out.println(s + ": " + s2);
                        });
                    }

                    if (routes.containsKey(request.route)) {
                        routes.get(request.route).run();
                    } else if (request.route.contains(".")) {
                        try {
                            byte[] bytes = Files.readAllBytes(Paths.get("src/public" + request.route));
                            sendHeader(200, bytes.length);
                            sendBytes(bytes);
                            sendToClient("\r\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (request.route.equals("/")) render(200, this.getClass().getResourceAsStream("public/index.html"));
                        else {
                            System.out.println("No route found!");
                            render(404, this.getClass().getResourceAsStream("public/404.html"));
                        }
                    }
                }



                inputStream.close();
                clientSocket.close();
            }
        }
    }

    public void addRoute(String route, Runnable script) {
        routes.put(route, script);
    }

    void sendHeader(int code, int length) {
        String status_code = switch (code) {
           case 200 -> "OK";
           case 403 -> "Forbidden";
           case 404 -> "Not Found";
           default -> "Processing";
       };
        String msg = "HTTP/1.1 " + code + " " + status_code + "\r\n" +
                "Content-Type: */*\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n\r\n";
        sendToClient(msg);
    }

    void sendBytes(byte[] bytes) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendToClient(String msg) {
        try {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            out.write(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void render(String pathToFile) {
        try {
            StringBuilder html = new StringBuilder();
            File renderFile = new File(pathToFile);
            Scanner fileReader = new Scanner(renderFile);
            while (fileReader.hasNextLine()) {
                html.append(fileReader.nextLine()).append("\n\r");
            }
            fileReader.close();
            html.append("\n\r");
            sendHeader(200, html.length());
            sendToClient(html.toString());
        } catch (FileNotFoundException e) {
            System.out.println("Das System konnte die angegebene Datei nicht finden!");
        }
    }

    public void render(int code, InputStream inputStream) {
        StringBuilder html = new StringBuilder();
        Scanner fileReader = new Scanner(inputStream);
        while (fileReader.hasNextLine()) {
            html.append(fileReader.nextLine()).append("\n\r");
        }
        fileReader.close();
        html.append("\n\r");
        sendHeader(code, html.length());
        sendToClient(html.toString());
    }

    class Request {
        String method;
        String route;
        Map<String, String> args = new HashMap<>();
        Map<String, String> attributes = new HashMap<>();

        Request(String request) {
            String[] lines = request.split("\r\n");
            method = lines[0].split(" ")[0];
            route = lines[0].split(" ")[1];

            if (route.contains("?")) {
                String args2 = route.substring(route.indexOf('?')+1);
                if (args2.contains("&")) {
                    String[] args3 = args2.split("&");
                    for (String arg : args3) args.put(arg.split("=")[0], arg.split("=")[1]);
                } else args.put(args2.split("=")[0], args2.split("=")[1]);
            }

            List<String> list = new ArrayList<>();
            Collections.addAll(list, lines);
            list.removeAll(Collections.singletonList(lines[0]));
            lines = list.toArray(new String[0]);
            if (DEBUG_MODE) System.out.println("Method: " + method + ", Route: " + route);

            for (String line : lines) {
                if (DEBUG_MODE) System.out.println(line);
                if (line.contains(": ")) attributes.put(line.split(": ")[0], line.split(": ")[1]);
                else if (line.contains("=")) {
                    if (line.contains("&")) {
                        String[] args2 = line.split("&");
                        for (String arg : args2) args.put(arg.split("=")[0], arg.split("=")[1]);
                        continue;
                    }
                    args.put(line.split("=")[0], line.split("=")[1]);
                }
            }
        }
    }
}
