package bochum.mpi.horstify;

import picocli.CommandLine;
import wien.secpriv.horst.execution.ExecutionResultHandler;

import java.util.Collections;
import java.util.List;

public class JsonOutDirMixinSouffle {
    @CommandLine.Option(names = {"-j","--json-out-dir"}, description = "Enables JSON Output and writes it to given directory.")
    private String jsonOutDir;

    public List<ExecutionResultHandler> getExecutionResultHandler(String fileName) {
        if (jsonOutDir == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new SouffleExecutionResultHandler.SouffleJsonOutputExecutionResultHandler(jsonOutDir + "/" + fileName + ".json", fileName));
    }
}
