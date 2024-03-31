import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class FTPClient {
    public static void main(String[] args) {
        String host = "127.0.0.1"; // Change host if needed
        int port = 2048; // Change port if needed
        String clientDIR = "Clients/";

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println(reader.readLine()); // Welcome message from server

            String response = "";
            String input;
            String command;

            Socket dataSocket = null;
            InputStream dataInputStream = null;

            do {
                System.out.print("\nCOMMAND: ");
                input = consoleReader.readLine();
                command = input.split(" ")[0].toUpperCase();

                writer.println(input);
                
                response = reader.readLine();
                System.out.println(response);

                // HANDLE RETR
                if (input.toUpperCase().startsWith("RETR") && response.startsWith("150")) {
                    receiveFileData(dataInputStream, clientDIR + input.split(" ")[1]);
                    System.out.println(reader.readLine());
                    dataSocket.close();
                } 
                // HADNLE multi-line response (LIST & HELP)
                else if (response.startsWith("150") || response.startsWith("214")) {
                    // Read all lines of the multi-line response until the final response code (226)
                    String line;
                    System.out.println();
                    while (!(line = reader.readLine()).startsWith("226")) {
                        System.out.println(line);
                    }
                    System.out.println(line); // Print the final response line
                }
                // HANDLE STOR
                else if (command.equals("STOR")) {
                    // Perform file upload
                }
                // HANDLE PASV
                else if (command.equals("PASV")) {
                    // Find the index of the opening parenthesis '(' and closing parenthesis ')'
                    int startIndex = response.indexOf('(');
                    int endIndex = response.indexOf(')');

                    // Extract the substring between the parentheses
                    String ipAddressAndPort = response.substring(startIndex + 1, endIndex);

                    // Extract IP address and port from PASV response (you need to parse it properly)
                    String[] pasvParts = ipAddressAndPort.split(",");
                    String ipAddress = pasvParts[0] + "." + pasvParts[1] + "." + pasvParts[2] + "." + pasvParts[3];
                    int portH = Integer.parseInt(pasvParts[4]);
                    int portL = Integer.parseInt(pasvParts[5]);
                    
                    // Calculate the data port
                    int dataPort = (portH << 8) + portL;

                    // Establish data connection
                    dataSocket = new Socket(ipAddress, dataPort);
                    dataInputStream = dataSocket.getInputStream();
                }

            } while (!response.contains("221"));

            // Close the socket
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFileData(InputStream dataInputStream, String filename) throws IOException {
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(filename))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = dataInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            // Close the dataInputStream after data transfer completes or in case of error
            if (dataInputStream != null) {
                dataInputStream.close();
            }
        }
    }
}

