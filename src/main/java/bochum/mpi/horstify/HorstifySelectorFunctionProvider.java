package bochum.mpi.horstify;

import bochum.mpi.horstify.visitors.ContractInformation;
import wien.secpriv.horst.data.tuples.Tuple2;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class HorstifySelectorFunctionProvider {

    private final ContractInformation contractInfo;

    public HorstifySelectorFunctionProvider(ContractInformation contractInfo){
        this.contractInfo = contractInfo;
    }

    // Implementation of the selector functions as defined in the interface
    // sel pcs: unit -> [int]; // gives all pcs of the contract
    public Iterable<BigInteger> pcs (){
        return contractInfo.getAllProgramCounters();
    }

    // sel pcsForOpcode: int -> [int]; // gives all pcs for a certain opcode
    public Iterable<BigInteger> pcsForOpcode(BigInteger oc){

        Optional<ContractInformation.OpCode> opcodeOpt = ContractInformation.OpCode.findByInt(oc.intValue());

        if (opcodeOpt.isEmpty()) {
            throw new RuntimeException("An opcode that does not exist was accessed! (probably to high, check for NUMOPCODES)");
        }

        /// create an integer array and plug all positions inside
        ArrayList<BigInteger> ret = new ArrayList<>();
        for (ContractInformation.OpcodeInstance ocinst: contractInfo.getInstancesForOpcode(opcodeOpt.get())) {
            ret.add(BigInteger.valueOf(ocinst.position));
        }
        return ret;
    }

    // sel nextForPc: int -> [int]; // gives the next (temporary) pc(s) after the given one
    public Iterable<BigInteger> nextForPc(BigInteger pc){
        ContractInformation.OpcodeInstance currentOpcode = checkAndGetOpcode(pc);

        /// create an integer array and plug the opcode code inside
        ArrayList<BigInteger> ret = new ArrayList<>();
        currentOpcode.next.ifPresent(ret::add);
        currentOpcode.jump.ifPresent(ret::add);

        if (ret.isEmpty())
            ret.add(BigInteger.valueOf(-1));

        return ret;
    }

    // sel getStackInputs: int -> [int];   // returns stack input variables for given pc
    public Iterable<BigInteger> getStackInputs(BigInteger pc, BigInteger pos){
        ContractInformation.OpcodeInstance currentOpcode = checkAndGetOpcode(pc);

        ArrayList<BigInteger> ret = new ArrayList<>();

        if (currentOpcode.stackInput.size() <= pos.intValue())
            // throw new RuntimeException("Stack Input does not exists for this pc");
            return ret;

        ret.add(currentOpcode.stackInput.get(pos.intValue()));
        return ret;
    }

    // sel getStackOutputs: int -> [int];  // returns stack output variables for given pc
    public Iterable<BigInteger> getStackOutput(BigInteger pc){
        ContractInformation.OpcodeInstance currentOpcode = checkAndGetOpcode(pc);

        ArrayList<BigInteger> ret = new ArrayList<>();
        currentOpcode.stackOutput.ifPresent(ret::add);

        return ret;
    }

    // sel isControlledBy: int -> [int]; // pc is control dependent on pc'
    public Iterable<BigInteger> isControlledBy(BigInteger pc){
        ContractInformation.OpcodeInstance currentOpcode = checkAndGetOpcode(pc);

        return new ArrayList<>(currentOpcode.controlDependencies);
    }

    // constAccess called for M(8)/S Store/Load
    // sel notConstAccess: int -> [int];
    public Iterable<BigInteger> notConstAccess(BigInteger pc){
        ContractInformation.OpcodeInstance currentOpcode = checkAndGetOpcode(pc);
        ArrayList<BigInteger> ret = new ArrayList<>();

        if (!currentOpcode.constantInformation.isEmpty())
            return ret;

        ret.add(pc);
        return ret;
    }

    // sel isVarConst: int -> [int];
    public Iterable<Tuple2<BigInteger,BigInteger>> isVarConst(){
        Map<BigInteger, BigInteger> variableMap = contractInfo.getConstantVariables();

        if (variableMap == null)
            return new ArrayList<>();

        List<Tuple2<BigInteger,BigInteger>> accu = new ArrayList<>(variableMap.size());
        variableMap.keySet().forEach(key -> accu.add(new Tuple2<>(key, variableMap.get(key))));
        return accu;
    }

    // sel notVarConst: int -> [int];
    public Iterable<BigInteger> notVarConst(){
        Set<BigInteger> variableSet = contractInfo.getDynamicVariables();
        return variableSet == null ? new ArrayList<>(): variableSet;
    }

    private ContractInformation.OpcodeInstance checkAndGetOpcode(BigInteger pc) {
        final Map<Integer, ContractInformation.OpcodeInstance> positionToOpcode = contractInfo.getPositionToOpcode();

        if (!positionToOpcode.containsKey(pc.intValue())) {
            throw new RuntimeException("A program counter that does not exist was accessed!");
        }

        return positionToOpcode.get(pc.intValue());
    }

    // sel provideMemInterval: int*int -> [int];
    public Iterable<BigInteger> provideMemInterval(BigInteger pc, BigInteger choice) {
        ContractInformation.OpcodeInstance currentOpcode = checkAndGetOpcode(pc);

        List<Optional<BigInteger>> constantInformation = currentOpcode.constantInformation;
        BigInteger start;
        BigInteger end;
        BigInteger na = BigInteger.valueOf(-1);

        int i = choice.intValue() < 2 ?
                switch (currentOpcode.opcode) {
                    case SHA3 -> 0;
                    case CREATE,CREATE2 -> 1;
                    case STATICCALL -> 2;
                    case CALL -> 3;
                    default -> throw new RuntimeException("Selector function provideMemInterval called with invalid opcode " + currentOpcode.opcode +  "!");
                } :
                switch (currentOpcode.opcode) {
                    case STATICCALL -> 4;
                    case CALL -> 5;
                    default -> throw new RuntimeException("Selector function provideMemInterval (output) called with invalid opcode " + currentOpcode.opcode +  "!");
                };

        start = constantInformation.get(i).orElse(na);
        end = start.add(constantInformation.get(i+1).orElse(na));

        if (start.equals(na) || end.equals(na))
            return new ArrayList<>();

        return () -> new Iterator<BigInteger>() {
            BigInteger state = start;

            @Override
            public boolean hasNext() {
                return state.compareTo(end) < 0;
            }

            @Override
            public BigInteger next() {
                BigInteger ret = state;
                state = state.add(BigInteger.ONE);
                return ret;
            }
        };
    }


    // sel interval: int -> [int];
    public Iterable<BigInteger> interval(BigInteger start, BigInteger end) {
        return new Iterable<BigInteger>() {
            @Override
            public Iterator<BigInteger> iterator() {
                return new Iterator<BigInteger>() {
                    BigInteger state = start;

                    @Override
                    public boolean hasNext() {
                        return state.compareTo(end) < 0;
                    }

                    @Override
                    public BigInteger next() {
                        BigInteger ret = state;
                        state = state.add(BigInteger.ONE);
                        return ret;
                    }
                };
            }
        };
    }

    public Iterable<Tuple2<BigInteger,BigInteger>> mayFollows() {
        Set<BigInteger> pcSet = contractInfo.getPositionToOpcode().keySet().stream().map(BigInteger::valueOf).collect(Collectors.toSet());
        int size = pcSet.size();
        BigInteger[] pcs = pcSet.toArray(new BigInteger[size]);

        Set<Tuple2<BigInteger,BigInteger>> closure = new HashSet<>();
        for (BigInteger pc : pcs) {
            nextForPc(pc).forEach(next -> closure.add(new Tuple2<>(pc, next)));
        }

        for (BigInteger pc1 : pcs)
            for (BigInteger pc2: pcs) {
                if (!closure.contains(new Tuple2<>(pc2, pc1)))
                    continue;
                for (BigInteger pc3 : pcs) {
                    if (closure.contains(new Tuple2<>(pc1, pc3)))
                        closure.add(new Tuple2<>(pc2, pc3));
                }
            }

        return closure;
    }

}
