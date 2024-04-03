/**
 * FTPServer is a simple FTP server implementation in Java.
 * It listens for client connections on a specified port and handles client requests.
 * It provides user authentication functionality and spawns a new thread for each client connection.
 * 
 * @author Ching, Nicolas Miguel T.
 * @author Santos, Kyle Adrian L.
 * @version 1.0
 * @since April 3, 2024
*/

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {
    //Array of user information for authentication.
    private static final User[] users = {
        new User("john", "1234"),
        new User("jane", "5678"),
        new User("joe", "qwerty")
    };

/**
 * Authenticates a user based on the provided username and password.
 * 
 * @param username The username to authenticate
 * @param password The password corresponding to the username
 * @return The User object if authentication is successful, null otherwise
*/

    public static User authenticateUser(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return user;
            }
        }
        return null; // Authentication failed
    }
    public static void main(String[] args) {
        int port = 2048; // Change port if needed

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("FTP Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
