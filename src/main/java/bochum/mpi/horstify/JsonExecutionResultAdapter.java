package bochum.mpi.horstify;

import java.util.List;

public class JsonExecutionResultAdapter {

    public final String contract;
    public final List<HorstifyPatterns.PatternMatch> results;
    public final long souffle_time;

    public JsonExecutionResultAdapter(List<HorstifyPatterns.PatternMatch> matches, String contract, long souffle_time) {
        this.contract = contract;
        this.results = matches;
        this.souffle_time = souffle_time;
    }
}
