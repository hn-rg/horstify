package bochum.mpi.horstify;

import wien.secpriv.horst.data.Proposition;
import wien.secpriv.horst.execution.ExecutionResult;
import wien.secpriv.horst.execution.ExecutionResultHandler;
import wien.secpriv.horst.execution.SouffleQueryExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SouffleResultHandler {

    private final Set<Proposition.PredicateProposition> originalQueries;
    private final String[] excludedQueries;
    private final SouffleQueryExecutor executor;
    private final List<ExecutionResultHandler> resultHandlers;
    protected static long souffle_time = -1;

    public SouffleResultHandler(Set<Proposition.PredicateProposition> originalQueries,
                                String[] excludedQueries, SouffleQueryExecutor executor,
                                List<ExecutionResultHandler> resultHandlers) {
        this.originalQueries = originalQueries;
        this.excludedQueries = excludedQueries;
        this.executor = executor;
        this.resultHandlers = resultHandlers;
        SouffleResultHandler.souffle_time = executor.getSouffleTime();
    }

    public void queryResults() {
        List<ExecutionResult> executionResults = originalQueries.stream()
                .filter(q -> {
                    if (excludedQueries == null)
                        return true;
                    else
                        return Arrays.stream(excludedQueries).noneMatch(q.predicate.name::equals);
                })
                .map(executor::executeQuery)
                .collect(Collectors.toList());

        for (ExecutionResultHandler resultHandler : resultHandlers) {
            resultHandler.handle(executionResults);
        }
    }
}
