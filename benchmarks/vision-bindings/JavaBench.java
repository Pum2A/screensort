import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

// Standalone benchmark, deliberately no Spring Boot/JPA startup.
public class JavaBench {
    private static float[] infer(OrtSession session, String name, OnnxTensor tensor) throws Exception {
        try (var result = session.run(Map.of(name, tensor))) {
            // getValue materializes a Java array rather than retaining native output.
            return ((float[][]) result.get(0).getValue())[0];
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        int threads = Integer.parseInt(args[2]);
        int warmup = Integer.parseInt(args[3]);
        int iterations = Integer.parseInt(args[4]);
        try (var env = OrtEnvironment.getEnvironment(); var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            long start = System.nanoTime();
            try (var session = env.createSession(args[0], options)) {
                double loadMs = (System.nanoTime() - start) / 1e6;
                String name = session.getInputNames().iterator().next();
                byte[] raw = Files.readAllBytes(Path.of(args[1]));
                int count = 3 * 224 * 224;
                var buffer = ByteBuffer.allocateDirect(raw.length).order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(raw).flip();
                var floats = buffer.asFloatBuffer();
                var tensors = new ArrayList<OnnxTensor>();
                try {
                    for (int offset = 0; offset < floats.limit(); offset += count) {
                        tensors.add(OnnxTensor.createTensor(env, floats.slice(offset, count), new long[]{1, 3, 224, 224}));
                    }
                    start = System.nanoTime();
                    float[] output = infer(session, name, tensors.getFirst());
                    double firstMs = (System.nanoTime() - start) / 1e6;
                    System.out.printf("{\"event\":\"ready\",\"load_ms\":%.6f,\"first_ms\":%.6f}%n", loadMs, firstMs);
                    for (int i = 0; i < warmup; i++) infer(session, name, tensors.get(i % tensors.size()));
                    var times = new double[iterations];
                    for (int i = 0; i < iterations; i++) {
                        start = System.nanoTime();
                        output = infer(session, name, tensors.get(i % tensors.size()));
                        times[i] = (System.nanoTime() - start) / 1e6;
                    }
                    System.out.println("{\"event\":\"result\",\"engine\":\"" + env.getVersion()
                        + "\",\"times_ms\":" + Arrays.toString(times) + ",\"output\":" + Arrays.toString(output) + "}");
                } finally { for (var tensor : tensors) tensor.close(); }
            }
        }
    }
}
