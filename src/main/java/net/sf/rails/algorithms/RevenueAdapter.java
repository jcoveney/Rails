package net.sf.rails.algorithms;

import java.awt.EventQueue;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.rails.common.LocalText;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.Phase;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.Train;
import net.sf.rails.ui.swing.hexmap.HexMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.SimpleGraph;


/**
 * RevenueAdapter links the revenue algorithm to Rails.
 */
public final class RevenueAdapter implements Runnable {

    protected static Logger log =
        LoggerFactory.getLogger(RevenueAdapter.class);
    
    // define VertexVisitSet
    public class VertexVisit {
        public Set<NetworkVertex> set;
        public VertexVisit() {set = new HashSet<NetworkVertex>();}
        public VertexVisit(Collection<NetworkVertex> coll) {set = new HashSet<NetworkVertex>(coll);}
        public String toString() {
            return "VertexVisit Set:" + set;
        }
    }    
    
    // define EdgeTravelSet
    public class EdgeTravel {
        public Set<NetworkEdge> set;
        public EdgeTravel() {set = new HashSet<NetworkEdge>();}
        public EdgeTravel(Collection<NetworkEdge> coll) {set = new HashSet<NetworkEdge>(coll);}
        public String toString() {
            return "EdgeTravel Set:" + set;
        }
    }
    
    // basic links, to be defined at creation
    private final RailsRoot root;
    private final RevenueManager revenueManager;
    private final NetworkAdapter networkAdapter;
    private final PublicCompany company;
    private final Phase phase;

    // basic components, defined empty at creation
    private NetworkGraph graph;
    private Set<NetworkVertex> startVertices;
    private List<NetworkTrain> trains;
    private List<VertexVisit> vertexVisitSets;
    private List<RevenueBonus> revenueBonuses;
    private Set<NetworkVertex> protectedVertices;
    private Map<NetworkEdge, EdgeTravel> edgeTravelSets;
    
    // components related to the revenue calculator
    private RevenueCalculator rc;
    private boolean useMultiGraph;
    Graph<NetworkVertex,NetworkEdge> rcGraph;
    private List<NetworkVertex> rcVertices;
    private List<NetworkEdge> rcEdges;
    private List<RevenueTrainRun> optimalRun;
    private boolean hasDynamicModifiers;
    
    // revenue listener to communicate results
    private RevenueListener revenueListener;
    
    public RevenueAdapter(RailsRoot root, NetworkAdapter networkAdapter, 
            PublicCompany company, Phase phase){
        this.root = root;
        this.revenueManager = root.getRevenueManager();
        this.networkAdapter = networkAdapter;
        this.company = company;
        this.phase = phase;
        
        this.graph = null;
        this.trains = new ArrayList<NetworkTrain>();
        this.startVertices = new HashSet<NetworkVertex>();
        this.vertexVisitSets = new ArrayList<VertexVisit>();
        this.edgeTravelSets = new HashMap<NetworkEdge, EdgeTravel>();
        this.revenueBonuses = new ArrayList<RevenueBonus>();
        this.protectedVertices = new HashSet<NetworkVertex>();
    }
    
    public static RevenueAdapter createRevenueAdapter(RailsRoot root, PublicCompany company, Phase phase) {
        NetworkAdapter networkAdapter = NetworkAdapter.create(root);
        RevenueAdapter ra = new RevenueAdapter(root, networkAdapter, company, phase);
        ra.populateFromRails();
        return ra;
    }
    
    
    public PublicCompany getCompany() {
        return company;
    }
    
    public Phase getPhase() {
        return phase;
    }
    
    public SimpleGraph<NetworkVertex,NetworkEdge> getGraph() {
        return graph.getGraph();
    }
    
    public Set<NetworkVertex> getVertices() {
        return graph.getGraph().vertexSet();
    }
   
    public Set<NetworkEdge> getEdges() {
        return graph.getGraph().edgeSet();
    }
    
    public Graph<NetworkVertex,NetworkEdge> getRCGraph() {
        return rcGraph;
    }
    
