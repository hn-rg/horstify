package bochum.mpi.horstify;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import bochum.mpi.horstify.visitors.ContractInformation;
import wien.secpriv.horst.execution.ExecutionResultHandler;

import java.io.File;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class ConsoleOutputQueryResultsMixinSouffle {

    @CommandLine.Option(names = {"-c", "--console"}, description = "Enable console output", defaultValue = "false")
    private boolean standardConsoleOut;

    @CommandLine.Option(names = {"-e", "--extended"}, description = "Enable extended console output.", defaultValue = "false")
    private boolean extendedOutput;

    @CommandLine.Option(names= {"-d", "--debug"}, description = "Enable debug console output to print test query results.", defaultValue = "false", hidden = true)
    private boolean debug;

    @CommandLine.Option(names= {"-p", "--print-ir"}, description = "Print intermediate representation of contract.")
    private boolean print;

    private static final Logger logger = LogManager.getLogger(Horstify.class);

    public List<ExecutionResultHandler> getExecutionResultHandler(String contractFile) {
        if (debug)
            return Collections.singletonList(new SouffleExecutionResultHandler.SouffleConsoleOutputDebugExecutionResultHandler(contractFile));
        if (extendedOutput || standardConsoleOut)
            return Collections.singletonList(new SouffleExecutionResultHandler.SouffleConsolePatternExecutionResultHandler(contractFile, extendedOutput));

        return Collections.emptyList();
    }

    public void printIR(ContractInformation contractInfo, File contractFile) {
        if (!print)
            return;
        logger.info("Printing contract {}", contractFile.getName());
        System.out.println();
        System.out.println("Print contract " + contractFile.getName());
        System.out.println("PC: OPCODE \t\t SO \t\t SI \t\t CF \t\t CI \t\tCD");
        for (BigInteger bigInteger : contractInfo.getAllProgramCounters()) {
            Integer integer = bigInteger.intValue();
            System.out.println(contractInfo.getPositionToOpcode().get(integer).toString());
        }
        System.out.println("PC: OPCODE \t\t SO \t\t SI \t\t CF \t\t CI \t\tCD");
        System.out.println();
    }
}
