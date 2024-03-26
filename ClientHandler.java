import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final int MIN_PASSIVE_PORT = 49152; // Minimum passive port number
    private final int MAX_PASSIVE_PORT = 65535; // Maximum passive port number
    private String serverDIR = "./Server";
    private String currentDIR = "/";

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            writer.println("220 Welcome to NSCOM01 FTP server");

            User currentUser = null;
            String username = "";

            // Handle client commands
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "USER":
                        username = parts.length > 1 ? parts[1] : "";
                        writer.println("331 User name okay, need password");
                        break;
                    case "PASS":
                        String password = parts.length > 1 ? parts[1] : "";
                        currentUser = FTPServer.authenticateUser(username, password);
                        if (currentUser != null) {
                            writer.println("230 User logged in, proceed");
                        } else {
                            writer.println("530 Not logged in");
                        }
                        break;
                    case "PASV":
                        handlePasvCommand();
                        break;
                    case "QUIT":
                        writer.println("221 Goodbye.");
                        break;
                    case "PWD":
                        writer.println("257 \"" + currentDIR + "\" is the current directory");
                        break;
                    case "CWD":
                        writer.println("250 Directory successfully changed");
                        break;
                    case "CDUP":
                        writer.println("200 Command okay");
                        break;
                    case "MKD":
                        writer.println("257 Directory created successfully");
                        break;
                    case "RMD":
                        writer.println("250 Directory deleted successfully");
                        break;
                    case "LIST":
                        writer.println("150 Here comes the directory listing");
                        // Implement directory listing logic here
                        // Example: writer.println("File1.txt\r\nFile2.txt");
                        writer.println("226 Directory send OK");
                        break;
                    case "RETR":
                        writer.println("150 Opening data connection");
                        // Implement file retrieval logic here
                        writer.println("226 Transfer complete");
                        break;
                    case "DELE":
                        writer.println("250 File deleted successfully");
                        break;
                    case "STOR":
                        writer.println("150 Opening data connection");
                        // Implement file storage logic here
                        writer.println("226 Transfer complete");
                        break;
                    case "HELP":
                        writer.println("214 Help message");
                        // Implement help message here
                        break;
                    case "TYPE":
                        writer.println("200 Type set to");
                        // Implement type setting logic here
                        break;
                    case "MODE":
                        writer.println("200 Mode set to");
                        // Implement mode setting logic here
                        break;
                    case "STRU":
                        writer.println("200 Structure set to");
                        // Implement structure setting logic here
                        break;
                    default:
                        writer.println("550 Requested action not taken");
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePasvCommand() {
        try {
            // Generate a random port for passive mode
            int passivePort = getRandomPort();
            ServerSocket serverSocket = new ServerSocket(passivePort);

            // Get server's IP address
            String ipAddress = clientSocket.getLocalAddress().getHostAddress().replace(".", ",");

            // Inform the client about the passive mode setup
            writer.println("227 Entering Passive Mode (" + ipAddress + "," + (passivePort / 256) + ","
                    + (passivePort % 256) + ")");
            writer.flush();

            // Accept incoming data connection
            Socket dataSocket = serverSocket.accept();

            // Handle data transfer over the dataSocket
            // Implement your data transfer logic here

            // Close the dataSocket and serverSocket
            dataSocket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getRandomPort() {
        Random random = new Random();
        return random.nextInt(MAX_PASSIVE_PORT - MIN_PASSIVE_PORT + 1) + MIN_PASSIVE_PORT;
    }
}
