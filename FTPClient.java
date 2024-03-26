import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class FTPClient {
    public static void main(String[] args) {
        String host = "127.0.0.1"; // Change host if needed
        int port = 2048; // Change port if needed

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println(reader.readLine() + "\n"); // Welcome message from server

            String response;
            
            // LOGIN (USER & PASS)
            do {
                System.out.print("COMMAND: ");
                writer.println(consoleReader.readLine());
                response = reader.readLine();
                System.out.println(response + "\n");
            } while (!response.contains("230"));

            do {
                System.out.print("COMMAND: ");
                writer.println(consoleReader.readLine());
                response = reader.readLine();
                System.out.println(response + "\n");
            } while (!response.contains("221"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