    public int getRCVertexId(NetworkVertex vertex) {
        return rcVertices.indexOf(vertex);
    }

    public int getRCEdgeId(NetworkEdge edge) {
        return rcEdges.indexOf(edge);
    }
        
    public Set<NetworkVertex> getStartVertices() {
        return startVertices;
    }
    
    public boolean addStartVertices(Collection<NetworkVertex> startVertices) {
        this.startVertices.addAll(startVertices);
        protectedVertices.addAll(startVertices);
        return true;
    }
    
    public List<NetworkTrain> getTrains() {
        return trains;
    }
    
    public boolean addTrain(Train railsTrain){
        NetworkTrain train = NetworkTrain.createFromRailsTrain(railsTrain);
        if (train == null) {
            return false;
        } else {
            trains.add(train);
            return true;
        }
    }
    
    public void addTrain(NetworkTrain train) {
        trains.add(train);
    }
    
    public void removeTrain(NetworkTrain train) {
        trains.remove(train);
    }

    public boolean addTrainByString(String trainString) {
        NetworkTrain train = NetworkTrain.createFromString(trainString);
        if (train == null) return false;
        trains.add(train);
        return true;
    }

    public List<VertexVisit> getVertexVisitSets() {
        return vertexVisitSets;
    }
    
    public void addVertexVisitSet(VertexVisit visit) {
        vertexVisitSets.add(visit);
        protectedVertices.addAll(visit.set);
    }
    
    public List<RevenueBonus> getRevenueBonuses() {
        return revenueBonuses;
    }
    
    public void addRevenueBonus(RevenueBonus bonus)  {
        revenueBonuses.add(bonus);
        protectedVertices.addAll(bonus.getVertices());
    }
    
    public void removeRevenueBonus(RevenueBonus bonus) {
        revenueBonuses.remove(bonus);
        // TODO: Change protectedVertices to multiSet then you can unprotect vertices
    }
    
    public void populateFromRails() {
        // define graph, without HQ
        graph = networkAdapter.getRouteGraphCached(company, false);
        
        // initialize vertices
        NetworkVertex.initAllRailsVertices(graph, company, phase);

        // define startVertexes
        addStartVertices(graph.getCompanyBaseTokenVertexes(company));
        
        // define visit sets
        defineVertexVisitSets();
        
        // define revenueBonuses
        defineRevenueBonuses();
        
        // define Trains
        for (Train train:company.getPortfolioModel().getTrainList()) {
            addTrain(train);
        }

        // add all static modifiers
        if (revenueManager != null) {
            revenueManager.initStaticModifiers(this);
        }

    }

    private void defineVertexVisitSets() {
        // define map of all locationNames 
        Map<String, VertexVisit> locations = new HashMap<String, VertexVisit>();
        for (NetworkVertex vertex:getVertices()) {
            String ln = vertex.getStopName();
            if (ln == null) continue;
            if (locations.containsKey(ln)) {
                locations.get(ln).set.add(vertex);
            } else {
                VertexVisit v = new VertexVisit();
                v.set.add(vertex);
                locations.put(ln, v);
            }
        }
        log.info("Locations = " + locations);
        // convert the location map to the vertex sets
        for (VertexVisit location:locations.values()) {
            if (location.set.size() >= 2) {
                addVertexVisitSet(location);
            }
        }
    }

    private void defineRevenueBonuses() {
        // create set of all hexes
        Set<MapHex> hexes = new HashSet<MapHex>();
        for (NetworkVertex vertex:getVertices()) {
            MapHex hex = vertex.getHex();
            if (hex != null) hexes.add(hex);
        }
        
        // check each vertex hex for a potential revenue bonus
        for (MapHex hex:hexes) {
            List<RevenueBonusTemplate> bonuses = new ArrayList<RevenueBonusTemplate>();
            List<RevenueBonusTemplate> hexBonuses = hex.getRevenueBonuses();
            if (hexBonuses != null) bonuses.addAll(hexBonuses);
            List<RevenueBonusTemplate> tileBonuses = hex.getCurrentTile().getRevenueBonuses();
            if (tileBonuses != null) bonuses.addAll(tileBonuses);

            for (RevenueBonusTemplate bonus:bonuses) {
                RevenueBonus bonusConverted = bonus.toRevenueBonus(hex, root, graph);
                if (bonusConverted != null) {
                    addRevenueBonus(bonusConverted);
                }
            }
        }
        log.info("RA: RevenueBonuses = " + revenueBonuses);
    }

