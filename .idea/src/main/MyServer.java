package main;

import java.io.*;
import java.net.*;
import java.util.*;

public class MyServer {
    private int port;
    private String serverName;
    private Set<String> bannedPhrases;
    private final Map<String, ClientSessionManager> clients = new HashMap<>();
    private ServerSocket serverSocket;

    public MyServer(String filePath) {
        loadConfigs(filePath);
    }

    private void loadConfigs(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                    case "port":
                        this.port = Integer.parseInt(value);
                        break;
                    case "serverName":
                        this.serverName = value;
                        break;
                    case "bannedPhrases":
                        this.bannedPhrases = new HashSet<>(Arrays.asList(value.split(",")));
                        break;
                    default:
                        System.out.println("Unknown configuration key: " + key);
                }
            }
            System.out.println("Server loaded with configurations: Port: " + port +"\n"+
                    "Server name: " + serverName +"\n"+
                    "Banned phrases: " + bannedPhrases);
        } catch (IOException e) {
            throw new RuntimeException("Error loading configuration", e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid port number in configuration", e);
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println(serverName+" launched on port: "+port);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientSessionManager(clientSocket)).start();
                } catch (SocketException e) {
                    if (serverSocket.isClosed()) {
                        System.out.println("Server socket is closed. Stopping the server.");
                        break;
                    }
                    System.err.println("Socket error while accepting connection:");
                    e.printStackTrace();
                }

            }
        } catch (IOException e) {
            System.err.println("An error occurred while running the server:");
            e.printStackTrace();
        }finally {
            stopServer();
        }
    }

    private void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new MyServer("src/main/server_config.txt").start();
    }

    class ClientSessionManager implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientName;

        public ClientSessionManager(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                initializeStreams();
                authenticateClient();
                registerClient();

                String message;
                while ((message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (SocketException e) {
                System.out.println("Connection closed for client: " + clientName);
            } catch (IOException e) {
                System.err.println("Error handling client: " + clientName);
                e.printStackTrace();
            } finally {
                closeChat();
            }
        }

        private void initializeStreams() throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        private void authenticateClient() throws IOException {
            while (true) {
                clientName = in.readLine();
                if (clientName == null || clientName.trim().isEmpty() || clients.containsKey(clientName)) {
                    out.println("Name is empty or already taken. Try again.");
                } else {
                    out.println("ACCEPTED");
                    break;
                }
            }
        }
        private void registerClient() {
            synchronized (clients) {
                clients.put(clientName, this);
                broadcastMessage("Server", clientName + " has joined the chat", false);
            }
            sendConnectedClientsList();
        }

        private void sendConnectedClientsList() {
            synchronized (clients) {
                String clientsList = String.join(", ", clients.keySet());
                sendMessage("Connected clients: " + clientsList);
            }
        }

        private void handleMessage(String message) {
            try {
                if (containsBannedPhrase(message)) {
                    sendMessage("Message contains banned phrases and will not be sent.");
                    return;
                }
                if (message.startsWith("/")) {
                    handleCommand(message);
                } else {
                    broadcastMessage(clientName, message, true);
                }
            } catch (Exception e) {
                sendMessage("Error handling message: " + e.getMessage());
            }
        }

        private boolean containsBannedPhrase(String message) {
            String lowerCaseMessage = message.toLowerCase();
            for (String phrase : bannedPhrases) {
                if (lowerCaseMessage.contains(phrase)) {
                    return true;
                }
            }
            return false;
        }

        private void handleCommand(String message) {
            if (message.startsWith("/msg ")) {
                handlePrivateMessage(message);
            } else if (message.startsWith("/except ")) {
                handleBroadcastWithExceptions(message);
            } else {
                switch (message) {
                    case "/exit" -> closeChat();
                    case "/list" -> sendConnectedClientsList();
                    case "/banned" -> sendMessage("Banned phrases: " + bannedPhrases);
                    case "/help" -> showAvailableCommands();
                    default -> sendMessage("Unknown command. Type /help for the list of available commands.");
                }
            }
        }

        private void handlePrivateMessage(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                out.println("Usage: /msg [user1,user2...] [message]");
                return;
            }
            String[] userArray = parts[1].split(",");
            String privateMessage = parts[2];
            synchronized (clients) {
                for (String user : userArray) {
                    ClientSessionManager targetClient = clients.get(user.trim());
                    if (targetClient != null) {
                        targetClient.sendMessage(clientName + " (private): " + privateMessage);
                    } else {
                        out.println("User " + user + " not found.");
                    }
                }
            }
        }

        private void handleBroadcastWithExceptions(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                out.println("Usage: /except [user1,user2,...] [message]");
                return;
            }
            String[] excludedUsers = parts[1].split(",");
            Set<String> excludedSet = new HashSet<>(Arrays.asList(excludedUsers));
            String userMessage = parts[2];

            synchronized (clients) {
                for (Map.Entry<String, ClientSessionManager> entry : clients.entrySet()) {
                    if (!excludedSet.contains(entry.getKey()) && !entry.getKey().equals(clientName)) {
                        entry.getValue().sendMessage(clientName + " (to everyone, except " + excludedSet + "): " + userMessage);
                    }
                }
            }
        }
        private void closeChat() {
            unregisterClient();
            closeSocket();
        }
        private void sendMessage(String message) {
            out.println(message);
        }
        private void showAvailableCommands() {
            sendMessage("""
                    Available commands:
                    /list - Show list of connected clients.
                    /banned - Show list of banned phrases.
                    /msg [username1,username2] [message] - Send a private message.
                    /except [user1,user2,...] [message] - Broadcast message excluding specified users.
                    /help - Show available commands.
                    /exit - Exit from chat.
                    """);
        }

        private void broadcastMessage(String sender, String message, boolean addSenderPrefix) {
            synchronized (clients) {
                for (ClientSessionManager client : clients.values()) {
                    if (!client.clientName.equals(sender)) {
                        client.sendMessage(addSenderPrefix ? sender + ": " + message : message);
                    }
                }
            }
        }

        private void unregisterClient() {
            synchronized (clients){
                if (clients.remove(clientName) != null) {
                    broadcastMessage("Server", clientName + " has left the chat", false);
                }
                if (clients.isEmpty() && !serverSocket.isClosed()) {
                    stopServer();
                }
            }
        }

        private void closeSocket() {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
