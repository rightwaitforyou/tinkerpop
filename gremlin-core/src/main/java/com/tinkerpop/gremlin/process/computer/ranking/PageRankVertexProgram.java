package com.tinkerpop.gremlin.process.computer.ranking;

import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.computer.GraphComputer;
import com.tinkerpop.gremlin.process.computer.MessageType;
import com.tinkerpop.gremlin.process.computer.Messenger;
import com.tinkerpop.gremlin.process.computer.VertexProgram;
import com.tinkerpop.gremlin.process.graph.GraphTraversal;
import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Property;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.util.Serializer;
import com.tinkerpop.gremlin.util.StreamFactory;
import com.tinkerpop.gremlin.util.function.SSupplier;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;

import java.io.IOException;
import java.util.Map;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PageRankVertexProgram implements VertexProgram<Double> {

    private MessageType.Local messageType = MessageType.Local.of(() -> GraphTraversal.of().outE());

    public static final String PAGE_RANK = Property.hidden("gremlin.pageRankVertexProgram.pageRank");
    public static final String EDGE_COUNT = Property.hidden("gremlin.pageRankVertexProgram.edgeCount");

    private static final String VERTEX_COUNT = "gremlin.pageRankVertexProgram.vertexCount";
    private static final String ALPHA = "gremlin.pageRankVertexProgram.alpha";
    private static final String TOTAL_ITERATIONS = "gremlin.pageRankVertexProgram.totalIterations";
    private static final String INCIDENT_TRAVERSAL = "gremlin.pageRankVertexProgram.incidentTraversal";

    private double vertexCountAsDouble = 1;
    private double alpha = 0.85d;
    private int totalIterations = 30;

    public PageRankVertexProgram() {

    }

    public void initialize(final Configuration configuration) {
        this.vertexCountAsDouble = configuration.getDouble(VERTEX_COUNT, 1.0d);
        this.alpha = configuration.getDouble(ALPHA, 0.85d);
        this.totalIterations = configuration.getInt(TOTAL_ITERATIONS, 30);
        try {
            if (configuration.containsKey(INCIDENT_TRAVERSAL)) {
                final SSupplier<Traversal<Vertex, Edge>> incidentTraversal = (SSupplier) Serializer.deserializeObject((byte[]) configuration.getProperty(INCIDENT_TRAVERSAL));
                this.messageType = MessageType.Local.of(incidentTraversal);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    public Map<String, KeyType> getComputeKeys() {
        return VertexProgram.ofComputeKeys(PAGE_RANK, KeyType.VARIABLE, EDGE_COUNT, KeyType.CONSTANT);
    }

    public Class<Double> getMessageClass() {
        return Double.class;
    }

    public void setup(final GraphComputer.SideEffects sideEffects) {

    }

    public void execute(final Vertex vertex, Messenger<Double> messenger, final GraphComputer.SideEffects sideEffects) {
        if (sideEffects.isInitialIteration()) {
            double initialPageRank = 1.0d / this.vertexCountAsDouble;
            double edgeCount = Long.valueOf(this.messageType.edges(vertex).count()).doubleValue();
            vertex.property(PAGE_RANK, initialPageRank);
            vertex.property(EDGE_COUNT, edgeCount);
            messenger.sendMessage(vertex, this.messageType, initialPageRank / edgeCount);
        } else {
            double newPageRank = StreamFactory.stream(messenger.receiveMessages(vertex, this.messageType)).reduce(0.0d, (a, b) -> a + b);
            newPageRank = (this.alpha * newPageRank) + ((1.0d - this.alpha) / this.vertexCountAsDouble);
            vertex.property(PAGE_RANK, newPageRank);
            messenger.sendMessage(vertex, this.messageType, newPageRank / vertex.<Double>property(EDGE_COUNT).orElse(0.0d));
        }
    }

    public boolean terminate(final GraphComputer.SideEffects sideEffects) {
        return sideEffects.getIteration() >= this.totalIterations;
    }

    //////////////////////////////

    public static Builder create() {
        return new Builder();
    }

    public static class Builder implements VertexProgram.Builder {

        private final Configuration configuration = new BaseConfiguration();

        private Builder() {
            this.configuration.setProperty(GraphComputer.VERTEX_PROGRAM, PageRankVertexProgram.class.getName());
        }

        public Builder iterations(final int iterations) {
            this.configuration.setProperty(TOTAL_ITERATIONS, iterations);
            return this;
        }

        public Builder alpha(final double alpha) {
            this.configuration.setProperty(ALPHA, alpha);
            return this;
        }

        public Builder incidentTraversal(final SSupplier<Traversal<Vertex, Edge>> incidentTraversal) throws IOException {
            configuration.setProperty(INCIDENT_TRAVERSAL, Serializer.serializeObject(incidentTraversal));
            return this;
        }

        public Builder vertexCount(final long vertexCount) {
            this.configuration.setProperty(VERTEX_COUNT, (double) vertexCount);
            return this;
        }

        public Configuration getConfiguration() {
            return this.configuration;
        }
    }

    ////////////////////////////

    public Features getFeatures() {
        return new Features() {

            public boolean requiresLocalMessageTypes() {
                return true;
            }

            public boolean requiresVertexPropertyAddition() {
                return true;
            }
        };
    }

}