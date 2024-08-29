package core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class DummyServer implements Runnable {

  private static final int MAX_NUMBER = 5;

  public DummyServer() {
    //throw new UnsupportedOperationException("No instances");
  }

  public void main() throws IOException {
    final File socketFile = new File(new File(System.getProperty("java.io.tmpdir")),
        "junixsocket-test.sock");
    System.out.println(socketFile);

    try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
      // server.setReuseAddress(false);
      server.bind(AFUNIXSocketAddress.of(socketFile));
      System.out.println("server: " + server);

      while (!Thread.interrupted()) {
        System.out.println("Waiting for connection...");
        try (Socket sock = server.accept()) {
          System.out.println("Connected: " + sock);

          try (InputStream is = sock.getInputStream(); //
              OutputStream os = sock.getOutputStream()) {
            DataOutputStream dout = new DataOutputStream(os);
            DataInputStream din = new DataInputStream(is);

            System.out.println("Saying hello to client " + os);
            //os.write("Hello, dear Client".getBytes(StandardCharsets.UTF_8));
            //os.flush();
            dout.writeUTF("Hello, dear Client");
            dout.flush();
/*
            byte[] buf = new byte[128];
            int read = is.read(buf);
            System.out.println("Client's response: " + new String(buf, 0, read,
                StandardCharsets.UTF_8));
 */
            String handshake = din.readUTF();
            System.out.println("Client's response: " + handshake);

            System.out.println("Now counting to 5...");

            int number = 0;
            while (!Thread.interrupted()) {
              number++;
              System.out.println("write " + number);
              dout.writeInt(number);
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                e.printStackTrace();
                break;
              }
              if (number > MAX_NUMBER) {
                System.out.println("write -123 (end of numbers)");
                dout.writeInt(-123); // in this demo, -123 is our magic number to indicate the end
                break;
              }

              // verify the number from the client
              // in the demo, the client just sends 2 * our number
              int theirNumber = din.readInt();
              System.out.println("received " + theirNumber);
              if (theirNumber != (number * 2)) {
                throw new IllegalStateException("Received the wrong number: " + theirNumber);
              }
            }
          }
        } catch (IOException e) {
          if (server.isClosed()) {
            throw e;
          } else {
            e.printStackTrace();
          }
        }
      }
    }
  }

  @Override
  public void run() {
    try {
      main();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
