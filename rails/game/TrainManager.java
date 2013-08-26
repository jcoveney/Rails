/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/TrainManager.java,v 1.28 2010/04/21 21:25:50 evos Exp $ */package rails.game;import java.util.*;import org.apache.log4j.Logger;import rails.common.LocalText;import rails.common.parser.ConfigurableComponentI;import rails.common.parser.ConfigurationException;import rails.common.parser.Tag;import rails.game.move.ObjectMove;import rails.game.state.BooleanState;import rails.game.state.IntegerState;import rails.util.Util;public class TrainManager implements ConfigurableComponentI {    // Static attributes    protected List<TrainType> lTrainTypes = new ArrayList<TrainType>();    protected Map<String, TrainType> mTrainTypes            = new HashMap<String, TrainType>();    protected List<TrainCertificateType> trainCertTypes             = new ArrayList<TrainCertificateType>();    protected Map<String, TrainCertificateType> trainCertTypeMap            = new HashMap<String, TrainCertificateType>();    protected Map<String, TrainI> trainMap            = new HashMap<String, TrainI>();        protected Map<TrainCertificateType, List<TrainI>> trainsPerCertType             = new HashMap<TrainCertificateType, List<TrainI>>();        protected TrainType defaultType = null; // Only required locally and in ChoiceType        private boolean removeTrain = false;    // defines obsolescence    public enum ObsoleteTrainForType {ALL, EXCEPT_TRIGGERING}    protected ObsoleteTrainForType obsoleteTrainFor = ObsoleteTrainForType.EXCEPT_TRIGGERING; // default is ALL    // Dynamic attributes    protected IntegerState newTypeIndex;        protected Map<String, Integer> lastIndexPerType = new HashMap<String, Integer>();    protected boolean trainsHaveRusted = false;    protected boolean phaseHasChanged = false;    protected boolean trainAvailabilityChanged = false;    protected List<PublicCompanyI> companiesWithExcessTrains;    protected GameManagerI gameManager = null;    protected Bank bank = null;        /** Required for the sell-train-to-foreigners feature of some games */    protected BooleanState anyTrainBought = new BooleanState ("AnyTrainBought", false);        // Triggered phase changes    protected Map<TrainCertificateType, Map<Integer, Phase>> newPhases            = new HashMap<TrainCertificateType, Map<Integer, Phase>>();    // Non-game attributes    protected Portfolio ipo, pool, unavailable;        // For initialisation only    boolean trainPriceAtFaceValueIfDifferentPresidents = false;    protected static Logger log =        Logger.getLogger(TrainManager.class.getPackage().getName());        /**     * No-args constructor.     */    public TrainManager() {        newTypeIndex = new IntegerState("NewTrainTypeIndex", 0);    }    /**     * @see rails.common.parser.ConfigurableComponentI#configureFromXML(org.w3c.dom.Element)     */    public void configureFromXML(Tag tag) throws ConfigurationException {                TrainType newType;        Tag defaultsTag = tag.getChild("Defaults");        // We will use this tag later, to preconfigure TrainCertType and TrainType.        List<Tag> typeTags;        // Choice train types (new style)        List<Tag> trainTypeTags = tag.getChildren("TrainType");        if (trainTypeTags != null) {            for (Tag trainTypeTag : trainTypeTags) {                TrainCertificateType certType = new TrainCertificateType();                if (defaultsTag != null) certType.configureFromXML(defaultsTag);                certType.configureFromXML(trainTypeTag);                trainCertTypes.add(certType);                trainCertTypeMap.put(certType.getName(), certType);                                // The potential train types                typeTags = trainTypeTag.getChildren("Train");                if (typeTags == null) {                    // That's OK, all properties are in TrainType, to let's reuse that tag                    typeTags = Arrays.asList(trainTypeTag);                }                for (Tag typeTag : typeTags) {                    newType = new TrainType();                    if (defaultsTag != null) newType.configureFromXML(defaultsTag);                    newType.configureFromXML(trainTypeTag);                    newType.configureFromXML(typeTag);                    lTrainTypes.add(newType);                    mTrainTypes.put(newType.getName(), newType);                    certType.addPotentialTrainType(newType);                }            }        }               // Special train buying rules        Tag rulesTag = tag.getChild("TrainBuyingRules");        if (rulesTag != null) {            // A 1851 special            trainPriceAtFaceValueIfDifferentPresidents = rulesTag.getChild("FaceValueIfDifferentPresidents") != null;        }                // Train obsolescence        String obsoleteAttribute = tag.getAttributeAsString("ObsoleteTrainFor");        if (Util.hasValue(obsoleteAttribute)) {            try {                obsoleteTrainFor= ObsoleteTrainForType.valueOf(obsoleteAttribute);            } catch (Exception e) {                throw new ConfigurationException(e);            }        }        // Are trains sold to foreigners?        Tag removeTrainTag = tag.getChild("RemoveTrainBeforeSR");        if (removeTrainTag != null) {            // Trains "bought by foreigners" (1844, 1824)            removeTrain = true; // completed in finishConfiguration()        }            }    public void finishConfiguration (GameManagerI gameManager)    throws ConfigurationException {        this.gameManager = gameManager;        bank = gameManager.getBank();        ipo = bank.getIpo();        pool = bank.getPool();        unavailable = bank.getUnavailable();        Map<Integer, String> newPhaseNames;        Phase phase;        String phaseName;        PhaseManager phaseManager = gameManager.getPhaseManager();                for (TrainCertificateType certType : trainCertTypes) {            certType.finishConfiguration(gameManager);                        List<TrainType> types = certType.getPotentialTrainTypes();            for (TrainType type : types) {                type.finishConfiguration(gameManager, certType);            }                        // Now create the trains of this type            TrainI train;            // Multi-train certificates cannot yet be assigned a type            TrainType initialType = types.size() == 1 ? types.get(0) : null;                        /* If the amount is infinite, only one train is created.             * Each time this train is bought, another one is created.             */            for (int i = 0; i < (certType.hasInfiniteQuantity() ? 1 : certType.getQuantity()); i++) {                train = certType.createTrain ();                train.init(certType, initialType, getNewUniqueId(certType.getName()));                addTrain(train);                unavailable.addTrain(train);            }                        // Register any phase changes            newPhaseNames = certType.getNewPhaseNames();            if (newPhaseNames != null && !newPhaseNames.isEmpty()) {                for (int index : newPhaseNames.keySet()) {                    phaseName = newPhaseNames.get(index);                    phase = (Phase)phaseManager.getPhaseByName(phaseName);                    if (phase == null) {                        throw new ConfigurationException ("New phase '"+phaseName+"' does not exist");                    }                    if (newPhases.get(certType) == null) newPhases.put(certType, new HashMap<Integer, Phase>());                    newPhases.get(certType).put(index, phase);                }            }        }        // By default, set the first train type to "available".        newTypeIndex.set(0);        makeTrainAvailable(trainCertTypes.get(newTypeIndex.intValue()));        // Trains "bought by foreigners" (1844, 1824)        if (removeTrain) {            gameManager.setGameParameter(GameDef.Parm.REMOVE_TRAIN_BEFORE_SR, true);        }                // Train trading between different players at face value only (1851)        gameManager.setGameParameter(GameDef.Parm.FIXED_PRICE_TRAINS_BETWEEN_PRESIDENTS,                trainPriceAtFaceValueIfDifferentPresidents);    }    /** Create train without throwing exceptions.     * To be used <b>after</b> completing initialization,     * i.e. in cloning infinitely available trains.     */    public TrainI cloneTrain (TrainCertificateType certType) {        TrainI train = null;        List<TrainType> types = certType.getPotentialTrainTypes();        TrainType initialType = types.size() == 1 ? types.get(0) : null;        try {            train = certType.createTrain();        } catch (ConfigurationException e) {            log.warn("Unexpected exception", e);        }        train.init(certType, initialType, getNewUniqueId(certType.getName()));        addTrain(train);        return train;    }    public void addTrain (TrainI train) {        trainMap.put(train.getUniqueId(), train);                TrainCertificateType type = train.getCertType();        if (!trainsPerCertType.containsKey(type)) {            trainsPerCertType.put (type, new ArrayList<TrainI>());        }        trainsPerCertType.get(type).add(train);    }    public TrainI getTrainByUniqueId(String id) {        return trainMap.get(id);    }        public String getNewUniqueId (String typeName) {        int newIndex = lastIndexPerType.containsKey(typeName) ? lastIndexPerType.get(typeName) + 1 : 0;        lastIndexPerType.put (typeName, newIndex);        return typeName + "_"+ newIndex;    }    /**     * This method handles any consequences of new train buying (from the IPO),     * such as rusting and phase changes. It must be called <b>after</b> the     * train has been transferred.     *     */    public void checkTrainAvailability(TrainI train, Portfolio from) {        trainsHaveRusted = false;        phaseHasChanged = false;        if (from != ipo) return;        TrainCertificateType boughtType, nextType;        boughtType = train.getCertType();        if (boughtType == (trainCertTypes.get(newTypeIndex.intValue()))            && ipo.getTrainOfType(boughtType) == null) {            // Last train bought, make a new type available.            newTypeIndex.add(1);            if (newTypeIndex.intValue() < lTrainTypes.size()) {                nextType = (trainCertTypes.get(newTypeIndex.intValue()));                if (nextType != null) {                    if (!nextType.isAvailable()) {                        makeTrainAvailable(nextType);                        trainAvailabilityChanged = true;                        ReportBuffer.add("All " + boughtType.getName()                                         + "-trains are sold out, "                                         + nextType.getName() + "-trains now available");                    }                }            }        }                int trainIndex = boughtType.getNumberBoughtFromIPO();        if (trainIndex == 1) {            // First train of a new type bought            ReportBuffer.add(LocalText.getText("FirstTrainBought",                    boughtType.getName()));        }                // New style phase changes, can be triggered by any bought train.        Phase newPhase;        if (newPhases.get(boughtType) != null                && (newPhase = newPhases.get(boughtType).get(trainIndex)) != null) {            gameManager.getPhaseManager().setPhase(newPhase, train.getHolder());            phaseHasChanged = true;        }    }        protected void makeTrainAvailable (TrainCertificateType type) {        type.setAvailable();        Portfolio to =            (type.getInitialPortfolio().equalsIgnoreCase("Pool") ? bank.getPool()                    : bank.getIpo());        for (TrainI train : trainsPerCertType.get(type)) {            new ObjectMove(train, unavailable, to);        }    }    // checks train obsolete condition    private boolean isTrainObsolete(TrainI train, Portfolio lastBuyingCompany) {        // check fist if train can obsolete at all        if (!train.getCertType().isObsoleting()) return false;                // then check if obsolete type        if (obsoleteTrainFor == ObsoleteTrainForType.ALL) {            return true;        } else  { // otherwise it is AllExceptTriggering            Portfolio holder = train.getHolder();            return (holder.getOwner() instanceof PublicCompanyI && holder != lastBuyingCompany);        }    }        protected void rustTrainType (TrainCertificateType type, Portfolio lastBuyingCompany) {        type.setRusted();        for (TrainI train : trainsPerCertType.get(type)) {            Portfolio holder = train.getHolder();            // check condition for train rusting            if (isTrainObsolete(train, lastBuyingCompany) && holder != pool) {                log.debug("Train " + train.getUniqueId() + " (owned by "                        + holder.getName() + ") obsoleted");                train.setObsolete();                holder.getTrainsModel().update();            } else {                log.debug("Train " + train.getUniqueId() + " (owned by "                        + holder.getName() + ") rusted");                train.setRusted();            }        }        // report about event        if (type.isObsoleting()) {            ReportBuffer.add(LocalText.getText("TrainsObsolete." + obsoleteTrainFor, type.getName()));        } else {            ReportBuffer.add(LocalText.getText("TrainsRusted",type.getName()));        }    }        public List<TrainI> getAvailableNewTrains() {        List<TrainI> availableTrains = new ArrayList<TrainI>();        TrainI train;        for (TrainCertificateType type : trainCertTypes) {            if (type.isAvailable()) {                train = ipo.getTrainOfType(type);                if (train != null) {                    availableTrains.add(train);                }            }        }        return availableTrains;    }    public String getTrainCostOverview() {        StringBuilder b = new StringBuilder();        for (TrainCertificateType certType : trainCertTypes) {            if (certType.getCost() > 0) {                if (b.length() > 1) b.append(" ");                b.append(certType.getName()).append(":").append(Bank.format(certType.getCost()));                if (certType.getExchangeCost() > 0) {                    b.append("(").append(Bank.format(certType.getExchangeCost())).append(")");                }            } else {                for (TrainType type : certType.getPotentialTrainTypes()) {                    if (b.length() > 1) b.append(" ");                    b.append(type.getName()).append(":").append(Bank.format(type.getCost()));                }            }        }        return b.toString();    }        public TrainType getTypeByName(String name) {        return mTrainTypes.get(name);    }    public List<TrainType> getTrainTypes() {        return lTrainTypes;    }    public List<TrainCertificateType> getTrainCertTypes() {        return trainCertTypes;    }        public TrainCertificateType getCertTypeByName (String name) {        return trainCertTypeMap.get(name);    }    public boolean hasAvailabilityChanged() {        return trainAvailabilityChanged;    }    public void resetAvailabilityChanged() {        trainAvailabilityChanged = false;    }    public boolean hasPhaseChanged() {        return phaseHasChanged;    }    public boolean isAnyTrainBought () {        return anyTrainBought.booleanValue();    }        public void setAnyTrainBought (boolean newValue) {        if (isAnyTrainBought() != newValue) {            anyTrainBought.set(newValue);        }    }    public List<TrainType> parseTrainTypes(String trainTypeName) {        List<TrainType> trainTypes = new ArrayList<TrainType>();        TrainType trainType;        for (String trainTypeSingle : trainTypeName.split(",")) {            trainType = getTypeByName(trainTypeSingle);            if (trainType != null) {                trainTypes.add(trainType);            } else {                continue;            }        }        return trainTypes;    }}