    /**
     * checks the set of trains for H-trains
     * @return true if H-trains are used
     */
    private boolean useHTrains() {
        boolean useHTrains = false;
        for (NetworkTrain train:trains) {
            if (train.isHTrain()) {
                useHTrains = true;
            }
        }
        return useHTrains;
    }
    
    public void initRevenueCalculator(boolean useMultiGraph){
        
        this.useMultiGraph = useMultiGraph;

        // check for dynamic modifiers (including an own calculator
        if (revenueManager != null) {
            hasDynamicModifiers = revenueManager.initDynamicModifiers(this);
        }
        
        // define optimized graph
        
        if (useMultiGraph) {
            // generate phase 2 graph
            NetworkMultigraph multiGraph = networkAdapter.getMultigraph(company, protectedVertices); 
            rcGraph = multiGraph.getGraph();
            // retrieve edge sets
            edgeTravelSets.putAll(multiGraph.getPhaseTwoEdgeSets(this));
        } else {
            // generate standard graph
            rcGraph = networkAdapter.getRevenueGraph(company, protectedVertices).getGraph();
        }
   
        // define the vertices and edges lists
        rcVertices = new ArrayList<NetworkVertex>(rcGraph.vertexSet());
        // define ordering on vertexes by value
        Collections.sort(rcVertices, new NetworkVertex.ValueOrder());
        rcEdges = new ArrayList<NetworkEdge>(rcGraph.edgeSet());
        Collections.sort(rcEdges, new NetworkEdge.CostOrder());

        // prepare train length
        prepareTrainLengths(rcVertices);

        // check dimensions
        int maxVisitVertices = maxVisitVertices();
        int maxBonusVertices = maxRevenueBonusVertices();
        int maxNeighbors = maxVertexNeighbors(rcVertices);
        int maxTravelEdges = maxTravelEdges();
         
        if (useMultiGraph) {
            if (useHTrains()) {
                rc = new RevenueCalculatorMultiHex(this, rcVertices.size(), rcEdges.size(), 
                        maxNeighbors, maxVisitVertices, maxTravelEdges, trains.size(), maxBonusVertices);
            } else {
                rc = new RevenueCalculatorMulti(this, rcVertices.size(), rcEdges.size(), 
                        maxNeighbors, maxVisitVertices, maxTravelEdges, trains.size(), maxBonusVertices);
            }
        } else {
            rc = new RevenueCalculatorSimple(this, rcVertices.size(), rcEdges.size(), 
                    maxNeighbors, maxVisitVertices, trains.size(), maxBonusVertices); 
        }
        
        populateRevenueCalculator();
    }

    private int maxVisitVertices() {
        int maxNbVertices = 0;
        for (VertexVisit vertexVisit:vertexVisitSets) {
            maxNbVertices = Math.max(maxNbVertices, vertexVisit.set.size());
        }
        log.info("RA: Block of " + vertexVisitSets + ", maximum vertices in a set = "+ maxNbVertices);
        return maxNbVertices;
    }

    private int maxVertexNeighbors(Collection<NetworkVertex> vertices) {
        int maxNeighbors = 0;
        for (NetworkVertex vertex:vertices) {
            maxNeighbors = Math.max(maxNeighbors, rcGraph.edgesOf(vertex).size());
        }
        log.info("RA: Maximum neighbors in graph = "+ maxNeighbors);
        return maxNeighbors;
    }

    private int maxRevenueBonusVertices() {
        // get the number of non-simple bonuses
        int nbBonuses = RevenueBonus.getNumberNonSimpleBonuses(revenueBonuses);
        log.info("Number of non simple bonuses = " + nbBonuses);
        return nbBonuses;
    }

