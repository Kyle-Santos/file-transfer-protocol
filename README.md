# file-transfer-protocol

How to run the program

- Compile and run FTPServer Java File and FTPClient File
/* Must be running simultaenously, Server first then client */
 
To Compile:
- javac FTPServer.java
- javac FTPClient.java

To Run:
- java FTPServer
/* Specify IP and Port Number */

- java FTPClient

FTPClient Login (Running)
/* Input Commands in Order */

- COMMAND: USER john
- COMMAND: PASS 1234

FTPClient (After Logging In)
- Choose FTP Commands to Execute 
COMMAND: HELP - displays available commands

/* Execute PASV command first before executing STOR or RETR commands */