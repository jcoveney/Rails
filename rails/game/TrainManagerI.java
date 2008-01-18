/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/Attic/TrainManagerI.java,v 1.4 2008/01/18 19:58:15 evos Exp $ */
package rails.game;

import java.util.List;

/**
 * Interface for CompanyManager objects. A company manager is a factory which
 * vends Company objects.
 */
public interface TrainManagerI
{

	/**
	 * This is the name by which the TrainManager should be registered with the
	 * ComponentManager.
	 */
	static final String COMPONENT_NAME = "TrainManager";

	public List<TrainI> getAvailableNewTrains();

	public TrainTypeI getTypeByName(String name);

	public List<TrainTypeI> getTrainTypes();

	public void checkTrainAvailability(TrainI train, Portfolio from);

	public boolean hasAvailabilityChanged();

	public void resetAvailabilityChanged();

	public boolean hasPhaseChanged();

}
