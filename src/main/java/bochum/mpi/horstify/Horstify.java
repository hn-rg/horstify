package bochum.mpi.horstify;

import ch.securify.Securify;
import ch.securify.decompiler.instructions.Instruction;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.status.StatusLogger;
import picocli.CommandLine;
import bochum.mpi.horstify.adapter.ContractHelper;
import bochum.mpi.horstify.visitors.ContractInformation;
import wien.secpriv.horst.cli.HorstSpecificationMixin;
import wien.secpriv.horst.cli.VerbosityMixin;
import wien.secpriv.horst.data.Expression;
import wien.secpriv.horst.data.Proposition;
import wien.secpriv.horst.data.Rule;
import wien.secpriv.horst.data.SelectorFunction;
import wien.secpriv.horst.execution.ExecutionResultHandler;
import wien.secpriv.horst.execution.FactGeneratingSouffleWithCompiledProgramQueryExecutor;
import wien.secpriv.horst.execution.SouffleQueryExecutor;
import wien.secpriv.horst.internals.SelectorFunctionHelper;
import wien.secpriv.horst.tools.DatalogFactPrinter;
import wien.secpriv.horst.translation.StandardSouffleTranslationPipeline;
import wien.secpriv.horst.translation.TranslationPipeline;
import wien.secpriv.horst.translation.external.SouffleTheory;
import wien.secpriv.horst.translation.external.StandardSouffleTheory;
import wien.secpriv.horst.visitors.VisitorState;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "horstify", mixinStandardHelpOptions = true, version = "Horstify version 0.1 WIP")
public class Horstify implements Callable<Integer> {

    @CommandLine.Mixin
    private VerbosityMixin verbosity;

    @CommandLine.Mixin
    private HorstSpecificationMixin horstSpecifications;

    enum HorstifyModes {
        facts, prog, exec, dl, debug
    }

    @CommandLine.Mixin
    private HorstifySettings horstifySettings;

    @CommandLine.Mixin
    private JsonOutDirMixinSouffle jsonOutDir;

    @CommandLine.Mixin
    private ConsoleOutputQueryResultsMixinSouffle consoleOutput;

    @CommandLine.Option(names = {"--exclude-queries"}, description = "Do not execute these queries", arity = "0..*", hidden = true)
    private String[] excludedQueries = new String[0];

    @CommandLine.Parameters(index = "2", description = "Contracts to be analyzed.")
    private File[] contractFiles = new File[0];

    @CommandLine.Option(names = {"--profiler"}, description = "Enables souffle profiler option in souffle executable with given profile location.", defaultValue = "", hidden = true)
    private String profilerArg = "";

    private static final Logger logger = LogManager.getLogger(Horstify.class);

    // TODO Check logger
    static {
        StatusLogger.getLogger().setLevel(Level.OFF);
    }

    private final SouffleTheory theory = StandardSouffleTheory.instance;

    public static void main(String[] args) {
        int exitCode = new CommandLine((new Horstify())).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        configureLogger();
        configureArguments(); // this sets the default semantics (+ queries)
        logger.info("Recognized {} contract to be analyzed", contractFiles.length);

        if (horstifySettings.isMode(HorstifyModes.prog) || horstifySettings.isMode(HorstifyModes.dl)) {
            throw new IllegalArgumentException("prog and dl generation is not supported by this preview of horstify");
        }

        if (horstifySettings.isMode(HorstifyModes.exec) || horstifySettings.isMode(HorstifyModes.facts) || horstifySettings.isMode(HorstifyModes.debug))
            for (File contractFile : contractFiles) processContract(contractFile);
        return 0;
    }

    private void processContract(File contractFile) {
        System.out.println("----- Selecting " + contractFile.getName() + " ------");
        logger.info("Compiling {}.", contractFile.getName());

        ContractInformation contractInfo = decompile(contractFile);

        SelectorFunctionHelper compiler = new SelectorFunctionHelper();
        VisitorState state = configureState(compiler, new VisitorState(), contractInfo);

        Set<Proposition.PredicateProposition> originalQueries = new HashSet<>();

        final long start = System.currentTimeMillis();
        TranslationPipeline pipeline = fillSelectorFunctions(compiler, state, originalQueries);
        final long exec = System.currentTimeMillis() - start;
        System.out.println(exec);

        if (horstifySettings.isMode(HorstifyModes.facts)) {
            logger.info("Fact generation for {}.", contractFile.getName());
            DatalogFactPrinter.writeToDirectory(
                    horstifySettings.getContractSpecificDir(contractFile.getName()),
                    pipeline.getSelectorFunctionCache()
            );
        }

        if (horstifySettings.isMode(HorstifyModes.exec))  {
            logger.info("Executing Horstify analysis for contract {}.", contractFile.getName());
            exec_horstify(contractFile, pipeline, originalQueries);
        }
    }

