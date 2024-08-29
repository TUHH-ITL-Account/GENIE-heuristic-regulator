package core;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ai.onnxruntime.OrtEnvironment;

public class OnnxTest {

  @Test
  public void testOnnxImportAndPrediction() {
    OrtEnvironment env = OrtEnvironment.getEnvironment();
    try (OrtSession.SessionOptions opts = new SessionOptions()) {
      opts.setOptimizationLevel(OptLevel.BASIC_OPT);
      try (OrtSession session = env.createSession("src/main/resources/models/rf_skillAndDiff.onnx", opts)) {
        System.out.println("Inputs:");
        for (NodeInfo i : session.getInputInfo().values()) {
          System.out.println(i.toString());
        }
        System.out.println("Outputs:");
        for (NodeInfo i : session.getOutputInfo().values()) {
          System.out.println(i.toString());
        }

        float[][] sourceArray = new float[1][19];
        sourceArray[0] = new float[]{1f, 0f, 0f, 8f, 5f, 2f, 0f, 4.5f, 2f, 0f, 6f, 4.5f, 0f, 0f, 4f, 1f, 0f, 0f, 9f};
        OnnxTensor tensorFromArray = OnnxTensor.createTensor(env,sourceArray);
        Map<String,OnnxTensor> runIn = Collections.singletonMap("X", tensorFromArray);
        try(Result result = session.run(runIn)) {
          OnnxTensor output = (OnnxTensor) result.get(0);
          long[] pred = (long[])output.getValue();
          System.out.println(pred[0]);
        }
      }
    } catch (OrtException e) {
      e.printStackTrace();
    }
  }
}
