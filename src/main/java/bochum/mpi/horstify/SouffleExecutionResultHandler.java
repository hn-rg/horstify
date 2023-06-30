package bochum.mpi.horstify;

import com.google.gson.Gson;
import com.microsoft.z3.Status;
import org.apache.commons.lang3.NotImplementedException;
import wien.secpriv.horst.data.Proposition;
import wien.secpriv.horst.execution.ExecutionResult;
import wien.secpriv.horst.execution.ExecutionResultHandler;
import wien.secpriv.horst.translation.visitors.ToStringRepresentationExpressionVisitor;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public abstract class SouffleExecutionResultHandler extends ExecutionResultHandler {

    public static class SouffleJsonOutputExecutionResultHandler extends SouffleExecutionResultHandler {
        private final String outputFileName;
        private final String contract;
        private final HorstifyPatterns patterns = new HorstifyPatterns();

        public SouffleJsonOutputExecutionResultHandler(String outputFileName, String contract) {
            this.outputFileName = outputFileName;
            this.contract = contract;
        }

        @Override
        public void handle(List<ExecutionResult> results) {

            SouffleExecutionResultHandler.TestAndQuerySeparatingVisitor separator = new SouffleExecutionResultHandler.TestAndQuerySeparatingVisitor();
            results.forEach(r -> r.accept(separator));
            for (ExecutionResult.QueryResult result : separator.queryResults) {
                HorstifyPatterns.Pattern key = HorstifyPatterns.Pattern.findByString(result.query.predicate.name);

                if (result.status != Status.UNKNOWN)
                    key.update(result);
            }

            List<HorstifyPatterns.PatternMatch> matches = patterns.match();


            try (PrintWriter writer = new PrintWriter(outputFileName)) {
                Gson gson = new Gson();

                gson.toJson(new JsonExecutionResultAdapter(matches, contract, SouffleResultHandler.souffle_time), writer);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class SouffleConsoleOutputExecutionResultHandler extends SouffleExecutionResultHandler {
        private final String contractName;

        public SouffleConsoleOutputExecutionResultHandler(String contractName) {
            this.contractName = contractName;
        }

        @Override
        public void handle(List<ExecutionResult> results) {
            System.out.println("Results for " + contractName + ":");

            TestAndQuerySeparatingVisitor separator = new TestAndQuerySeparatingVisitor();
            results.forEach(r -> r.accept(separator));
            if (!separator.queryResults.isEmpty()) {
                System.out.println("Queries");
                for (ExecutionResult.QueryResult result : separator.queryResults) {
                    printQueryResult(result);
                }
            }
            if (!separator.testResults.isEmpty()) {
                throw new UnsupportedOperationException();
            }

        }

        private void printQueryResult(ExecutionResult result) {
            System.out.println("query id:       " + result.query.predicate.name);
            System.out.println("query check     " + SouffleExecutionResultHandler.queryToString(result.query));
            System.out.println("result:         " + result.status);
            result.info.ifPresent(s -> System.out.println("info:           " + s));
        }
    }

    public static class SouffleConsoleOutputDebugExecutionResultHandler extends SouffleExecutionResultHandler {
        private final String contractName;

        public SouffleConsoleOutputDebugExecutionResultHandler(String contractName) {
            this.contractName = contractName;
        }

        @Override
        public void handle(List<ExecutionResult> results) {
            System.out.println("Results for " + contractName + ":");

            TestAndQuerySeparatingVisitor separator = new TestAndQuerySeparatingVisitor();
            results.forEach(r -> r.accept(separator));
            if (!separator.queryResults.isEmpty()) {
                System.out.println("Queries");
                for (ExecutionResult.QueryResult result : separator.queryResults) {
                    printQueryResult(result);
                }
            }
            if (!separator.testResults.isEmpty()) {
                System.out.println("Tests:");
                for (ExecutionResult.TestResult result : separator.testResults) {
                    printTestResult(result);
                }
            }

        }

        private void printQueryResult(ExecutionResult result) {
            System.out.println("query id:       " + result.query.predicate.name);
            System.out.println("query check     " + SouffleExecutionResultHandler.queryToString(result.query));
            System.out.println("result:         " + result.status);
            result.info.ifPresent(s -> System.out.println("info:           " + s));
        }

        private void printTestResult(ExecutionResult.TestResult result) {
            System.out.println("test id:       " + result.query.predicate.name);
            System.out.println("query check     " + SouffleExecutionResultHandler.queryToString(result.query));
            System.out.println("result:         " + result.status);
            System.out.println("success:         " + result.success);
            result.info.ifPresent(s -> System.out.println("info:           " + s));
        }
    }

    public static class SouffleConsolePatternExecutionResultHandler extends SouffleExecutionResultHandler {
        private final String contractName;
        private final boolean extended;
        private final HorstifyPatterns patterns = new HorstifyPatterns();


        public SouffleConsolePatternExecutionResultHandler(String contractName, boolean extended) {
            this.contractName = contractName;
            this.extended = extended;
        }

        @Override
        public void handle(List<ExecutionResult> results) {

            TestAndQuerySeparatingVisitor separator = new TestAndQuerySeparatingVisitor();
            results.forEach(r -> r.accept(separator));
            if (!separator.queryResults.isEmpty()) {
                for (ExecutionResult.QueryResult result : separator.queryResults) {
                    HorstifyPatterns.Pattern key = HorstifyPatterns.Pattern.findByString(result.query.predicate.name);

                    if (result.status != Status.UNKNOWN)
                        key.update(result);
                }
            }
            /*
            if (!separator.testResults.isEmpty()) {
                throw new UnsupportedOperationException();
            }
             */

            List<HorstifyPatterns.PatternMatch> matches = patterns.match();
            matches.forEach(extended ? HorstifyPatterns.PatternMatch::printExtended : HorstifyPatterns.PatternMatch::print);
        }
    }

    public static class SouffleJsonPatternExecutionResultHandler extends SouffleExecutionResultHandler {
        private final String outputFileName;

        public SouffleJsonPatternExecutionResultHandler(String outputFileName) {
            this.outputFileName = outputFileName;
        }

        @Override
        public void handle(List<ExecutionResult> results) {
            throw new NotImplementedException("SouffleJsonPatternExecutionResultHandler");
        }
    }

    private static class TestAndQuerySeparatingVisitor implements ExecutionResult.Visitor<Void> {
        public final List<ExecutionResult.QueryResult> queryResults = new ArrayList<>();
        public final List<ExecutionResult.TestResult> testResults = new ArrayList<>();

        @Override
        public Void accept(ExecutionResult.QueryResult queryResult) {
            queryResults.add(queryResult);
            return null;
        }

        @Override
        public Void accept(ExecutionResult.TestResult testResult) {
            testResults.add(testResult);
            return null;
        }
    }



    private static String queryToString(Proposition.PredicateProposition query) {
        StringJoiner parameterJoiner = new StringJoiner(", ", "{", "}");
        parameterJoiner.setEmptyValue("");
        query.parameters.stream().map(e -> e.accept(new ToStringRepresentationExpressionVisitor())).forEach(parameterJoiner::add);
        return query.predicate.name + parameterJoiner;
    }
}
