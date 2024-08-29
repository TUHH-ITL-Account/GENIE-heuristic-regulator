package core;

import exceptions.MalformedMessageException;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;

public class TaskReceiver implements Runnable {

  final Socket regulatorSocket;
  final BlockingQueue<Task> receivedTasksQueue;

  public TaskReceiver(Socket socket, BlockingQueue<Task> receivedTasksQueue) {
    this.regulatorSocket = socket;
    this.receivedTasksQueue = receivedTasksQueue;
  }

  @Override
  public void run() {
    try (InputStream is = regulatorSocket.getInputStream()) {
      DataInputStream din = new DataInputStream(is);
      while (!Thread.interrupted()) {
        try {
          String message = din.readUTF();
          System.out.println("Received message from controller: " + message);
          Task task = new Task(message);
          receivedTasksQueue.put(task);
        } catch (SocketException e) {
          System.out.println(
              "Stream closed while waiting for read. Terminating RegulatorReceiver thread...");
          break;
        } catch (Exception e) {
          System.out.println("Exception in regulator-TaskReceiver: ");
          e.printStackTrace();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
