import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class Client {
    public static final String SERVER_ADDRESS = "localhost";  
    public static final int PORT = 12345;
    public DataOutputStream out;
    public JFrame frame = new JFrame("Chat and File Sharing Client");
    public JTextField textField = new JTextField(40);
    public JTextArea messageArea = new JTextArea(16, 50);
    public JComboBox<String> clientListDropdown = new JComboBox<>();
    public JButton sendButton = new JButton("Send");
    public JButton fileButton = new JButton("Send File");
    public boolean fileMode = false;

    public Client() {
        messageArea.setEditable(false);
        frame.add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.add(new JLabel("Clients Connected:"));
        panel.add(clientListDropdown);
        panel.add(fileButton);
        panel.add(sendButton);
        frame.add(panel, BorderLayout.NORTH);
        frame.add(textField, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);

        // Send button action
        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!fileMode) {
                    String selectedClient = (String) clientListDropdown.getSelectedItem();
                    String message = textField.getText();

                    // Display the message on sender's message area
                    messageArea.append("You: " + message + "\n");

                    try {
                        if (selectedClient.equals("BROADCAST")) {
                            // Send to all clients
                            out.writeBytes("BROADCAST:" + message + "\n");
                        } else {
                            // Send to specific client
                            out.writeBytes("MSG_TO:" + selectedClient + ":" + message + "\n");
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    textField.setText("");
                }
            }
        });

        // File button action
        fileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fileMode = true;
                sendFile();
            }
        });
    }

    public void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String selectedClient = (String) clientListDropdown.getSelectedItem();
            try {
                if (selectedClient.equals("BROADCAST")) {
                    out.writeBytes("FILE_BROADCAST:" + file.getName() + "\n");
                } else {
                    out.writeBytes("FILE_TO:" + selectedClient + ":" + file.getName() + "\n");
                }

                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    out.writeBytes(line + "\n");
                }
                out.writeBytes("END_OF_FILE\n");
                reader.close();
                messageArea.append("File sent successfully: " + file.getName() + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                fileMode = false;
            }
        }
    }

    public void connect() throws IOException {
        Socket socket = new Socket(SERVER_ADDRESS, PORT);
        out = new DataOutputStream(socket.getOutputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        new Thread(new Runnable() {
            public void run() {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("CLIENTS:")) {
                            updateClientList(message.substring(8));
                        } else if (message.startsWith("FILE_BROADCAST:") || message.startsWith("FILE_TO:")) {
                            String fileName = message.split(":")[1];
                            messageArea.append("Receiving file: " + fileName + "\n");
                            receiveFile(fileName, in); // Handle file reception
                        } else {
                            messageArea.append(message + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void updateClientList(String clients) {
        clientListDropdown.removeAllItems();  // Clear existing items
        String[] clientNames = clients.split(",");
        for (String client : clientNames) {
            clientListDropdown.addItem(client);
        }
        clientListDropdown.addItem("BROADCAST");
    }

    public void receiveFile(String fileName, BufferedReader reader) {
        try {
            File receivedFile = new File("received_" + fileName);
            BufferedWriter writer = new BufferedWriter(new FileWriter(receivedFile));

            String line;
            while ((line = reader.readLine()) != null && !line.equals("END_OF_FILE")) {
                writer.write(line);
                writer.newLine();
            }

            writer.close();
            messageArea.append("File received successfully: " + receivedFile.getName() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        try {
            client.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