    private int maxTravelEdges() {
        int maxNbEdges = 0;
        for (EdgeTravel edgeTravel:edgeTravelSets.values()) {
            maxNbEdges = Math.max(maxNbEdges, edgeTravel.set.size());
        }
        for (NetworkEdge edge:edgeTravelSets.keySet()) {
            EdgeTravel edgeTravel = edgeTravelSets.get(edge);
            StringBuilder edgeString = new StringBuilder("RA: EdgeSet for " + edge.toFullInfoString() + 
                   " size = " + edgeTravel.set.size() + "\n");
            for (NetworkEdge edgeInSet:edgeTravel.set) {
                edgeString.append(edgeInSet.toFullInfoString() + "\n");
            }
            log.info(edgeString.toString());
        }
        log.info("RA: maximum edges in a set = "+ maxNbEdges);
        return maxNbEdges;
    }

    
    private void prepareTrainLengths(Collection<NetworkVertex> vertices) {
        
        // separate vertexes
        List<NetworkVertex> cities = new ArrayList<NetworkVertex>();
        List<NetworkVertex> towns = new ArrayList<NetworkVertex>();
        for (NetworkVertex vertex: vertices) {
            if (vertex.isMajor()) cities.add(vertex);
            if (vertex.isMinor()) towns.add(vertex);
        }
        
        int maxCities = cities.size();
        int maxTowns = towns.size();
        
        // check train lengths
        int maxCityLength = 0, maxTownLength = 0;
        for (NetworkTrain train: trains) {
            int trainTowns = train.getMinors();
            if (train.getMajors() > maxCities) {
                trainTowns = trainTowns+ train.getMajors() - maxCities;
                train.setMajors(maxCities);
            }
            train.setMinors(Math.min(trainTowns, maxTowns));

            maxCityLength = Math.max(maxCityLength, train.getMajors());
            maxTownLength = Math.max(maxTownLength, train.getMinors());
        }
        
    }
    
    private void populateRevenueCalculator(){
        
        for (int id=0; id < rcVertices.size(); id++){ 
            NetworkVertex v = rcVertices.get(id);
            // add to revenue calculator
            v.addToRevenueCalculator(rc, id);
            for (int trainId=0; trainId < trains.size(); trainId++) {
                NetworkTrain train = trains.get(trainId);
                rc.setVertexValue(id, trainId, getVertexValue(v, train, phase));
            }
            
            // set neighbors, now regardless of sink property
            // this is covered by the vertex attribute
            // and required for startvertices that are sinks themselves
            if (useMultiGraph) {
                Set<NetworkEdge> edges = rcGraph.edgesOf(v);
                int e=0; int[] edgesArray = new int[edges.size()];
                for (NetworkEdge edge:edges) {
                    edgesArray[e++] = rcEdges.indexOf(edge);
                }
                // sort by order on edges
                Arrays.sort(edgesArray, 0, e);
                // define according vertices
                int[] neighborsArray = new int[e];
                for (int j=0; j < e; j++) {
                    NetworkVertex toVertex = Graphs.getOppositeVertex(rcGraph, rcEdges.get(edgesArray[j]), v);
                    neighborsArray[j] = rcVertices.indexOf(toVertex);
                }
                rc.setVertexNeighbors(id, neighborsArray, edgesArray);
            } else {
                List<NetworkVertex> neighbors = Graphs.neighborListOf(rcGraph, v); 
                int j=0, neighborsArray[] = new int[neighbors.size()];
                for (NetworkVertex n:neighbors){
                    neighborsArray[j++] = rcVertices.indexOf(n);
                }
                // sort by value orderboolean activatePrediction
                Arrays.sort(neighborsArray, 0, j);
                // define according edges
                int[] edgesArray = new int[j];
                for (int e=0; e < j; e++) {
                    NetworkVertex toVertex = rcVertices.get(neighborsArray[e]);
                    edgesArray[e] = rcEdges.indexOf(rcGraph.getEdge(v, toVertex));
                }
                rc.setVertexNeighbors(id, neighborsArray, edgesArray);
            }
        }

        // set startVertexes
        int startVertexId =0, sv[] = new int[startVertices.size()];
        for (NetworkVertex startVertex:startVertices) {
            sv[startVertexId++] = rcVertices.indexOf(startVertex);
        }
        Arrays.sort(sv); // sort by value order 
        rc.setStartVertexes(sv);
        
        // set edges
        for (int id=0; id < rcEdges.size(); id++) {
            // prepare values
            NetworkEdge e = rcEdges.get(id);
            boolean greedy = e.isGreedy();
            int distance = e.getDistance();
            rc.setEdge(id, greedy, distance);
        }
        
        // set trains, check for H-trains
        for (int id=0; id < trains.size(); id++) {
            NetworkTrain train = trains.get(id);
            train.addToRevenueCalculator(rc, id);
        }
        
        // set vertex sets
        for (VertexVisit visit:vertexVisitSets) {
            int j=0, setArray[] = new int[visit.set.size()];
            for (NetworkVertex n:visit.set){
                setArray[j++] = rcVertices.indexOf(n);
            }
            rc.setVisitSet(setArray);
        }
        log.info("RA: rcVertices:" + rcVertices);
        log.info("RA: rcEdges:" + rcEdges);

        // set revenue bonuses
        int id = 0;
        for (RevenueBonus bonus:revenueBonuses) {
            if (bonus.addToRevenueCalculator(rc, id, rcVertices, trains, phase)) id ++;
        }
        
        log.info("RA: edgeTravelSets:" + edgeTravelSets);
        
        // set edge sets
        if (useMultiGraph) {
            for (NetworkEdge edge:edgeTravelSets.keySet()) {
                EdgeTravel edgeTravel = edgeTravelSets.get(edge);
                int j=0, setArray[] = new int[edgeTravel.set.size()];
                for (NetworkEdge n:edgeTravel.set){
                    setArray[j++] = rcEdges.indexOf(n);
                }
                ((RevenueCalculatorMulti)rc).setTravelSet(rcEdges.indexOf(edge), setArray);
            }
        }

        
        // activate dynamic modifiers
        rc.setDynamicModifiers(hasDynamicModifiers);
    }

