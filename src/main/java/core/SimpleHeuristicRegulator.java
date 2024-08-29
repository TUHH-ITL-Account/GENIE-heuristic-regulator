package core;

import static java.util.Map.entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import generator.caching.Cache;
import generator.exceptions.UnfulfillableException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import records.CompetencyEntry;
import records.ExerciseHistoryEntry;
import util.CollectionHelper;

public class SimpleHeuristicRegulator implements Runnable {

  protected static final Random RANDOM = new Random();
  protected final BlockingQueue<Task> receivedTasksQueue;
  protected final BlockingQueue<Task> finishedTasksQueue;
  protected final String[] inputArten = {"text-completion", "text-answer", "multiple-choice",
      "single-choice",
      "ordering", "assigning", "labeling", "calculation"};
  protected Task task;
  protected Map<String, Cache> cacheMap;
  protected MariaDbPoolDataSource pool;
  protected CompetencyEntry cEntry;
  protected List<ExerciseHistoryEntry> exerciseHistory;
  protected List<ExerciseHistoryEntry> last5;
  protected Map<String, String> paramSetters;

  public SimpleHeuristicRegulator() {
    this.receivedTasksQueue = new ArrayBlockingQueue<>(64);
    this.finishedTasksQueue = new ArrayBlockingQueue<>(64);
  }

  public SimpleHeuristicRegulator(BlockingQueue<Task> receivedTasksQueue,
      BlockingQueue<Task> finishedTasksQueue, Map<String, Cache> cacheMap,
      MariaDbPoolDataSource pool) {
    this.receivedTasksQueue = receivedTasksQueue;
    this.finishedTasksQueue = finishedTasksQueue;
    this.cacheMap = cacheMap;
    this.pool = pool;
  }

  public void setCompetencyEntry() throws SQLException {
    if (this.cEntry != null) {
      return;
    }
    try (Connection connection = this.pool.getConnection()) {
      try (Statement stmt = connection.createStatement()) {
        String topic = (String) this.task.getParameterMap().get("topic_actual");
        ResultSet rs = stmt.executeQuery(
            String.format(
                "SELECT * FROM Competencies INNER JOIN KnowledgeModels USING(`Topic`) WHERE Topic='%s' AND KnowledgeModel='%s'",
                topic,
                task.getCourseId()));
        if (!rs.next()) {
          System.out.printf(
              "WARNING: Could not find entry for topic '%s' in competencies table, aborting regulation.%n",
              topic
          );
          return;
        }
        this.cEntry = new CompetencyEntry(rs);
      }
    }
  }

  public void setExerciseHistory() throws SQLException, JsonProcessingException {
    if (this.exerciseHistory != null) {
      return;
    }
    this.exerciseHistory = new ArrayList<>();
    try (Connection connection = pool.getConnection()) {
      try (Statement stmt = connection.createStatement()) {
        ResultSet rs = stmt.executeQuery(String.format(
            "SELECT `Parameters`,`MaxPoints`,`Topic`,`TotalPoints` "
                + "FROM `Exercises` INNER JOIN `UserExercises` USING(`ExerciseId`) INNER JOIN KnowledgeModels USING(`Topic`)"
                + "WHERE UserId='%s' AND KnowledgeModel='%s' ORDER BY `GenerationDate` DESC",
            this.task.getUserId(), this.task.getCourseId()));
        while (rs.next()) {
          this.exerciseHistory.add(new ExerciseHistoryEntry(rs));
        }
      }
    }
  }

  protected void parameterCheck() {
    @SuppressWarnings("unchecked")
    Map<String, String> parSetters = (Map<String, String>) task.getParameterMap()
        .get("paramSetters");
    if (parSetters == null) {
      paramSetters = new HashMap<>();
      task.getParameterMap().put("paramSetters", paramSetters);
    } else {
      paramSetters = parSetters;
    }
  }

  public void regulate() throws UnfulfillableException, SQLException, JsonProcessingException {
    regulateTopic();
    regulateKKV();
    regulateDifficulty();
    regulateExerciseType();
  }

  public void regulateTopic() throws SQLException {
    if (task.getParameterMap().get("topic_actual") != null) {
      return;
    }
    List<CompetencyEntry> competencies = new ArrayList<>();
    try (Connection connection = this.pool.getConnection()) {
      try (Statement stmt = connection.createStatement()) {
        ResultSet rs = stmt.executeQuery(
            String.format(
                "SELECT * FROM Competencies INNER JOIN KnowledgeModels USING(`Topic`) WHERE UserId='%s' AND KnowledgeModel='%s' AND `FdlArray` NOT LIKE \"[]\"",
                task.getUserId(), task.getCourseId()));
        while (rs.next()) {
          competencies.add(new CompetencyEntry(rs));
        }
      }
    }
    List<Integer> lowestIndexes = new ArrayList<>();
    double lowestRating = 10;
    for (int i = 0; i < competencies.size(); i++) {
      double rating = evaluateCompetencyNeed(competencies.get(i));
      if (rating < lowestRating) {
        lowestIndexes = new ArrayList<>();
        lowestIndexes.add(i);
        lowestRating = rating;
      } else if (rating == lowestRating) {
        lowestIndexes.add(i);
      }
    }
    if (lowestRating == 10) { //equiv: lowestIndexes.isEmpty()
      lowestIndexes.add(RANDOM.nextInt(competencies.size()));
    }
    String topicChoice = competencies.get(lowestIndexes.get(RANDOM.nextInt(lowestIndexes.size())))
        .getTopic();
    task.getParameterMap().put("topic_actual", topicChoice);
    paramSetters.put("topic_actual", "RG");
  }

