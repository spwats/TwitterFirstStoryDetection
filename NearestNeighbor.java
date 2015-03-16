//Author: Sam Watson

//Small class for representing a tweet, its nearest neighbor, and their distance
public class NearestNeighbor {
	private long ID; //ID of this tweet
	private Long neighborID; //ID of the nearest neighboring tweet
	private double distance; //Cosine distance to the nearest neighbor
	
	public NearestNeighbor(long ID, Long neighborID, double distance) {
		this.ID = ID;
		this.neighborID = neighborID;
		this.distance = distance;
	}

	public long getID() {
		return ID;
	}

	public Long getNeighborID() {
		return neighborID;
	}

	public double getDistance() {
		return distance;
	}
	
	
}
