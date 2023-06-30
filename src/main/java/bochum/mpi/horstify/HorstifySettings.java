package bochum.mpi.horstify;

import picocli.CommandLine;

import java.nio.file.Path;

public class HorstifySettings {

    @CommandLine.Parameters(index = "0",  arity = "1", split = ",", description = "Valid values: ${COMPLETION-CANDIDATES}")
    private Horstify.HorstifyModes[] mode = new Horstify.HorstifyModes[0];

    @CommandLine.Parameters(index = "1", description = "Input and Output directory for souffle facts and program.")
    public Path workingDir;

    public Path getContractSpecificDir(String filename) {
        return this.workingDir.resolve(filename);
    }

    public boolean isSet() {
        return workingDir != null;
    }

    public Path getDir() {
        return workingDir;
    }

    public boolean isMode(Horstify.HorstifyModes mode) {
        return this.mode[0] == mode || (this.mode.length > 1 && this.mode[1] == mode);
    }
}