    public int getVertexValue(NetworkVertex vertex, NetworkTrain train, Phase phase) {
        
        // base value
        int value = vertex.getValueByTrain(train);
        
        // add potential revenueBonuses
        for (RevenueBonus bonus:revenueBonuses) {
            if (bonus.checkSimpleBonus(vertex, train.getRailsTrain(), phase)) {
                value += bonus.getValue();
            }
        }
        
        return value;
    }
    
    public String getVertexValueAsString(NetworkVertex vertex, NetworkTrain train, Phase phase) {
        StringBuffer s = new StringBuffer();
        
        // base value
        s.append(vertex.getValueByTrain(train));
        
        // add potential revenueBonuses
        for (RevenueBonus bonus:revenueBonuses) {
            if (bonus.checkSimpleBonus(vertex, train.getRailsTrain(), phase)) {
                s.append("+" + bonus.getValue());
            }
        }
        return s.toString();
    }
    
    
    private List<RevenueTrainRun> convertRcRun(int[][] rcRun) {
        
        List<RevenueTrainRun> convertRun = new ArrayList<RevenueTrainRun>();
                
        for (int j=0; j < rcRun.length; j++) {
            RevenueTrainRun trainRun = new RevenueTrainRun(this, trains.get(j));
            convertRun.add(trainRun);

            if (rcEdges.size() == 0) continue; 
            for (int v=0; v < rcRun[j].length; v++) {
                int id= rcRun[j][v];
                if (id == -1) break;
                if (useMultiGraph) {
                    trainRun.addEdge(rcEdges.get(id));
                } else {
                    trainRun.addVertex(rcVertices.get(id));
                }
            }
            if (useMultiGraph) {
                trainRun.convertEdgesToVertices();
            } else {
                trainRun.convertVerticesToEdges();
            }
        }
        return convertRun;
    }
    
