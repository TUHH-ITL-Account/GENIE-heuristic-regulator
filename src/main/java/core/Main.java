package core;

import exceptions.ConfigException;
import generator.caching.Cache;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class Main {

  public static boolean DEBUG = true;
  private static int MAX_THREADS = 5;
  private static int QUEUE_SIZE = 128;
  private static String UDSOCKETS_DIR;
  private static String LOG_DIR;
  private static String OS = "Unix";
  private static String PIPE_NAME;
  private static String DB_NAME;
  private static String DB_USER;
  private static String DB_PASSWORD;
  private static String DB_HOST = "localhost";
  private static String DB_PORT = "3306";
  private static String DB_SOCKET = "";
  private static String MODEL_DIR = "";
  private static String[] PRELOADED_MODELS = {};

  private Main() {
    throw new UnsupportedOperationException("No instances");
  }

  public static void main(String[] args) throws Exception {
    // argument parsing
    String config = null;
    String dbConfig = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-c", "--config" -> {
          i++;
          if (i < args.length) {
            config = args[i];
          }
        }
        case "-dc", "--dbconfig" -> {
          i++;
          if (i < args.length) {
            dbConfig = args[i];
          }
        }
      }
    }
    // do config stuff
    if (config == null) {
      config = "heureg.config";
    }
    if (dbConfig == null) {
      dbConfig = "db.config";
    }
    File configFile = new File(config);
    if (!configFile.exists()) {
      throw new ConfigException(
          "Config file '" + configFile.getAbsoluteFile() + "' does not exist.");
    }
    Properties properties = loadConfig(configFile);
    checkConfig(properties);
    setConfigProperties(properties);

    File dbconfigFile = new File(dbConfig);
    if (!dbconfigFile.exists()) {
      throw new ConfigException(
          "Config file '" + dbconfigFile.getAbsoluteFile() + "' does not exist.");
    }
    Properties dbproperties = loadConfig(dbconfigFile);
    checkConfig(dbproperties);
    setDBConfigProperties(dbproperties);

    // load knowledge models without parsing FDLs
    Map<String, Cache> cacheMap = new HashMap<>();
    for (String model : PRELOADED_MODELS) {
      System.out.println("Caching: '" + model + "' from '" + MODEL_DIR + "'");
      cacheMap.putIfAbsent(model, new Cache(MODEL_DIR, model, false));
    }

    // MariaDB uses named pipes on Windows vs unix-domain-sockets on Unix
    if (System.getProperty("os.name").startsWith("Windows")) {
      OS = "Windows";
      if (PIPE_NAME == null) {
        throw new Exception("Argument for pipe-name required on Windows.");
      }
    }
    // try connecting to a running MariaDB server/process
    String connectionUrl;
    if (OS.equals("Windows")) {
      connectionUrl = String.format(
          "jdbc:mariadb://%s:%s/%s?pipe=%s&user=%s&password=%s&pool=1&minPoolSize=%o&maxPoolSize=%o",
          DB_HOST, DB_PORT, DB_NAME, PIPE_NAME, DB_USER, DB_PASSWORD, MAX_THREADS, MAX_THREADS);
    } else {
      if (DB_SOCKET.equals("")) {
        connectionUrl = String.format(
            "jdbc:mariadb://%s/%s?user=%s&password=%s&maxPoolSize=%o",
            DB_HOST, DB_NAME, DB_USER, DB_PASSWORD, MAX_THREADS);
      } else {
        connectionUrl = String.format(
            "jdbc:mariadb://%s/%s?user=%s&password=%s&localSocket=%s&maxPoolSize=%o",
            DB_HOST, DB_NAME, DB_USER, DB_PASSWORD, DB_SOCKET, MAX_THREADS);
      }
    }
    // use Connection pool to be used by threads
    MariaDbPoolDataSource pool = new MariaDbPoolDataSource(connectionUrl);
    System.out.println("Connected to DB via: " + pool.getUrl());

    // start threads
    System.out.println("Starting heuristic regulator...");

    BlockingQueue<Task> receivedTasksQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    BlockingQueue<Task> finishedTasksQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);

    Thread[] regulatorThreads = new Thread[MAX_THREADS];
    for (int i = 0; i < regulatorThreads.length; i++) {
      regulatorThreads[i] = new Thread(
          new RandomForestRegulator(receivedTasksQueue, finishedTasksQueue, cacheMap, pool));
    }

    final File regulatorSocketFile = new File(new File(UDSOCKETS_DIR),
        "junixsocket-controller2regulator.sock");

    try (AFUNIXSocket sock = AFUNIXSocket.newInstance()) {

      Thread receiverThread = new Thread(new TaskReceiver(sock, receivedTasksQueue));
      Thread senderThread = new Thread(new TaskSender(sock, finishedTasksQueue));

      while (!Thread.interrupted()) {
        try {
          sock.connect(AFUNIXSocketAddress.of(regulatorSocketFile));
          break;
        } catch (SocketException e) {
          System.out.println(
              "HeuReg: Cannot connect to controller. Have you started it? Waiting 5s until retry...");
          System.out.println();
          Thread.sleep(5000);
        }
      }

      boolean running = true;
      while (!Thread.interrupted() && running) {
        //System.out.println("Waiting for connection from frontend...");
        try {
          // possibly create handshake Threads here and block until finished //

          receiverThread.start();
          senderThread.start();
          for (Thread regulatorThread : regulatorThreads) {
            // daemon means Threads keep running; if used add proper shutdown!
            regulatorThread.setDaemon(false);
            regulatorThread.start();
          }
          System.out.println("HeuReg: Started all threads.");

          // use main program to check threads and queues (every 10s)
          int lastRecQCap = 0, lastSenQCap = 0, lastFinQCap = 0;
          while (!Thread.interrupted() && running) {
            Thread.sleep(10000);
            if(!receiverThread.isAlive() || receiverThread.isInterrupted()) {
              System.out.println("HeuReg: Something happened to the receiver thread!");
            }
            if(!senderThread.isAlive() || senderThread.isInterrupted()) {
              System.out.println("HeuReg: Something happened to the sender thread!");
            }
            for(Thread regThread : regulatorThreads) {
              if (!regThread.isAlive() || regThread.isInterrupted()) {
                System.out.println("HeuReg: Something happened to a regulator thread!");
              }
            }

            int currRecQCap = receivedTasksQueue.remainingCapacity();
            if(lastRecQCap - currRecQCap > 5) {
              System.out.println("HeuReg: The receiver queue is stacking up?");
            }
            lastRecQCap = currRecQCap;

            int currSenQCap = receivedTasksQueue.remainingCapacity();
            if(lastSenQCap - currSenQCap > 5) {
              System.out.println("HeuReg: The sender queue is stacking up?");
            }
            lastSenQCap = currSenQCap;

            int currFinQCap = finishedTasksQueue.remainingCapacity();
            if(lastFinQCap - currFinQCap > 5) {
              System.out.println("HeuReg: The finished tasks queue is stacking up?");
            }
            lastFinQCap = currFinQCap;
          }
        } catch (InterruptedException e) {
          running = false;
        }
      }
    }
  }

  protected static Properties loadConfig(File file) throws IOException {
    Properties prop = new Properties();
    FileInputStream fis = new FileInputStream(file);
    prop.load(fis);
    return prop;
  }

  protected static void checkConfig(Properties prop) throws ConfigException {
    // check for mandatory properties
    /*if(!prop.containsKey("udsocket_path")) {
      throw new ConfigException("Missing mandatory property in config: udsocket_path");
    }*/
    // check types of properties
    for (String key : prop.stringPropertyNames()) {
      switch (key) {
        case "max_threads":
          try {
            Integer.parseInt(prop.getProperty(key));
          } catch (NumberFormatException e) {
            throw new ConfigException("Property 'max_threads' must be an integer.");
          }
          break;
        case "queue_size":
          try {
            Integer.parseInt(prop.getProperty(key));
          } catch (NumberFormatException e) {
            throw new ConfigException("Property 'queue_size' must be an integer.");
          }
          break;
        case "model_dir":
          File mdir = new File(prop.getProperty(key));
          if (!mdir.exists()) {
            if (!mdir.mkdirs()) {
              throw new ConfigException(
                  "Unable to create non-existent directories specified in 'models_dir'.");
            }
          }
          if (!mdir.isDirectory()) {
            throw new ConfigException("'models_dir' does not point to a directory, but a file.");
          }
          if (!mdir.canRead()) {
            throw new ConfigException("No read permissions in 'models_dir'.");
          }
          break;
        case "log_dir":
          File ldir = new File(prop.getProperty(key));
          if (!ldir.exists()) {
            if (!ldir.mkdirs()) {
              throw new ConfigException(
                  "Unable to create non-existent directories specified in 'logs_dir'.");
            }
          }
          if (!ldir.isDirectory()) {
            throw new ConfigException("'logs_dir' does not point to a directory, but a file.");
          }
          if (!ldir.canWrite()) {
            throw new ConfigException("No write permissions in 'logs_dir'.");
          }
      }
    }
  }

  private static void setConfigProperties(Properties prop) {
    String workingDir = System.getProperty("user.dir");
    UDSOCKETS_DIR =
        Boolean.parseBoolean((String) prop.getOrDefault("udsockets_use_temp", "false")) ?
            System.getProperty("java.io.tmpdir")
            : (String) prop.getOrDefault("udsockets_dir", System.getProperty("java.io.tmpdir"));
    PIPE_NAME = (String) prop.getOrDefault("mariadb_pipe_name", "MariaDB");
    LOG_DIR = (String) prop.getOrDefault("log_dir", workingDir + "/logs");
    MAX_THREADS = Integer.parseInt((String) prop.getOrDefault("max_threads", "5"));
    QUEUE_SIZE = Integer.parseInt((String) prop.getOrDefault("queue_size", "100"));
    MODEL_DIR = (String) prop.getOrDefault("model_dir", workingDir + "/models");
    PRELOADED_MODELS = ((String) prop.getOrDefault("preloaded_models", "")).split(",");
    DEBUG = Boolean.parseBoolean((String) prop.getOrDefault("debug", "true"));
  }

  private static void setDBConfigProperties(Properties prop) {
    DB_NAME = (String) prop.getOrDefault("db_names", "genDB");
    DB_USER = (String) prop.getOrDefault("db_user", "root");
    DB_PASSWORD = (String) prop.getOrDefault("db_password", "admin");
    DB_HOST = (String) prop.getOrDefault("db_host", "localhost");
    DB_PORT = (String) prop.getOrDefault("db_port", "3306");
    DB_SOCKET = (String) prop.getOrDefault("db_socket", "");
  }
}
