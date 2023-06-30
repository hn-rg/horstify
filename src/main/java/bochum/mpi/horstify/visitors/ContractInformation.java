package bochum.mpi.horstify.visitors;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class ContractInformation {

    private final Map<Integer, OpcodeInstance> positionToOpcode = new LinkedHashMap<>();
    private final Map<OpCode, List<OpcodeInstance>> opcodeToPosition = new LinkedHashMap<>();
    private static Map<BigInteger, BigInteger> codeToOffset;
    private Map<BigInteger, BigInteger> constantVariables = new HashMap<>();
    private Set<BigInteger> dynamicVariables = new HashSet<>();

    public ContractInformation(Map<BigInteger, BigInteger> codeToOffset) {
        ContractInformation.codeToOffset = codeToOffset;
    }
    public Integer getSize() {
        return positionToOpcode.size();
    }

    public Map<Integer, OpcodeInstance> getPositionToOpcode() {
        return this.positionToOpcode;
    }

    public List<OpcodeInstance> getInstancesForOpcode(OpCode opcode){
        return opcodeToPosition.getOrDefault(opcode, new ArrayList<>());
    }

    public void addOpcodeInstance(OpcodeInstance opcodeInstance) {
        positionToOpcode.put(opcodeInstance.position, opcodeInstance);
        opcodeToPosition.computeIfAbsent(opcodeInstance.opcode, s -> new ArrayList<>()).add(opcodeInstance);
    }

    public void addVariables(Map<BigInteger, BigInteger> constantVariables, Set<BigInteger> dynamicVariables) {
        this.constantVariables = constantVariables;
        this.dynamicVariables = dynamicVariables;
    }

    public Map<BigInteger, BigInteger> getConstantVariables() {
        return constantVariables;
    }

    public Set<BigInteger> getDynamicVariables() {
        return dynamicVariables;
    }

    public Iterable<BigInteger> getAllProgramCounters() {
        return positionToOpcode.keySet().stream().map(BigInteger::valueOf).collect(Collectors.toList());
    }

    public static Optional<OpCode> getEqOpcode(int i) {
        OpCode opCode;
        if (i == 0)
            opCode = OpCode.STOP;
        else if (i <= 0x1D)
            opCode = OpCode.ARITH;
        else if (i == 0x20)
            opCode = OpCode.SHA3;
        else if (i >= 0xA0 && i <= 0xA4)
            opCode = OpCode.LOG;
        else if (i >= 0x60 && i <= 0x9F)
            opCode = OpCode.ASSIGN;
        else return ContractInformation.OpCode.findByInt(i);

        return Optional.of(opCode);
    }

    public enum OpCode {
        STOP(0x0),
        ADD(0x1),
        AND(0x16),
        LE(0x10),

        ARITH(0x1E),

        SHA3(0x20),

        ADDRESS(0x30),
        BALANCE(0x31),
        ORIGIN(0x32),
        CALLER(0x33),
        CALLVALUE(0x34),
        CALLDATALOAD(0x35),
        CALLDATASIZE(0x36),
        CALLDATACOPY(0x37),
        CODESIZE(0x38),
        CODECOPY(0x39),
        GASPRIZE(0x3A),
        EXTCODESIZE(0x3B),
        EXTCODECOPY(0x3C),
        RETURNDATASIZE(0x3D),
        RETURNDATACOPY(0x3E),
        EXTCODEHASH(0x3F),
        BLOCKHASH(0x40),
        COINBASE(0x41),
        TIMESTAMP(0x42),
        NUMBER(0x43),
        DIFFICULTY(0x44),
        GASLIMIT(0x45),
        CHAINID(0x46),
        SELFBALANCE(0x47),
        BASEFEE(0x48),


        MLOAD(0x51),
        MSTORE(0x52),
        MSTORE8(0x53),
        SSTORE(0x55),
        SLOAD(0x54),
        JUMP(0x56),
        JUMPI(0x57),
        PC(0x58),
        MSIZE(0x59),
        GAS(0x5A),
        JUMPDEST(0x5B),
        TMP_LABEL(0x5C),
        TMP_JUMP(0x5D),
        TMP_ASSIGN(0x5E),
        TMP_JUMPI(0x5F),

        ASSIGN(0x60),
        LOG(0xA0),

        CREATE(0xF0),
        CALL(0xF1),
        CALLCODE(0xF2),
        RETURN(0xF3),
        DELEGATECALL(0xF4),
        CREATE2(0xF5),

        STATICCALL(0xFA),
        REVERT(0xFD),
        THROW(0xFE),
        SELFDESTRUCT(0xFF),

        TEST(17);

        private static final Map<Integer, OpCode> intToOpcode;

        static {
            intToOpcode = new HashMap<>();
            for (OpCode v : OpCode.values()) {
                intToOpcode.put(v.opcode, v);
            }
        }

        public final int opcode;

        OpCode(int opcode) {
            this.opcode = opcode;
        }

        public static Optional<OpCode> findByInt(int i) {
            OpCode ret = intToOpcode.get(i);
            if (ret == null) {
                return Optional.of(TEST);
            }
            return Optional.of(ret);
        }
    }

    public static class OpcodeInstance {
        public final int position;      // Position
        public final OpCode opcode;     // Opcode
        public final List<BigInteger> stackInput;
        public final Optional<BigInteger> stackOutput;
        public final Optional<BigInteger> next;
        public final Optional<BigInteger> jump;
        public final List<Optional<BigInteger>> constantInformation;
        public final List<BigInteger> controlDependencies;

        public OpcodeInstance(VisitorState visitorState) {
            this.position = visitorState.getPosition();
            this.opcode = visitorState.getOpcode(); // Objects.requireNonNull(opcode, "Opcode may not be null!");
            this.stackInput = visitorState.getStackInput();
            this.stackOutput = visitorState.getStackOutput();
            this.next = visitorState.getNext();
            this.jump = visitorState.getJump();
            this.constantInformation = visitorState.getConstantInformation();
            this.controlDependencies = visitorState.getControlDependencies();
        }

        @Override
        public boolean equals (Object o){
            if (o == null) {
                return false;
            }
            if (!(OpcodeInstance.class.isAssignableFrom(o.getClass()))){
                return false;
            }

            final OpcodeInstance other = (OpcodeInstance) o;
            return this.opcode.equals(other.opcode)
                    && this.position == other.position
                    && this.stackInput.equals(((OpcodeInstance) o).stackInput)
                    && this.stackOutput.equals(((OpcodeInstance) o).stackOutput)
                    && this.next.equals(((OpcodeInstance) o).next)
                    && this.jump.equals(((OpcodeInstance) o).jump)
                    && this.constantInformation.equals(((OpcodeInstance) o).constantInformation)
                    && this.controlDependencies.equals(((OpcodeInstance) o).controlDependencies);
        }

        @Override
        public String toString() {

            String cf = "[" + (next.map(integer -> integer.toString(16)).orElse("")) + (jump.map(bigInteger -> ", " + bigInteger.toString(16)).orElse("")) + "]";

            String inList = toHexListString(stackInput);
            String ciList = toHexListString(constantInformation.stream().map(i-> i.orElse(BigInteger.valueOf(-1))).collect(Collectors.toList()));
            String cdList = toHexListString(controlDependencies);

            return "/* " + codeToOffset.getOrDefault(BigInteger.valueOf(position), BigInteger.valueOf(-1)).toString().replaceAll("-1", ". ")
                    + " / " + Integer.toHexString(position) + ":\t */"
                    + opcode.toString()
                        + computeTabs(16, opcode.toString().length())
                    + optionalStringConverter(stackOutput)
                    + inList
                        + computeTabs(16, inList.length())
                    + cf
                        + computeTabs(16, cf.length())
                    + ciList.replaceAll("[,]","")
                        + computeTabs(16, ciList.replaceAll("[,]","").length())
                    + cdList.replaceAll("[,]","")
                    + ";";
        }

        private String optionalStringConverter(Optional<BigInteger> optional) {
                int maxLength = 12;
                if (optional.isEmpty())
                    return "[]" + computeTabs(maxLength, 2);
                else
                    return "["+ optional.get().toString(16) + "]" + computeTabs(maxLength, optional.get().toString(16).length()+2);
        }
        private String  computeTabs(int maxLength, int length) {
            int tabs = (maxLength - length-1) / 4;
            StringBuilder accu = new StringBuilder();
            for (int i = 0; i < tabs ; i++) {
                accu.append("\t");
            }
            return accu.toString();
        }

        private String toHexListString(List<BigInteger> list) {
            return list.stream().map(i -> i.toString(16)).collect(Collectors.toList()).toString();
        }
    }



}
