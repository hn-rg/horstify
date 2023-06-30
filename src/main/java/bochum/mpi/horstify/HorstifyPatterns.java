package bochum.mpi.horstify;

import com.microsoft.z3.Status;
import wien.secpriv.horst.execution.ExecutionResult;
import wien.secpriv.horst.translation.visitors.ToStringRepresentationExpressionVisitor;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;
public class HorstifyPatterns {
    private static final HashMap<Pattern, List<List<BigInteger>>> querySat = new HashMap<>();
    private static final HashMap<Pattern, List<List<BigInteger>>> queryUnSat = new HashMap<>();
    public static Map<BigInteger, BigInteger> codeToOffset;

    public enum Pattern {
        RestrictedWriteViolation(0) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, false, Pattern.matchTwoUnSat(querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

        },
        TimestampDependSafetyCall(1) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, true, Pattern.matchNUnSat(8, querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

        },
        TodSafety(2) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, true, Pattern.matchTwoUnSat(querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }
        },
        ReentrancyViolation(3) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                Set<BigInteger> pcs = queryUnSat.stream().flatMap(Collection::stream).map(codeToOffset::get).collect(Collectors.toSet());

                return new PatternMatch(this, false, pcs);
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(parameterToBigInteger(result));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(parameterToBigInteger(result));
            }
        },
        ValidatedArgViolation(4) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, false, matchOneNoneMatchingPerPC(querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(parameterToBigInteger(result));

            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(parameterToBigInteger(result));
            }

        },
        HandledExceptionViolation(5) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, false, matchOneNoneMatchingPerPC(querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(parameterToBigInteger(result));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(parameterToBigInteger(result));
            }
        },
        TimestampDependSafetyStaticCall(6) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, true, Pattern.matchNUnSat(7, querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

        },
        TimestampDependSafetyCreate(7) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, true, Pattern.matchNUnSat(4, querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

        },
        TimestampDependSafetyCreate2(8) {
            @Override
            public PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
                return new PatternMatch(this, true, Pattern.matchNUnSat(5, querySat, queryUnSat));
            }

            @Override
            public void updateSat(ExecutionResult.QueryResult result) {
                querySat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

            @Override
            public void updateUnSat(ExecutionResult.QueryResult result) {
                queryUnSat.get(this).add(Collections.singletonList(parameterToBigInteger(result).get(0)));
            }

        };

        public final int patternId;

        Pattern(int patternId) {
            this.patternId = patternId;
        }

        public static Pattern findByString(String query) {
            return switch(query) {
                case "restrictedWriteViolationV", "restrictedWriteViolationI"             -> Pattern.RestrictedWriteViolation;
                case "validatedArgF", "validatedArgV"                   -> Pattern.ValidatedArgViolation;
                case "handledExceptionF", "handledExceptionV"           -> Pattern.HandledExceptionViolation;
                case "todSafetyA", "todSafetyB"                         -> Pattern.TodSafety;
                case "timestampDependSafetyV", "timestampDependSafetyI","timestampDependSafetyVCall", "timestampDependSafetyICall" -> Pattern.TimestampDependSafetyCall;
                case "timestampDependSafetyVStaticCall", "timestampDependSafetyIStaticCall" -> Pattern.TimestampDependSafetyStaticCall;
                case "timestampDependSafetyVCreate", "timestampDependSafetyICreate" -> Pattern.TimestampDependSafetyCreate;
                case "timestampDependSafetyVCreate2", "timestampDependSafetyICreate2" -> Pattern.TimestampDependSafetyCreate2;
                case "reentrancyViolation"                              -> Pattern.ReentrancyViolation;
                default -> throw new UnsupportedOperationException(query);
            };
        }

        public static Iterable<Pattern> activePatterns() {
            LinkedList<Pattern> patterns = new LinkedList<>();
            patterns.add(Pattern.RestrictedWriteViolation);
            patterns.add(Pattern.ReentrancyViolation);
            patterns.add(Pattern.ValidatedArgViolation);
            patterns.add(Pattern.HandledExceptionViolation);
            patterns.add(Pattern.TimestampDependSafetyCall);
            patterns.add(Pattern.TimestampDependSafetyStaticCall);
            patterns.add(Pattern.TimestampDependSafetyCreate);
            patterns.add(Pattern.TimestampDependSafetyCreate2);
            patterns.add(Pattern.TodSafety);

            return patterns;
        }

        public abstract PatternMatch match(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat);
        public abstract void updateSat(ExecutionResult.QueryResult result);
        public abstract void updateUnSat(ExecutionResult.QueryResult result);

        private static Set<BigInteger> matchTwoUnSat(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
            List<BigInteger> pcList = queryUnSat.stream().flatMap(Collection::stream).collect(Collectors.toList());
            return pcList.stream()
                    .filter(i -> Collections.frequency(pcList, i) > 1)
                    .map(codeToOffset::get)
                    .collect(Collectors.toSet());
        }

        private static Set<BigInteger> matchNUnSat(int n, List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
            List<BigInteger> pcList = queryUnSat.stream().flatMap(Collection::stream).collect(Collectors.toList());
            return pcList.stream()
                    .filter(i -> Collections.frequency(pcList, i) >= n)
                    .map(codeToOffset::get)
                    .collect(Collectors.toSet());
        }

        private static Set<BigInteger> matchOneNoneMatchingPerPC(List<List<BigInteger>> querySat, List<List<BigInteger>> queryUnSat) {
            return queryUnSat.stream().map(i -> i.get(0)).filter(i -> {
                List<BigInteger> pcsForPCi = querySat.stream()
                        .filter(j -> j.get(0).equals(i))
                        .flatMap(list -> list.subList(1, min(2, list.size())).stream())
                        .collect(Collectors.toList());
                return pcsForPCi.size() != pcsForPCi.stream().distinct().count();
            })
                    .map(codeToOffset::get)
                    .collect(Collectors.toSet());
        }

        public void update(ExecutionResult.QueryResult result) {
            if (result.status == Status.SATISFIABLE)
                updateSat(result);
            else
                updateUnSat(result);
        }

        private static List<BigInteger> parameterToBigInteger(ExecutionResult.QueryResult result) {
            return result.query.parameters
                    .stream()
                    .map(e -> e.accept(new ToStringRepresentationExpressionVisitor()))
                    .map(BigInteger::new)
                    .collect(Collectors.toList());
        }
    }

    public HorstifyPatterns() {
        for (Pattern pattern : Pattern.values()) {
            querySat.put(pattern, new LinkedList<>());
            queryUnSat.put(pattern, new LinkedList<>());
        }
    }


    public List<PatternMatch> match() {
        List<PatternMatch> matches = new LinkedList<>();

        for (Pattern pattern : Pattern.activePatterns())
            matches.add(pattern.match(querySat.get(pattern), queryUnSat.get(pattern)));

        return matches;
    }


    protected static class PatternMatch {
        public final Pattern pattern;
        public final Set<BigInteger> matches;
        public final PatternType type;
        public final int count;

        private enum PatternType {
            Violation,
            Safety
        }

        public PatternMatch(Pattern pattern, boolean safety, Set<BigInteger> matches) {
            this.pattern = pattern;
            this.matches = matches;
            this.type = safety ? PatternType.Safety : PatternType.Violation;
            this.count = matches.size();
        }

        public void printExtended()  {
            String ident = type == PatternType.Safety ? "Safety Pattern    " : "Violation Pattern ";
            if (count == 0) {
                System.out.println(ident  + pattern + " was not matched! ");
                System.out.println();
                return;
            }

            System.out.println(ident + pattern + " was matched: ");
            for (BigInteger pc : matches) {
                System.out.println(pc.toString(16));
            }
            System.out.println();
        }

        public void print()  {
            System.out.println(pattern);
            System.out.println(matches.size());
        }
    }

}
