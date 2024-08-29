package core;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import generator.caching.Cache;
import generator.exceptions.UnfulfillableException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import records.ExerciseHistoryEntry;

public class RandomForestRegulator extends SimpleHeuristicRegulator {

  public boolean mlOK = true;
  private OrtEnvironment env;
  private OrtSession session;

  public RandomForestRegulator() {
    super();
  }

  public RandomForestRegulator(BlockingQueue<Task> receivedTasksQueue,
      BlockingQueue<Task> finishedTasksQueue, Map<String, Cache> cacheMap,
      MariaDbPoolDataSource pool) {
    super(receivedTasksQueue, finishedTasksQueue, cacheMap, pool);
    setupOnnxSession();
  }

  private void setupOnnxSession() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("models/rf_skillAndDiff.onnx");
    env = OrtEnvironment.getEnvironment();
    try (OrtSession.SessionOptions opts = new SessionOptions()) {
      opts.setOptimizationLevel(OptLevel.BASIC_OPT);
      try {
        session = env.createSession(inputStream.readAllBytes(), opts);
      } catch (OrtException e) {
        mlOK = false;
        System.out.println("Error creating Onnx sesssion:");
        e.printStackTrace();
      } catch (IOException e) {
        System.out.println("Error reading model:");
        e.printStackTrace();
      }
    } catch (OrtException e) {
      mlOK = false;
      System.out.println("Error creating Onnx using session options:");
      e.printStackTrace();
    }
  }

  @Override
  public void regulateDifficulty() {
    if (task.getParameterMap().containsKey("difficulty")) {
      return;
    }
    String topic = (String) task.getParameterMap().get("topic_actual");
    if (topic == null) {
      task.getParameterMap().put("difficulty", 6);
      System.out.println("RF regulator got difficulty without topic?");
      return;
    }
    List<ExerciseHistoryEntry> exForTopic = new ArrayList<>();
    for (int i = 0; i < exerciseHistory.size(); i++) {
      ExerciseHistoryEntry current = exerciseHistory.get(i);
      if (current.getTopic().equals(topic) && current.getAchievedPoints() == 1f) {
        exForTopic.add(current);
      }
    }
    List<Float[]> skillProgression = new ArrayList<>();
    skillProgression.add(new Float[]{0f, 0f, 0f});
    for (int i = exForTopic.size() - 1; i >= 0; i--) {
      Integer iterDifficulty = (Integer) exForTopic.get(i).getParameters().get("difficulty");
      if (iterDifficulty == null) {
        task.getParameterMap().put("difficulty", 6);
        System.out.println("RF regulator got exercise without difficulty?");
        return;
      }
      int index = exForTopic.get(i).getParameters().get("kennen") != null &&
          (boolean) exForTopic.get(i).getParameters().get("kennen") ? 0 :
          exForTopic.get(i).getParameters().get("koennen") != null &&
              (boolean) exForTopic.get(i).getParameters().get("koennen") ? 1 : 2;
      Float[] newSkill = skillProgression.get(skillProgression.size() - 1).clone();
      newSkill[index] = newSkill[index] + learningCurve(iterDifficulty, skillProgression.get(
          skillProgression.size() - 1)[index]);
      skillProgression.add(newSkill);
    }
    try {
      float[][] sourceArray = new float[1][19];
      float kennenPrev = -1f;
      float koennenPrev = -1f;
      float verstehenPrev = -1f;
      float difficultyPrev = -1f;
      if (exForTopic.size() > 0) {
        kennenPrev =
            (boolean) exForTopic.get(exForTopic.size() - 1).getParameters().get("kennen") ? 1f : 0f;
        koennenPrev =
            (boolean) exForTopic.get(exForTopic.size() - 1).getParameters().get("koennen") ? 1f
                : 0f;
        verstehenPrev =
            (boolean) exForTopic.get(exForTopic.size() - 1).getParameters().get("verstehen") ? 1f
                : 0f;
        difficultyPrev = (float) exForTopic.get(exForTopic.size() - 1).getParameters()
            .get("difficulty");
      }
      float kennenPrevPrev = -1f;
      float koennenPrevPrev = -1f;
      float verstehenPrevPrev = -1f;
      float difficultyPrevPrev = -1f;
      if (exForTopic.size() > 1) {
        kennenPrevPrev =
            (boolean) exForTopic.get(exForTopic.size() - 2).getParameters().get("kennen") ? 1f : 0f;
        koennenPrevPrev =
            (boolean) exForTopic.get(exForTopic.size() - 2).getParameters().get("koennen") ? 1f
                : 0f;
        verstehenPrevPrev =
            (boolean) exForTopic.get(exForTopic.size() - 2).getParameters().get("verstehen") ? 1f
                : 0f;
        difficultyPrevPrev = (float) exForTopic.get(exForTopic.size() - 2).getParameters()
            .get("difficulty");
      }
      float kennenPrevPrevPrev = -1f;
      float koennenPrevPrevPrev = -1f;
      float verstehenPrevPrevPrev = -1f;
      float difficultyPrevPrevPrev = -1f;
      if (exForTopic.size() > 2) {
        kennenPrevPrevPrev =
            (boolean) exForTopic.get(exForTopic.size() - 3).getParameters().get("kennen") ? 1f : 0f;
        koennenPrevPrevPrev =
            (boolean) exForTopic.get(exForTopic.size() - 3).getParameters().get("koennen") ? 1f
                : 0f;
        verstehenPrevPrevPrev =
            (boolean) exForTopic.get(exForTopic.size() - 3).getParameters().get("verstehen") ? 1f
                : 0f;
        difficultyPrevPrevPrev = (float) exForTopic.get(exForTopic.size() - 3).getParameters()
            .get("difficulty");
      }

      sourceArray[0] = new float[]{
          (boolean) task.getParameterMap().get("kennen") ? 1f : 0f,
          (boolean) task.getParameterMap().get("koennen") ? 1f : 0f,
          (boolean) task.getParameterMap().get("verstehen") ? 1f : 0f,
          5f, //placeholder
          skillProgression.get(skillProgression.size() - 1)[0],
          skillProgression.get(skillProgression.size() - 1)[1],
          skillProgression.get(skillProgression.size() - 1)[2],
          kennenPrev,
          koennenPrev,
          verstehenPrev,
          difficultyPrev,
          kennenPrevPrev,
          koennenPrevPrev,
          verstehenPrevPrev,
          difficultyPrevPrev,
          kennenPrevPrevPrev,
          koennenPrevPrevPrev,
          verstehenPrevPrevPrev,
          difficultyPrevPrevPrev
      };
      int[] predictions = new int[10];
      for (int i = 0; i < 10; i++) {
        sourceArray[0][3] = (float) (i+1);
        OnnxTensor tensorFromArray = OnnxTensor.createTensor(env, sourceArray);
        Map<String, OnnxTensor> runIn = Collections.singletonMap("X", tensorFromArray);
        Result result = session.run(runIn);
        OnnxTensor output = (OnnxTensor) result.get(0);
        predictions[i] = (int) ((long[]) output.getValue())[0];
      }
      int maxIndex = 0;
      for (int i = 0; i < predictions.length; i++) {
        if (predictions[i] == 1) {
          maxIndex = i;
        }
      }
      int chosenDifficulty = maxIndex + 1;
      task.getParameterMap().put("difficulty", chosenDifficulty);
      paramSetters.put("difficulty", "RG-ML");
    } catch (OrtException e) {
      System.out.println("Error using Onnx model:");
      e.printStackTrace();
    }
  }

  /**
   * Emulates a learning increase in style of a sigmoid curve
   *
   * @param difficulty   the difficulty of the exercise which was solved
   * @param currentSkill the pre-solving skill
   * @return a double representing the skill increase
   */
  private float learningCurve(int difficulty, float currentSkill) {
    return (float) ((1f / (1f + Math.pow(Math.E, 5f - difficulty)) + 0.5f) * 0.5f *
        Math.max(2f, difficulty - currentSkill));
  }

  public void regulateHeuristicly()
      throws UnfulfillableException, SQLException, JsonProcessingException {
    regulateTopic();
    regulateKKV();
    super.regulateDifficulty();
    regulateExerciseType();
  }

  @Override
  public void run() {
    while (!Thread.interrupted()) {
      try {
        this.task = this.receivedTasksQueue.take();
        parameterCheck();
        boolean useML = true;
        Object noAI = task.getParameterMap().get("noAI");
        if(noAI instanceof Boolean) {
          useML = (Boolean) noAI;
        } else if(noAI instanceof String) {
          useML = ((String) noAI).equals("2");
        } else if(noAI instanceof Integer) {
          useML = (Integer)noAI == 2;
        }
        Object AI = task.getParameterMap().get("AI");
        boolean AIisML = AI != null && (AI instanceof String ? ((String)AI).equals("2") : (
            AI instanceof Integer && (Integer) AI == 2));
        if ((useML || AIisML) && mlOK) {
          regulate();
        } else {
          regulateHeuristicly();
        }
        this.finishedTasksQueue.put(this.task);
      } catch (InterruptedException | UnfulfillableException | SQLException | JsonProcessingException e) {
        e.printStackTrace();
      }
    }
  }
}
