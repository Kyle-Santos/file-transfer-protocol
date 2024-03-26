import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {
    // Array of user information
    private static final User[] users = {
        new User("john", "1234"),
        new User("jane", "5678"),
        new User("joe", "qwerty")
    };

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
