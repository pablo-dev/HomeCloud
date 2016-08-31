/*
 * ...
 */
package homecloud;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * 
 * @author pablo.carnero
 */
public class HomeCloud {
    
    private final static int DEFAULT_PORT = 3999;
    private final static String DEFAULT_STORAGE_PATH = "C:\\Users\\pablo.carnero\\Temp";
    
    private static int port = DEFAULT_PORT;
    private static String storagePath = DEFAULT_STORAGE_PATH;
    
    /**
     * Read the input arguments
     *   -d,--directory <arg>   storage directory
     *   -p,--port <arg>        port number
     * 
     * @param args 
     */
    private static void readParameters(String[] args) {
        // reading parameters
        Options options = new Options();
        
        Option portOption = new Option("p", "port", true, "port number");
        options.addOption(portOption);
        
        Option pathOption = new Option("d", "directory", true, "storage directory");
        options.addOption(pathOption);
        
        // TODO: add user and password option for authentication
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
            
            String portStr = cmd.getOptionValue("p");
            if (portStr != null) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new ParseException("Integer value required for port: " + port);
                }
            }
            
            storagePath = cmd.getOptionValue("d", DEFAULT_STORAGE_PATH);
            
        } catch (ParseException ex) {
            System.out.println("Invalid arguments: " + ex.getMessage());
            formatter.printHelp("homecloud", options);
            System.exit(1);
        }
    }
    
    /**
     * Starts a server listening to a port for incoming connections.
     * Once a connection is stablished it sends the last sync date to the client.
     * Then it asks for the number of file to receive and start the transference for all of them.
     * 
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        
        readParameters(args);
        
        // starting the service
        int bytesRead;
        OutputStream output = null;
        try (
                PropManager propManager = new PropManager(storagePath);
                ServerSocket serverSocket = new ServerSocket(port)) {
            
            while (true)
            {
                System.out.println("awaiting for connection...");
                try (
                        Socket clientSocket = serverSocket.accept();
                        InputStream in = clientSocket.getInputStream();
                        DataInputStream clientData = new DataInputStream(in);
                        OutputStream out = clientSocket.getOutputStream();
                        DataOutputStream serverData = new DataOutputStream(out);
                    ) {
                    
                    String clientId = clientData.readUTF();
                    
                    System.out.println("connection established with " + clientId);
                    
                    System.out.println("sending the last sync date " + propManager.getLastSync(clientId));
                    serverData.writeUTF(propManager.getLastSync(clientId));

                    int fileNumbers = clientData.readInt();
                    System.out.println("ready to receive " + fileNumbers + " files");
                    
                    for (int i = 0; i < fileNumbers; i++) {
                        
                        String fileName = clientData.readUTF();
                        System.out.println("receiving the file number " + i + " with name: " + fileName);

                        output = new FileOutputStream(storagePath + "\\" + fileName);

                        long size = clientData.readLong();     
                        byte[] buffer = new byte[1024];
                        while (size > 0 && (bytesRead = clientData.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                            output.write(buffer, 0, bytesRead);
                            size -= bytesRead;
                            System.out.println(size + " bytes remaining for " + fileName);
                        }

                        System.out.println(fileName + " recived");
                    
                    }
                    
                    propManager.setLastSync(clientId);
                    
                } finally {
                    if (output != null) output.close();
                }
            }
            
        } catch (IOException ex) {
            System.out.println("Input/output Error " + ex.getMessage());
        }

    }

}
