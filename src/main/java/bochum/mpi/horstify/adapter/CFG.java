package bochum.mpi.horstify.adapter;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;

import java.util.List;
import java.util.Set;

public class CFG {

    Graph<BasicBlock, DefaultEdge> cfg = new DirectedPseudograph<>(DefaultEdge.class);
    //EdgeReversedGraph<BasicBlock, DefaultEdge> cfg_reversed;

    public CFG (List<BasicBlock> bbs) {
        for (BasicBlock bb : bbs) {
            cfg.addVertex(bb);
        }

        for (BasicBlock bb : bbs) {
            for (BasicBlock bout : bb.getOutgoingEdges())
                cfg.addEdge(bb, bout);
        }

        cfg.addEdge(BasicBlock.getStart(), BasicBlock.getExit());

        //cfg_reversed = new EdgeReversedGraph(cfg);
    }

    public Set<BasicBlock> getBbs() {
        return cfg.vertexSet();
    }

    public GraphPath<BasicBlock, DefaultEdge> getPath(BasicBlock b1, BasicBlock b2) {
        DijkstraShortestPath<BasicBlock, DefaultEdge> dijkstraAlg =
                new DijkstraShortestPath<>(cfg);

        return dijkstraAlg.getPath(b1,b2);
    }

    public List<GraphPath<BasicBlock, DefaultEdge>> getPaths(BasicBlock b1, BasicBlock b2) {
        AllDirectedPaths<BasicBlock, DefaultEdge> pathAlg =
                new AllDirectedPaths<>(cfg);

        return pathAlg.getAllPaths(b1, b2, true, null);
    }

}
