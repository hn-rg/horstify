package bochum.mpi.horstify.adapter;

import ch.securify.decompiler.instructions.Instruction;
import ch.securify.decompiler.instructions.JumpI;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;

public class PDG {

    private final CFG cfg;

    public PDG(CFG cfg) {
        this.cfg = cfg;
    }

    public void fakeCompute() {
        for (BasicBlock bb : cfg.getBbs()) {
            if (bb.getBranchingInstruction().isPresent() &&  bb.getBranchingInstruction().get() instanceof JumpI) {
                for (BasicBlock b : bb.getOutgoingEdges())
                    addEdge(bb, b);
            }
        }
    }

    private void addEdge(BasicBlock b1, BasicBlock b2) {
        b2.addController(b1);
    }

    public void compute() {
        Map<BasicBlock, Set<BasicBlock>> pDomSet = computePostDominators();

        for (BasicBlock bb1 : cfg.getBbs()) {
            for (BasicBlock bb2 : cfg.getBbs()) {
                List<GraphPath<BasicBlock, DefaultEdge>> paths;

                if (pDomSet.get(bb1).contains(bb2))
                    continue;

                if ((paths = cfg.getPaths(bb1, bb2)).isEmpty())
                    continue;

                outer:
                for (GraphPath<BasicBlock, DefaultEdge> gp : paths) {
                    List<BasicBlock> vertexList = gp.getVertexList();
                    for (int i = 1; i < vertexList.size()-1; i++) {
                        if (pDomSet.get(vertexList.get(i)).contains(bb2))
                            continue outer;
                    }
                    addEdge(bb1, bb2);
                    break;
                }
            }
        }
    }

    public void computeEasy() {
        Map<BasicBlock, Set<BasicBlock>> pDomSet = computePostDominators();

        for (BasicBlock bb1 : cfg.getBbs()) {
            for (BasicBlock bb2 : cfg.getBbs()) {
                if (cfg.getPath(bb1, bb2) == null)
                    continue;

                Optional<Instruction> branching = bb1.getBranchingInstruction();
                if (branching.isPresent() && branching.get() instanceof JumpI && bb1.getOutgoingEdges().size() > 1) {
                    boolean one = pDomSet.get(bb1.getOutgoingEdges().get(0)).contains(bb2);
                    boolean two = pDomSet.get(bb1.getOutgoingEdges().get(1)).contains(bb2);

                    if (one ^ two)
                        addEdge(bb1, bb2);
                }
            }
        }
    }

    private Map<BasicBlock, Set<BasicBlock>> computePostDominators() {
        Map<BasicBlock, Set<BasicBlock>> pDomSet = new HashMap<>();

        pDomSet.put(BasicBlock.getExit(), Collections.singleton(BasicBlock.getExit()));
        for (BasicBlock bb : cfg.getBbs()) {
            pDomSet.putIfAbsent(bb, cfg.getBbs());
        }

        boolean changes;
        do {
            changes = false;
            for (BasicBlock bb : cfg.getBbs()) {
                if (bb.isExit())
                    continue;

                Set<BasicBlock> oldPDom = pDomSet.get(bb);
                Set<BasicBlock> newPDom = new HashSet<>(Collections.singleton(bb));

                List<BasicBlock> incoming = bb.getIncomingEdges();
                if (!incoming.isEmpty()) {
                    Set<BasicBlock> intersection = new HashSet<>(pDomSet.get(incoming.get(0)));
                    for (int i = 1; i < incoming.size(); i++) {
                        intersection.retainAll(new HashSet<>(pDomSet.get(incoming.get(i))));
                    }
                    newPDom.addAll(intersection);
                }

                pDomSet.replace(bb, newPDom);
                changes |= oldPDom.size() != newPDom.size();
            }
        } while (changes);

        return pDomSet;
    }
}
