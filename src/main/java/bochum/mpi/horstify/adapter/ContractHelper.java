package bochum.mpi.horstify.adapter;

import ch.securify.decompiler.Variable;
import ch.securify.decompiler.evm.RawInstruction;
import ch.securify.decompiler.instructions.*;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.lang3.NotImplementedException;
import bochum.mpi.horstify.visitors.ContractInformation;
import bochum.mpi.horstify.visitors.VisitorState;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class ContractHelper {

    private List<Instruction> instructions;

    protected BiMap<Variable, BigInteger> varToCode;
    protected BiMap<Instruction, BigInteger> instrToCode;
    protected BiMap<Class, BigInteger> typeToCode;
    protected BiMap<Integer, BigInteger> constToCode;
    protected Map<BigInteger, BigInteger> codeToOffset = new HashMap<>();

    protected Map<BigInteger, BigInteger> constantVariables = new HashMap<>();
    protected Set<BigInteger> dynamicVariables = new HashSet<>();

    protected BigInteger bvCounter = BigInteger.ZERO;

    public ContractHelper () {
        varToCode = HashBiMap.create();
        instrToCode = HashBiMap.create();
        typeToCode = HashBiMap.create();
        constToCode = HashBiMap.create();
    }

    public ContractInformation createHorstifyContract(List<Instruction> instructions) {
        // Compute BB and Standard Control Dependencies
        List<BasicBlock> bbs = BasicBlock.generateBaseBlocks(instructions);
        bbs.forEach(BasicBlock::connectBB);
        CFG cfg = new CFG(bbs);
        PDG pdg = new PDG(cfg);
        pdg.computeEasy();
        bbs.forEach(BasicBlock::addDependenciesToInstruction);


        // Create visitors for every instruction to collect IR information
        List<VisitorState> visitors = new LinkedList<>();
        instructions.forEach(opcode -> visitors.add(opcodeAdapter(opcode)));

        // Create final contract information
        ContractInformation ci = new ContractInformation(codeToOffset);
        visitors.forEach(visitor -> ci.addOpcodeInstance(new ContractInformation.OpcodeInstance(visitor)));
        ci.addVariables(constantVariables, dynamicVariables);
        return ci;
    }

    private VisitorState opcodeAdapter(Instruction instruction) {
        RawInstruction ri = instruction.getRawInstruction();
        VisitorState vs = new VisitorState(getCode(instruction));

        // Opcode
        if (ri == null) {
            if (instruction instanceof JumpDest) {
                vs.setOpcode(ContractInformation.OpCode.TMP_LABEL);
            } else if (instruction instanceof JumpI) {
                vs.setOpcode(ContractInformation.OpCode.TMP_JUMPI);
            } else if (instruction instanceof _VirtualAssignment || instruction instanceof Push || instruction instanceof Eq) {
                vs.setOpcode(ContractInformation.OpCode.TMP_ASSIGN);
            } else if (instruction instanceof Jump) {
                vs.setOpcode(ContractInformation.OpCode.TMP_JUMP);
            } else if (instruction instanceof Invalid) {
                vs.setOpcode(ContractInformation.OpCode.THROW);
            } else {
                throw new IllegalStateException();
            }
        } else {
            codeToOffset.put(BigInteger.valueOf(vs.getPosition()), BigInteger.valueOf(ri.offset));
            Optional<ContractInformation.OpCode> op = ContractInformation.getEqOpcode(ri.opcode);
            if (op.isPresent() && op.get() != ContractInformation.OpCode.TEST) {
                vs.setOpcode(op.get());
            } else
                throw new NotImplementedException("Opcode " + ri.opcode + " is not available in Horstify implementation");
        }

        // Stack input
        Variable[] input = instruction.getInput();
        List<BigInteger> inputList = new LinkedList<>();
        if (input != null) {
            if (instruction instanceof JumpI && input.length > 1) {
                inputList = Collections.singletonList(getCode(input[1]));
            } else {
                inputList = varToBigInt(input);
            }
            for (Variable var : input) {
                if (var == null)
                    continue;
                if (var.hasConstantValue())
                    constantVariables.put(getCode(var), BigInteger.valueOf(getInt(var.getConstantValue())));
                else
                    dynamicVariables.add(getCode(var));
            }
        }
        vs.setStackInput(inputList);


        // Stack output
        List<BigInteger> outputs = varToBigInt(instruction.getOutput());
        vs.setStackOutput(outputs.isEmpty() ? Optional.empty() : Optional.of(outputs.get(0)));

        // Constant input information (non-call case)
        List<Optional<BigInteger>> constantInfo = new LinkedList<>();
        if (input != null) {
            for (Variable var : input) {
                if (var != null && var.hasConstantValue()) {
                    constantInfo.add(Optional.of(BigInteger.valueOf(getInt(var.getConstantValue()))));
                } else {
                    constantInfo.add(Optional.empty());
                }
            }
        }
        vs.setConstantInformation(constantInfo);

        // Next + Jump
        vs.setNext(instruction.getNext() != null ? Optional.of(extractInstNum(instruction.getNext())) : Optional.empty());
        vs.setJump(Optional.empty());
        if (instruction instanceof JumpI) {
            BranchInstruction bi = (BranchInstruction) instruction;
            // handle next  + jump
            Collection<Instruction> outgoingBranches = bi.getOutgoingBranches();
            LinkedList<Instruction> li = new LinkedList<>(outgoingBranches);
            switch (li.size()) {
                case 0:
                    break;
                case 2:
                    throw new IllegalStateException("ContactHelper::opcodeAdapter: more than one outgoing branch");
                default:
                    vs.setJump(Optional.of(extractInstNum(li.get(0))));
            }
        } else if (instruction instanceof Jump) {
            Jump jump = (Jump) instruction;
            Collection<Instruction> outgoingBranches = jump.getOutgoingBranches();

            if (outgoingBranches.size() > 1)
                throw new IllegalStateException();

            vs.setNext(outgoingBranches.size() != 0 ? Optional.of(extractInstNum(new LinkedList<>(outgoingBranches).get(0))) : Optional.empty());
            vs.setJump(Optional.empty());
        }

        // Control dependence
        vs.setControlDependencies(instruction.getIsControlled().stream()
                .map(this::getCode)
                .collect(Collectors.toList()));

        return vs;
    }

    private List<BigInteger> varToBigInt(Variable[] vars) {
        return Arrays.stream(vars).map(this::getCode).collect(Collectors.toCollection(LinkedList::new));
    }

    // Translates code
    protected BigInteger getFreshCode() {
        if (bvCounter.equals(BigInteger.valueOf(Integer.MAX_VALUE))) {
            throw new RuntimeException("Integer overflow.");
        }
        BigInteger freshCode = bvCounter;
        bvCounter = bvCounter.add(BigInteger.ONE);
        return freshCode;
    }

    protected BigInteger getCode(Variable var) {
        if (!varToCode.containsKey(var))
            varToCode.put(var, getFreshCode());
        return varToCode.get(var);
    }

    protected BigInteger getCode(Instruction instr) {
        if (!instrToCode.containsKey(instr))
            instrToCode.put(instr, getFreshCode());
        return instrToCode.get(instr);
    }

    protected BigInteger getCode(Class instructionClass) {
        if (!typeToCode.containsKey(instructionClass))
            typeToCode.put(instructionClass, getFreshCode());
        return typeToCode.get(instructionClass);
    }

    protected BigInteger getCode(Integer constVal) {
        if (!constToCode.containsKey(constVal))
            constToCode.put(constVal, getFreshCode());
        return constToCode.get(constVal);
    }

    protected BigInteger getCode(Object o) {
        if (o instanceof Instruction) {
            return getCode((Instruction) o);
        } else if (o instanceof Class) {
            return getCode((Class) o);
        } else if (o instanceof Integer) {
            return getCode((Integer) o);
        } else if (o instanceof Variable) {
            return getCode((Variable) o);
        } else {
            throw new RuntimeException("Not supported object of a bit vector");
        }
    }

    public static int getInt(byte[] data) {
        byte[] bytes = new byte[4];
        System.arraycopy(data, 0, bytes, 4 - Math.min(data.length, 4), Math.min(data.length, 4));
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return bb.getInt();
    }

    private BigInteger extractInstNum(Instruction inst) {
        return getCode(inst);
    }

    private static boolean isRealInst(Instruction inst) {
        return inst.getRawInstruction() != null;
    }
    public Map<BigInteger, BigInteger> getCodeToOffset() {
        return codeToOffset;
    }


}
