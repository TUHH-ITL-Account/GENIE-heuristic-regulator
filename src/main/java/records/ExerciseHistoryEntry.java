package records;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ExerciseHistoryEntry {

  private final Map<String, Object> parameters;
  private final float achievedPoints;
  private final String topic;
  private float maxPoints;

  //`Parameters`,`MaxPoints`,`Topic`,`TotalPoints`

  public ExerciseHistoryEntry(ResultSet rs) throws SQLException, JsonProcessingException {
    topic = rs.getString("Topic");
    achievedPoints = rs.getFloat("TotalPoints");
    ObjectMapper mapper = new ObjectMapper();
    String paras = rs.getString("Parameters");
    parameters =
        paras == null || paras.equals("") ? new HashMap<>() : mapper.readValue(paras, Map.class);
    //Map<String, Float> pointsPerPart = mapper.readValue(rs.getString("MaxPoints"), Map.class);
    //for (float f : pointsPerPart.values()) {
    //  this.maxPoints += f;
    //}
    maxPoints = 1;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public float getAchievedPoints() {
    return achievedPoints;
  }

  public float getMaxPoints() {
    return maxPoints;
  }

  public String getTopic() {
    return topic;
  }
}
