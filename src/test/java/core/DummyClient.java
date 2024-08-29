package core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class DummyClient implements Runnable {

  private String socketPath;
  private String socketName;

  public DummyClient(String socketPath, String socketName) {
    this.socketPath = socketPath;
    this.socketName = socketName;
  }

  public void main() throws IOException, InterruptedException {
    final File socketFile = new File(new File(socketPath),
        socketName);

    try (AFUNIXSocket sock = AFUNIXSocket.newInstance()) {
      while (!Thread.interrupted()) {
        try {
          sock.connect(AFUNIXSocketAddress.of(socketFile));
          break;
        } catch (SocketException e) {
          System.out.println("Cannot connect to server. Have you started it?");
          System.out.println();
          Thread.sleep(5000);
        }
      }
      System.out.println("Connected");

      try (InputStream is = sock.getInputStream(); //
          OutputStream os = sock.getOutputStream();) {

        DataInputStream din = new DataInputStream(is);
        DataOutputStream dout = new DataOutputStream(os);

        /*
        byte[] buf = new byte[128];

        int read = is.read(buf);
        System.out.println("Server says: " + new String(buf, 0, read, StandardCharsets.UTF_8));

        String handshake = din.readUTF();
        System.out.println("Server says: " + handshake);

        System.out.println("Replying to server...");
        //os.write("Hello Server".getBytes(StandardCharsets.UTF_8));
        //os.flush();
        dout.writeUTF("Hello Server");
        dout.flush();

        System.out.println("Now reading numbers from the server...");
*/
        while (!Thread.interrupted()) {
          /*int number = din.readInt();
          if (number == -123) {
            // by convention of this demo, if the number is -123, we stop.
            // If we don't do this, we'll get an EOFException upon the next unsuccessful read.
            break;
          }
          System.out.println(number);

          int ourNumber = number * 2;

          System.out.println("Sending back " + ourNumber);
          dout.writeInt(ourNumber);*/
          Thread.sleep(60000);
        }
      }
    }

    System.out.println("End of communication.");
  }

  @Override
  public void run() {
    try {
      main();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}