  protected double evaluateCompetencyNeed(CompetencyEntry entry) {
    double skillSum = entry.getKennenSkill() + entry.getKoennenSkill() + entry.getVerstehenSkill();
    if (skillSum == 0) {
      return 0;
    }
    int notZero = 0;
    if (entry.getKennenSkill() != 0) {
      notZero++;
    }
    if (entry.getKoennenSkill() != 0) {
      notZero++;
    }
    if (entry.getVerstehenSkill() != 0) {
      notZero++;
    }
    return skillSum / notZero;
  }

  public void regulateKKV() throws UnfulfillableException, SQLException, JsonProcessingException {
    task.getParameterMap().putIfAbsent("kennen", false);
    task.getParameterMap().putIfAbsent("koennen", false);
    task.getParameterMap().putIfAbsent("verstehen", false);
    boolean kennen = (boolean) task.getParameterMap().get("kennen");
    boolean koennen = (boolean) task.getParameterMap().get("koennen");
    boolean verstehen = (boolean) task.getParameterMap().get("verstehen");

    // catch only 1 being set, which means nothing to regulate here
    if (kennen ^ koennen ^ verstehen) {
      return;
    }
    // catch more than 1 kkv dimension being set
    if (kennen ^ koennen ? verstehen : kennen) {
      throw new UnfulfillableException("More than 1 KKV dimension were set to true.");
    }
    // retrieve and set data from db
    setCompetencyEntry();
    setExerciseHistory();
    // [ASSUMPTION: kennen exercises are always possible] if kennen below 5, select that
    if (cEntry.getKennenSkill() < 5) {
      task.getParameterMap().put("kennen", true);
      return;
    }
    // get the last 5 exercises for the topic
    last5 = new ArrayList<>();
    for (int i = 0; i < exerciseHistory.size() && last5.size() < 5; i++) {
      if (exerciseHistory.get(i).getTopic().equals(task.getParameterMap().get("topic_actual"))) {
        last5.add(exerciseHistory.get(i));
      }
    }
    if (cEntry.getKoennenSkill() < 5) {
      // last 3 exercises already were koennen, give kennen
      if (checkLastNDims("koennen", 3)) {
        task.getParameterMap().put("kennen", true);
        return;
      }
      task.getParameterMap().put("koennen", true);
      return;
    }
    if (cEntry.getVerstehenSkill() < 5) {
      // last 2 exercises already were verstehen, give koennen
      if (checkLastNDims("verstehen", 2)) {
        task.getParameterMap().put("koennen", true);
        return;
      }
      task.getParameterMap().put("verstehen", true);
      return;
    }
    // all skill levels are above 5, select lowest if not all are maxed
    if (cEntry.getKennenSkill() < 10 && cEntry.getKoennenSkill() < 10
        && cEntry.getVerstehenSkill() < 10) {
      if (cEntry.getKennenSkill() < cEntry.getKoennenSkill()
          && cEntry.getKennenSkill() < cEntry.getVerstehenSkill()) {
        task.getParameterMap().put("kennen", true);
        return;
      } else if (cEntry.getKoennenSkill() < cEntry.getVerstehenSkill()) {
        task.getParameterMap().put("koennen", true);
        return;
      } else {
        task.getParameterMap().put("verstehen", true);
        return;
      }
    }
    // all skill dimensions are maxed, select based on lowest performance, defaulting to verstehen on tie
    if (cEntry.getKennenPerformance() < cEntry.getKoennenPerformance()
        && cEntry.getKennenPerformance() < cEntry.getVerstehenPerformance()) {
      task.getParameterMap().put("kennen", true);
    } else if (cEntry.getKoennenPerformance() < cEntry.getVerstehenPerformance()) {
      task.getParameterMap().put("koennen", true);
    } else {
      task.getParameterMap().put("verstehen", true);
    }
    paramSetters.put("kkv", "RG");
  }

  /**
   * Checks whether the last n entries of the last 5 exercises were of KKV dimension dim.
   *
   * @param dim - "kennen"|"koennen"|"verstehen"
   * @param n   - number of exercises to look back
   * @return true iff the last n exercises were of dimension dim
   */
  public boolean checkLastNDims(String dim, int n) {
    if (last5 == null) {
      return false;
    }
    if (last5.isEmpty()) {
      return false;
    }
    for (int i = 0; i < n && i < last5.size(); i++) {
      if (!((boolean) last5.get(i).getParameters().get(dim))) {
        return false;
      }
    }
    return true;
  }