    public int calculateRevenue() {
        // allows (one) dynamic modifiers to have their own revenue calculation method
        // TODO: Still to be added 
//        if (hasDynamicCalculator) {
//            return revenueManager.revenueFromDynamicCalculator(this);
//        } else { // otherwise standard calculation
            return calculateRevenue(0, trains.size() - 1);
//        }
    }
    
    public int calculateRevenue(int startTrain, int finalTrain) {
        if (startTrain < 0 || finalTrain >= trains.size() || startTrain > finalTrain) {
            return 0;
        }
        // the optimal run might change
        optimalRun = null;
        rc.initRuns(startTrain, finalTrain);
        rc.executePredictions(startTrain, finalTrain);
        int value = rc.calculateRevenue(startTrain, finalTrain);
        return value;
    }
    
    public  List<RevenueTrainRun> getOptimalRun() {
        if (optimalRun == null) {
            optimalRun = convertRcRun(rc.getOptimalRun());
            if (hasDynamicModifiers) { 
                revenueManager.adjustOptimalRun(optimalRun);
            }
        }
        return optimalRun;
    }
    
    public List<RevenueTrainRun> getCurrentRun() {
        return convertRcRun(rc.getCurrentRun());
    }
    
    /**
     * is called by rc for dynamic evaluations
     */
    int dynamicEvaluation() {
        int value = 0;
        if (hasDynamicModifiers) {
            value = revenueManager.evaluationValue(this.getCurrentRun(), false);
        }
        return value;
    }
    
    /**
     * is called by rc for dynamic predictions
     */
    int dynamicPrediction() {
        int value = 0;
        if (hasDynamicModifiers) {
            value = revenueManager.predictionValue(this.getCurrentRun());
        }
        return value;
    }

    public void addRevenueListener(RevenueListener listener) {
        this.revenueListener = listener;
    }

    void notifyRevenueListener(final int revenue, final boolean finalResult) {
        if (revenueListener == null) return;
        
        EventQueue.invokeLater(
                new Runnable() {
                    public void run() {
                        //listener could have deregistered himself in the meantime
                        if (revenueListener != null) revenueListener.revenueUpdate(revenue, finalResult);
                    }
                });
    }
    
    public void run() {
        calculateRevenue(0, trains.size() -1);
    }
    
    public void removeRevenueListener() {
        // only removes revenueListener
        revenueListener = null;
    }
    

    public String getOptimalRunPrettyPrint(boolean includeDetails) {
        List<RevenueTrainRun> listRuns = getOptimalRun();
        if (listRuns== null) return LocalText.getText("RevenueNoRun");

        StringBuffer runPrettyPrint = new StringBuffer();
        for (RevenueTrainRun run:listRuns) {
            runPrettyPrint.append(run.prettyPrint(includeDetails));
            if (!includeDetails && run != listRuns.get(listRuns.size()-1)) {
                    runPrettyPrint.append("; ");
            }
        }
        if (includeDetails) {
            if (revenueManager != null) {
                runPrettyPrint.append(revenueManager.prettyPrint(this));
            }
        } else {
            int dynamicBonuses = 0;
            if (hasDynamicModifiers) {
                dynamicBonuses = revenueManager.evaluationValue(this.getOptimalRun(), true);
            }
            if (dynamicBonuses != 0) {
                runPrettyPrint.append("; " + 
                        LocalText.getText("RevenueBonus", dynamicBonuses));
            }
        }
        return runPrettyPrint.toString();
    }
    
    public void drawOptimalRunAsPath(HexMap map) {
        List<RevenueTrainRun> listRuns = getOptimalRun();
        
        List<GeneralPath> pathList = new ArrayList<GeneralPath>();
        if (listRuns != null) {
            for (RevenueTrainRun run:listRuns) {
                pathList.add(run.getAsPath(map));
            }
        }
        map.setTrainPaths(pathList);
    }
    
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("RevenueCalculator:\n" + rc + "\n");
        buffer.append("rcVertices:\n" + rcVertices + "\n");
        buffer.append("rcEdges:\n" + rcEdges + "\n");
        buffer.append("startVertices:" + startVertices);
        return buffer.toString();
    }
    
}
