package core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;

public class TaskSender implements Runnable {

  final Socket regulatorSocket;
  final BlockingQueue<Task> finishedTasksQueue;

  public TaskSender(Socket socket, BlockingQueue<Task> finishedTasksQueue) {
    this.regulatorSocket = socket;
    this.finishedTasksQueue = finishedTasksQueue;
  }

  @Override
  public void run() {
    try (OutputStream os = regulatorSocket.getOutputStream()) {
      DataOutputStream ds = new DataOutputStream(os);
      while (!Thread.interrupted()) {
        try {
          Task task = finishedTasksQueue.take();
          String message = task.task2String();
          System.out.println("Sending message to controller: " + message);
          ds.writeUTF(message);
        } catch (SocketException e) {
          System.out.println(
              "Stream closed trying to write. Terminating RegulatorSender thread...");
          break;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      System.out.println(
          "RegulatorSender thread interrupted, terminating...");
    }
  }
}