    TranslationPipeline fillSelectorFunctions(SelectorFunctionHelper compiler, VisitorState state, Set<Proposition.PredicateProposition> originalQueries) {
        TranslationPipeline pipeline = StandardSouffleTranslationPipeline
                .getForFillingSelectorFunctionInvocationCacheAndGeneratingQueries(state, compiler, theory, originalQueries);
        ArrayList<Rule> rules = new ArrayList<>(state.getRules().values());
        pipeline.apply(rules);
        return pipeline;
    }

    void exec_horstify(File contractFile, TranslationPipeline pipeline, Set<Proposition.PredicateProposition> originalQueries) {
        List<ExecutionResultHandler> resultHandlers = initializeSouffleResultHandlers(contractFile.getName().replaceAll("\\.bin\\.hex$", ""));
        if (!resultHandlers.isEmpty()) {
            logger.info("Executing Souffle for contract {}.", contractFile.getName());
            SouffleQueryExecutor executor = getExecutor(pipeline.getSelectorFunctionCache());
            SouffleResultHandler souffleResultHandler = new SouffleResultHandler(originalQueries, excludedQueries, executor, resultHandlers);

            logger.info("Query Souffle results for contract {}.", contractFile.getName());
            souffleResultHandler.queryResults();
        } else {
            logger.info("Do not execute souffle output because results are not requested.");
        }
    }

    private ContractInformation decompile(File contractFile) {
        byte[] bin = Securify.CompilationHelpers.extractBinaryFromFile(contractFile);
        List<Instruction> instructions = Securify.decompileContract(bin);
        ContractHelper helper = new ContractHelper();
        ContractInformation contractInfo = helper.createHorstifyContract(instructions);
        HorstifyPatterns.codeToOffset = helper.getCodeToOffset();

        consoleOutput.printIR(contractInfo, contractFile);

        return contractInfo;
    }

    private SouffleQueryExecutor getExecutor(Map<SelectorFunction, Map<List<Expression>, Iterable<Object>>> selectorFunctionCache) {
        String prepared = String.join(" ",  "-j", Integer.toString(Runtime.getRuntime().availableProcessors())).concat(profilerArg.length() > 0 ? "--profile=" + profilerArg : "");
        return new FactGeneratingSouffleWithCompiledProgramQueryExecutor(selectorFunctionCache, horstifySettings.getDir(), prepared);
    }

    private VisitorState configureState(SelectorFunctionHelper compiler, VisitorState state, ContractInformation contractInfo) {
        state.setSelectorFunctionHelper(compiler);
        HorstifySelectorFunctionProvider provider = new HorstifySelectorFunctionProvider(contractInfo);
        compiler.registerProvider(provider);

        // here the HoRSt Compiler invocation starts
        return horstSpecifications.parse(state);
    }

    private void configureArguments() {
        ArrayList<String> semantics = new ArrayList<>(List.of("resource:///abstract-semantics-patterns-full.txt"));
        horstSpecifications.setDefaultHorstFilesIfNotAlreadySet(Collections.unmodifiableList(semantics));
    }

    private void configureLogger() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Level level = verbosity.getLoggerLevel();
        context.getConfiguration().getRootLogger().setLevel(level);

        Appender consoleAppender = context.getConfiguration().getAppender("Console");
        if (consoleAppender != null)
            context.getConfiguration().getRootLogger().addAppender(consoleAppender, level, null);
    }

    private List<ExecutionResultHandler> initializeSouffleResultHandlers(String contractFile) {
        List<ExecutionResultHandler> resultHandlers = new ArrayList<>();

        resultHandlers.addAll(jsonOutDir.getExecutionResultHandler(contractFile));
        resultHandlers.addAll(consoleOutput.getExecutionResultHandler(contractFile));

        if (resultHandlers.size() > 1) {
            logger.warn("Only a single result handler at a time is supported. Defaulted to JSON Output.");
            return resultHandlers.subList(0,1);
        }

        return resultHandlers;
    }
}
