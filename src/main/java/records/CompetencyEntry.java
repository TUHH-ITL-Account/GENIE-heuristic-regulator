package records;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CompetencyEntry {

  private final String topic;
  private final float kennenSkill;
  private final float koennenSkill;
  private final float verstehenSkill;
  private final float kennenPerformance;
  private final float koennenPerformance;
  private final float verstehenPerformance;
  private final float kennenAccuracy;
  private final float koennenAccuracy;
  private final float verstehenAccuracy;
  private final float kennenConfidence;
  private final float koennenConfidence;
  private final float verstehenConfidence;

  public CompetencyEntry(ResultSet rs) throws SQLException {
    this.topic = rs.getString("Topic");
    this.kennenSkill = rs.getFloat("KennenSkill");
    this.koennenSkill = rs.getFloat("KoennenSkill");
    this.verstehenSkill = rs.getFloat("VerstehenSkill");
    this.kennenPerformance = rs.getFloat("KennenPerformance");
    this.koennenPerformance = rs.getFloat("KoennenPerformance");
    this.verstehenPerformance = rs.getFloat("VerstehenPerformance");
    this.kennenAccuracy = rs.getFloat("KennenAccuracy");
    this.koennenAccuracy = rs.getFloat("KoennenAccuracy");
    this.verstehenAccuracy = rs.getFloat("VerstehenAccuracy");
    this.kennenConfidence = rs.getFloat("KennenConfidence");
    this.koennenConfidence = rs.getFloat("KoennenConfidence");
    this.verstehenConfidence = rs.getFloat("VerstehenConfidence");
  }

  public float getKennenSkill() {
    return kennenSkill;
  }

  public float getKoennenSkill() {
    return koennenSkill;
  }

  public float getVerstehenSkill() {
    return verstehenSkill;
  }

  public float getKennenPerformance() {
    return kennenPerformance;
  }

  public float getKoennenPerformance() {
    return koennenPerformance;
  }

  public float getVerstehenPerformance() {
    return verstehenPerformance;
  }

  public float getKennenAccuracy() {
    return kennenAccuracy;
  }

  public float getKoennenAccuracy() {
    return koennenAccuracy;
  }

  public float getVerstehenAccuracy() {
    return verstehenAccuracy;
  }

  public float getKennenConfidence() {
    return kennenConfidence;
  }

  public float getKoennenConfidence() {
    return koennenConfidence;
  }

  public float getVerstehenConfidence() {
    return verstehenConfidence;
  }

  public String getTopic() {
    return topic;
  }
}
