package core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import exceptions.MalformedMessageException;
import generator.types.GenerationTask.EXERCISE_TYPE;
import generator.types.GenerationTask.SOLUTION_TYPE;
import java.util.HashMap;
import java.util.Map;

public class Task {

  public static String MESSAGE_DELIMITER = "##";

  private final String requestId;
  private final String userId;
  private final String courseId;
  private final TASK_TYPE taskType;
  private final String origMessage;
  private final EXERCISE_TYPE exerciseType;
  private final SOLUTION_TYPE solutionType;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private String jsonParameters;
  private Map<String, Object> parameterMap;

  public Task(String message) throws MalformedMessageException, JsonProcessingException {
    this.origMessage = message;
    String[] parts = message.split(MESSAGE_DELIMITER, -1);
    if (parts.length != 7) {
      throw new MalformedMessageException(
          "Unable to split message according to delimiter '" + MESSAGE_DELIMITER + "'.");
    }
    requestId = parts[0];
    userId = parts[1];
    courseId = parts[2];
    String jsonParameters = parts[3];
    if (jsonParameters.equals("")) {
      parameterMap = new HashMap<>();
    } else {
      parameterMap = objectMapper.readValue(jsonParameters,
          new TypeReference<Map<String, Object>>() {
          });
    }
    exerciseType = EXERCISE_TYPE.values()[Integer.parseInt(parts[4])];
    solutionType = SOLUTION_TYPE.values()[Integer.parseInt(parts[5])];
    taskType = TASK_TYPE.values()[Integer.parseInt(parts[6])];
  }

  public void convertJson2Map() throws JsonProcessingException {
    this.parameterMap = objectMapper.readValue(this.jsonParameters,
        new TypeReference<Map<String, Object>>() {
        });
  }

  public void convertMap2Json() throws JsonProcessingException {
    this.jsonParameters = objectMapper.writeValueAsString(this.parameterMap);
  }

  public String task2String() throws JsonProcessingException {
    convertMap2Json();
    return String.join(MESSAGE_DELIMITER, requestId, userId, courseId, jsonParameters,
        String.valueOf(exerciseType.ordinal()), String.valueOf(solutionType.ordinal()),
        String.valueOf(taskType.ordinal()));
  }

  public String getRequestId() {
    return requestId;
  }

  public String getUserId() {
    return userId;
  }

  public String getCourseId() {
    return courseId;
  }

  public String getJsonParameters() {
    return jsonParameters;
  }

  public Map<String, Object> getParameterMap() {
    return parameterMap;
  }

  public TASK_TYPE getTaskType() {
    return taskType;
  }

  public String getOrigMessage() {
    return origMessage;
  }

  enum TASK_TYPE {
    COURSE,
    PART_TREE,
    TOPIC,
    FDL
  }
}
