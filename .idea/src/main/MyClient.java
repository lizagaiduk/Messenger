package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class MyClient {
    private final String serverAddress;
    private final int serverPort;
    private JTextArea messageArea;
    private JTextField messageField;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    public MyClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void start() {
        JFrame frame = setupUI();
        try {
            connectToServer(frame);
            startServerListener();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error: " + e.getMessage());
            frame.dispose();
        }
    }

    private JFrame setupUI() {
        JFrame frame = new JFrame("Chat");
        frame.setSize(400, 400);
        frame.setLocation(500, 100);

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setBackground(Color.BLACK);
        messageArea.setForeground(Color.GREEN);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        frame.add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());

        messageField = new JTextField();
        messageField.setBackground(Color.BLACK);
        messageField.setForeground(Color.MAGENTA);
        messageField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        panel.add(messageField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Monospaced", Font.PLAIN, 14));
        sendButton.setBackground(Color.BLACK);
        sendButton.setForeground(Color.MAGENTA);
        sendButton.setFocusPainted(false);
        sendButton.addActionListener(e -> sendMessage(frame));
        panel.add(sendButton, BorderLayout.EAST);

        messageField.addActionListener(e -> sendMessage(frame));
        frame.add(panel, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeClient();
               frame.dispose();
    }
        });
        frame.setVisible(true);
        return frame;
    }

    private void connectToServer(JFrame frame) throws IOException {
        socket = new Socket(serverAddress, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        while (true) {
            String name = JOptionPane.showInputDialog(frame, "Enter your name:");
            if (name == null || name.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            out.println(name.trim());
            String serverResponse = in.readLine();

            if ("ACCEPTED".equals(serverResponse)) {
                messageArea.append("Connected to server as " + name.trim() + "\n");
                break;
            } else {
                JOptionPane.showMessageDialog(frame, serverResponse, "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void startServerListener() {
        Thread serverReaderThread = new Thread(() -> {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    messageArea.append(serverMessage + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeClient();
            }
        });
        serverReaderThread.start();
    }
    private void sendMessage(JFrame frame) {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Message cannot be empty.");
            return;
        }
        out.println(message);
        if(message.equals("/exit")){
            closeClient();
            frame.dispose();
            return;
        }
        messageArea.append("You: " + message + "\n");
        messageField.setText("");
    }
    private void closeClient() {
        try {
            if (out != null) {
                out.println("/exit");
            }
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new MyClient("localhost", 1701).start();
    }
}
