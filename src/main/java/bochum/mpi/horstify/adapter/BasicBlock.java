package bochum.mpi.horstify.adapter;

import ch.securify.decompiler.instructions.*;

import java.util.*;
import java.util.stream.Collectors;

public class BasicBlock {
    private final List<Instruction> instructions;
    private Optional<Instruction> branchingInstruction = Optional.empty();

    private List<BasicBlock> outgoingEdges = new LinkedList<>();
    private List<BasicBlock> incomingEdges = new LinkedList<>();

    private final List<BasicBlock> controlledByBB = new LinkedList<>();

    private boolean isStart = false;
    private boolean isExit = false;

    private static final BasicBlock START = new BasicBlock(new LinkedList<>());
    private static final BasicBlock EXIT = new BasicBlock(new LinkedList<>());

    static {
        START.isStart = true;
        EXIT.isExit = true;
    }

    private static void reset() {
        START.outgoingEdges = new LinkedList<>();
        START.incomingEdges = new LinkedList<>();

        EXIT.outgoingEdges = new LinkedList<>();
        EXIT.incomingEdges = new LinkedList<>();

        START.addOutgoingEdges(Collections.singletonList(EXIT));
        EXIT.addIncomingEdges(Collections.singletonList(START));
    }

    public BasicBlock(List<Instruction> instructions) {
        this.instructions = instructions;
        if (EXIT != null)
            reset();
    }

    public static List<BasicBlock> generateBaseBlocks(List<Instruction> instructions) {
        List<BasicBlock> bbs = new LinkedList<>();
        bbs.add(getStart());
        bbs.add(getExit());

        Set<Instruction> alreadyProcessed = new HashSet<>();
        Queue<Instruction> queue = new LinkedList<>();
        Instruction inst = instructions.get(0);
        outer:
        do {
            if (alreadyProcessed.contains(inst))
                continue;

            alreadyProcessed.add(inst);
            List<Instruction> currentBB = new LinkedList<>();
            bbs.add(new BasicBlock(currentBB));

            currentBB.add(inst);

            Instruction next;
            while ((next = inst.getNext()) != null || inst instanceof Jump ) {
                if (inst instanceof Jump) {
                    queue.add(new ArrayList<>(((Jump) inst).getOutgoingBranches()).get(0));
                    continue outer;
                }

                if (next instanceof JumpDest) {
                    queue.add(next);
                    continue outer;
                }

                if (next instanceof JumpI) {
                    currentBB.add(next);
                    queue.add(next.getNext());
                    queue.addAll(((JumpI) next).getOutgoingBranches());
                    continue outer;
                }

                inst = next;
                currentBB.add(inst);
            }
        } while ((inst = queue.poll()) != null);

        for (BasicBlock bb : bbs) {
            for (Instruction instruction : bb.instructions)
                instruction.setBb(bb);
        }

        return bbs;
    }

    public void connectBB() {
        if (instructions.isEmpty() && !isStart && !isExit )
            throw new IllegalStateException();

        if (isStart || isExit)
            return;

        Instruction firstInst = instructions.get(0);
        Instruction lastInst = instructions.get(instructions.size() - 1);

        branchingInstruction =  Optional.of(lastInst);

        if (firstInst.getPrev() == null && !(firstInst instanceof JumpDest)) {
            getStart().addOutgoingEdges(Collections.singletonList(this));
        }

        if (lastInst.getNext() != null)
            addOutgoingEdges(Collections.singletonList(lastInst.getNext().getBb()));

        if (lastInst instanceof JumpI) {
            BranchInstruction bi = (BranchInstruction) lastInst;
            Collection<Instruction> outgoingBranches = bi.getOutgoingBranches();
            LinkedList<Instruction> li = new LinkedList<>(outgoingBranches);
            switch (li.size()) {
                case 0 -> addOutgoingEdges(Collections.singletonList(getExit()));
                case 2 ->
                        throw new IllegalStateException("ContactHelper::opcodeAdapter: more than one outgoing branch");
                default -> addOutgoingEdges(Collections.singletonList(li.get(0).getBb()));
            }
        }  else if (lastInst instanceof Jump) {
            Jump jump = (Jump) lastInst;
            Collection<Instruction> outgoingBranches = jump.getOutgoingBranches();

            if (outgoingBranches.size() > 1)
                throw new IllegalStateException();
            if (outgoingBranches.size() != 0)
                addOutgoingEdges(Collections.singletonList(new LinkedList<>(outgoingBranches).get(0).getBb()));
        } else if (lastInst.getNext() == null) {
            addOutgoingEdges(Collections.singletonList(getExit()));
            //getExit().addIncomingEdges(Collections.singletonList(this));
        }

        outgoingEdges.forEach(this::registerIncomingBranch);
    }

    public void registerIncomingBranch(BasicBlock bb) {
        if (incomingEdges.contains(bb))
            return;

        incomingEdges.add(bb);
    }

    public Optional<Instruction> getBranchingInstruction() {
        return branchingInstruction;
    }

    public static BasicBlock getStart() {
        return START;
    }

    public static BasicBlock getExit() {
        return EXIT;
    }

    public boolean isStart() {
        return isStart;
    }

    public boolean isExit() {
        return isExit;
    }

    public void addOutgoingEdges(List<BasicBlock> outgoingEdges) {
        assert outgoingEdges.stream().noneMatch(Objects::isNull);
        this.outgoingEdges.addAll(outgoingEdges);
    }

    public List<BasicBlock> getOutgoingEdges() {
        return outgoingEdges;
    }

    public List<BasicBlock> getIncomingEdges() {
        return incomingEdges;
    }

    public void addIncomingEdges(List<BasicBlock> incomingEdges) {
        this.incomingEdges.addAll(incomingEdges);
    }

    public void addControlledByBB(List<BasicBlock> controlledByBB) {
        this.controlledByBB.addAll(controlledByBB);
    }

    public void addController(BasicBlock bb) {
        controlledByBB.add(bb);
    }

    public void addDependenciesToInstruction() {
        List<Instruction> controlledByInst = controlledByBB.stream()
                .map(BasicBlock::getBranchingInstruction)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        instructions.forEach(inst ->
                inst.addIsControlled(controlledByInst));
    }
}