  public void regulateDifficulty() {
    if (task.getParameterMap().containsKey("difficulty")) {
      return;
    }
    if ((boolean) task.getParameterMap().get("kennen")) {
      task.getParameterMap()
          .put("difficulty", Math.min((int) Math.ceil(cEntry.getKennenSkill() + 1), 10));
    } else if ((boolean) task.getParameterMap().get("koennen")) {
      task.getParameterMap()
          .put("difficulty", Math.min((int) Math.ceil(cEntry.getKoennenSkill() + 1), 10));
    } else {
      task.getParameterMap()
          .put("difficulty", Math.min((int) Math.ceil(cEntry.getVerstehenSkill() + 1), 10));
    }
    paramSetters.put("difficulty", "RG");
  }

  public void regulateExerciseType() throws UnfulfillableException {
    if (task.getParameterMap().containsKey("exercise_type_recommended")) {
      return;
    }

    Set<String> possibleTypes;
    if (task.getParameterMap().containsKey("exercise_type_whitelist")) {
      //assert task.getParameterMap().get("exercise_type_whitelist") instanceof Set; //todo: mimimi cast check
      if (((Set<String>) task.getParameterMap().get("exercise_type_whitelist")).isEmpty()) {
        throw new UnfulfillableException("Exercise-type-whitelist is given, but empty");
      }
      possibleTypes = (Set<String>) task.getParameterMap().get("exercise_type_whitelist");
    } else {
      possibleTypes = new HashSet<>(List.of(inputArten));
    }
    if (task.getParameterMap().containsKey("exercise_type_blacklist")) {
      for (String type : (Set<String>) task.getParameterMap().get("exercise_type_blacklist")) {
        possibleTypes.remove(type);
      }
    }
    Map<String, Double[]> typeDistMap = Map.ofEntries(
        entry("text-completion", new Double[]{2.0, 2.0}),
        entry("labeling", new Double[]{2.0, 2.0}),
        entry("single-choice", new Double[]{4.0, 2.0}),
        entry("multiple-choice", new Double[]{5.0, 3.0}),
        entry("ordering", new Double[]{5.0, 2.0}),
        entry("assigning", new Double[]{6.0, 2.0}),
        entry("text-answer", new Double[]{7.0, 2.0}),
        entry("calculation", new Double[]{7.0, 2.0})
    );
    Map<String, Double> probMap = new HashMap<>();
    for (var entry : typeDistMap.entrySet()) {
      probMap.put(entry.getKey(), getNormDistProb(entry.getValue()[0], entry.getValue()[1],
          (int) task.getParameterMap().get("difficulty")));
    }
    Map<String, Double> orderedProbMap = CollectionHelper.sortByValue(probMap);
    double totalProb = 0;
    for (double d : probMap.values()) {
      totalProb += d;
    }
    double randomPick = RANDOM.nextDouble();
    String pick = null;
    double covered = 0;
    List<Entry<String, Double>> orderedEntryList = orderedProbMap.entrySet().stream().toList();
    for (Entry<String, Double> stringDoubleEntry : orderedEntryList) {
      covered += stringDoubleEntry.getValue() / totalProb;
      if (covered > randomPick) {
        pick = stringDoubleEntry.getKey();
        break;
      }
    }
    if (pick == null) {
      pick = orderedEntryList.get(orderedEntryList.size() - 1).getKey();
    }
    List<String> recommendation = new ArrayList<>();
    recommendation.add(pick);
    for (int i = orderedEntryList.size() - 1; i >= 0; i--) {
      if (!orderedEntryList.get(i).getKey().equals(pick)) {
        recommendation.add(orderedEntryList.get(i).getKey());
      }
    }
    task.getParameterMap().put("exercise_type_recommended", recommendation);
  }


  /**
   * Calculate the probability given a normal distribution with given mean and standard deviation
   *
   * @param mean         of the normal distribution
   * @param stdDeviation of the normal distribution
   * @param x            location of desired probability
   * @return probability according to the normal distribution defined by the arguments
   */
  public double getNormDistProb(double mean, double stdDeviation, double x) {
    return (1 / (stdDeviation * Math.sqrt(2 * Math.PI))) * Math.pow(Math.E,
        -0.5 * Math.pow((x - mean) / (stdDeviation), 2));
  }

  @Override
  public void run() {
    while (!Thread.interrupted()) {
      try {
        this.task = this.receivedTasksQueue.take();
        parameterCheck();
        regulate();
        this.finishedTasksQueue.put(this.task);
      } catch (InterruptedException | UnfulfillableException | SQLException | JsonProcessingException e) {
        e.printStackTrace();
      }
    }
  }
}
