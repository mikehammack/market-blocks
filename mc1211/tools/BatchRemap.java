import java.nio.file.*;
import java.util.*;
import net.fabricmc.tinyremapper.*;

/** Batch-remap many jars in one JVM into a single output jar.
 *  args: <mappings> <from> <to> <classpath> <out> <in1> [<in2> ...] */
public class BatchRemap {
    public static void main(String[] args) throws Exception {
        String mappings = args[0], from = args[1], to = args[2], classpath = args[3];
        Path out = Paths.get(args[4]);
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(Paths.get(mappings), from, to))
                .build();
        try {
            if (!classpath.isEmpty()) remapper.readClassPath(Paths.get(classpath));
            List<Path> inputs = new ArrayList<>();
            for (int i = 5; i < args.length; i++) inputs.add(Paths.get(args[i]));
            InputTag tag = remapper.createInputTag();
            remapper.readInputs(tag, inputs.toArray(new Path[0]));
            OutputConsumerPath ocp = new OutputConsumerPath.Builder(out).assumeArchive(true).build();
            remapper.apply(ocp, tag);
            ocp.close();
            System.out.println("wrote " + out + " from " + inputs.size() + " inputs");
        } finally {
            remapper.finish();
        }
    }
}
