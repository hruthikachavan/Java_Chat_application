import java.io.*;
import java.net.*;

public class Server {
    public static final int PORT = 12345;
    public static ClientHandler[] clientHandlers = new ClientHandler[10];
    public static int clientCount = 0;

    public static void main(String[] args) {
        System.out.println("Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                if (clientCount < clientHandlers.length) {
                    clientHandlers[clientCount++] = clientHandler;
                    clientHandler.start();
                } else {
                    System.out.println("Server is full. Connection rejected.");
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        public Socket socket;
        public DataOutputStream out;
        public BufferedReader in;
        public String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            // Assign a unique client name: client1, client2, etc.
            this.clientName = "client" + (clientCount + 1);
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new DataOutputStream(socket.getOutputStream());

                sendClientList();

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("BROADCAST:")) {
                        sendToAll(clientName + ": " + message.substring(10), null);
                    } else if (message.startsWith("MSG_TO:")) {
                        String[] parts = message.split(":", 3);
                        String targetClient = parts[1];
                        String msgContent = parts[2];
                        sendToAll(clientName + ": " + msgContent, targetClient);
                    } else if (message.startsWith("FILE_TO:")) {
                        String[] parts = message.split(":", 3);
                        String targetClient = parts[1];
                        String fileName = parts[2];
                        sendToAll("Receiving file: " + fileName + " from " + clientName, targetClient);
                        receiveAndBroadcastFile(fileName, targetClient);
                    } else if (message.startsWith("FILE_BROADCAST:")) {
                        String fileName = message.substring(15);
                        sendToAll("Receiving file: " + fileName + " from " + clientName, null);
                        receiveAndBroadcastFile(fileName, null);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                removeClientHandler(this);
                sendClientList();
                try { socket.close(); } catch (IOException e) { }
            }
        }

        public void sendToAll(String message, String targetClient) {
            for (int i = 0; i < clientCount; i++) {
                if (clientHandlers[i] != null) {
                    if (targetClient == null || clientHandlers[i].clientName.equals(targetClient)) {
                        try {
                            clientHandlers[i].out.writeBytes(message + "\n");
                        } catch (IOException e) {
                            System.out.println("Error sending message to client: " + e.getMessage());
                        }
                    }
                }
            }
        }

        public void sendClientList() {
            StringBuilder clients = new StringBuilder("CLIENTS:");
            for (int i = 0; i < clientCount; i++) {
                if (clientHandlers[i] != null) {
                    clients.append(clientHandlers[i].clientName).append(",");
                }
            }

            for (int i = 0; i < clientCount; i++) {
                if (clientHandlers[i] != null) {
                    try {
                        clientHandlers[i].out.writeBytes(clients.toString() + "\n");
                    } catch (IOException e) {
                        System.out.println("Error sending client list to client: " + e.getMessage());
                    }
                }
            }
        }

        public void removeClientHandler(ClientHandler clientHandler) {
            for (int i = 0; i < clientCount; i++) {
                if (clientHandlers[i] == clientHandler) {
                    clientHandlers[i] = clientHandlers[--clientCount];
                    break;
                }
            }
        }

        public void receiveAndBroadcastFile(String fileName, String targetClient) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter("received_" + fileName));
                String line;
                while ((line = in.readLine()) != null && !line.equals("END_OF_FILE")) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.close();
                sendToAll("File " + fileName + " received successfully.", targetClient);
            } catch (IOException e) {
                System.out.println("Error handling file: " + e.getMessage());
            }
        }
    }
}
