//Author: Sam Watson

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;


/*
 * Probabilistically estimates buckets of similar tweets using cosine distance and locality sensitive hashing
 * Works by dividing the space of all possible words tweets can contain with random hyperplanes
 * and seeing which tweets fall into the same subspaces created by these hyperplanes. These tweets are likely nearest neighbors.
 * For a more detailed description, see included readme.
 */
public class HashTable {
	private HashMap<BitSet, Queue<TinyTweet>> table; //Map hashcodes to buckets of (probably) similar tweets
	private int maxTweetsPerBucket; //Max number of tweets a bucket can contain. Reduces number of distance calculations, but might lose a tweet's true nearest neighbor.
	private double[][] hyperplanes; //Hyperplanes to intersect space with
	private Random rando;
	
	public HashTable(int numHyperplanes, int maxTweetsPerBucket, List<Integer> allWords, int seed){
		this.maxTweetsPerBucket = maxTweetsPerBucket;
		table = new HashMap<BitSet, Queue<TinyTweet>>();
		rando = new Random(seed);

		//Initialize random hyperplanes
		hyperplanes = new double[numHyperplanes][4]; //For an explanation of how hyperplanes are represented, see makeRandomPlane() method
		for(int i=0;i<hyperplanes.length;i++){
			try {
				hyperplanes[i] = makeRandomPlane(allWords);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	//Small class for holding barebones data about tweets.
	//Includes tweet ID and a set of the (integer encoded) words it contains.
	private class TinyTweet{
		private long ID; //ID of tweet
		private HashSet<Integer> words; //Words in tweets.
		
		private TinyTweet(long ID, HashSet<Integer> words){
			this.ID = ID;
			this.words = words;
		}
	}
	
	//Returns a random hyperplane that passes through the origin
	//The plane is guaranteed to pass through positive subspace
	//The plane is represented in a compressed format since only two coordinates have nonzero values
	//*Index 0 == some coordinate
	//*Index 1 == value at that coordinate
	//*Index 2 == some other coordinate
	//*Index 3 == value at that coordinate
	//
	//Hyperplanes that divide the space more evenly are more useful for distinguishing tweets
	//Therefore, we choose hyperplane coordinates based on the frequency with which words appear in the dataset
	//See readme for a more detailed explanation
	private double[] makeRandomPlane(List<Integer> allWords) throws Exception{
		if(allWords.size() < 2){
			throw new Exception("Insufficient words to generate planes!");
		}
		
		//Pick two coordinates in hyperspace
		//allWords contains word instance in dataset, therfore we are more likely to pick coordinates that divide space using this list
		double coord1 = allWords.remove(0);
		double coord2 = allWords.remove(0);
		//Make sure we didn't choose identical coordinates
		int tryCount = 0;
		while(coord2 == coord1){
			if(tryCount >= allWords.size()){
				throw new Exception("Not enough unique words to generate planes!");
			}
			allWords.add((int)coord2);
			coord2 = allWords.remove(0);
		}
		
		//Give one coord a random negative coefficient, the other positive
		//This trivially creates a hyperplane that passes through both coordinates and origin
				double coefficient1 = 0 - positiveRandomDouble();
				double coefficient2 = positiveRandomDouble();
		
		//Return plane in compressed form
		double[] compressedPlane = {coord1, coefficient1, coord2, coefficient2};
		return compressedPlane; 
	}
	
	//Record in a binary string which side of each hyperplane the given point (ie tweet) lies on via dot product
	//Points that fall in same subspace have same hashcode and are likely to be similar
	//words = the set of encoded words in some tweet
	private BitSet generateHashCode(HashSet<Integer> words){
		BitSet tweetHashCode = new BitSet(hyperplanes.length);
		double[] hyperplane;
		for(int i=0;i<hyperplanes.length;i++){
			hyperplane = hyperplanes[i];
			if(pointPlaneDotProduct(words, hyperplane) < 0){
				tweetHashCode.set(i, false);
			}
			else{
				tweetHashCode.set(i, true);
			}
		}
		return tweetHashCode;
	}

	//Calculates a given tweet's hashcode and adds it to the table.
	//Returns the hashcode of the tweet as a BitSet
	public BitSet addTweet(long ID, HashSet<Integer> words){
		BitSet tweetHashCode = generateHashCode(words);
		
		//Add to table
		Queue<TinyTweet> bucket = table.get(tweetHashCode);
		if(bucket == null){
			bucket = new LinkedList<TinyTweet>();
		}
		TinyTweet tweet = new TinyTweet(ID, words);
		bucket.add(tweet);
		if(bucket.size() > maxTweetsPerBucket){
			bucket.remove(); //Remove oldest tweet to make room for new one
		}
		table.put(tweetHashCode, bucket);

		return tweetHashCode;
	}
	

	//Returns dot product of a given point and plane (where plane is in compressed format)
	//Since planes contain just two nonzero coefficients and points have binary coordinate values
	//we can greatly simplify this calculation
	private double pointPlaneDotProduct(HashSet<Integer> point, double[] plane){
		double dotProduct = 0.0;
		if(point.contains((int)plane[0])){
			dotProduct += plane[1];
		}
		if(point.contains((int)plane[2])){
			dotProduct += plane[3];
		}
		return dotProduct;
	}
	
	//Returns dot product of a two given points (ie tweets) represented as sets of coordinate indices where point has nonzero value (ie word IDs)
	//Since point coordinates are binary, the dot product == intersection of the sets
	private int pointDotProduct(HashSet<Integer> point1, HashSet<Integer> point2){
		HashSet<Integer> intersection = new HashSet<Integer>(point1);
		intersection.retainAll(point2);
		return intersection.size();
	}

	//Returns a random double > zero
	private double positiveRandomDouble(){
		double nonzero = rando.nextDouble();;
		while(nonzero == 0.0){
			nonzero = rando.nextDouble();
		}
		return nonzero;
	}

	//Returns the NearestNeighbor of a given tweet and it's corresponding hashCode
	//NearestNeighbor objects know the tweet's ID, its nearest neighbor's ID, and their cosine distance
	public NearestNeighbor findNearestNeighbor(Long tweetID, HashSet<Integer> words, BitSet hashCode){
		Queue<TinyTweet> neighbors = table.get(hashCode);
		if(neighbors == null){
			throw new IllegalArgumentException("The given hash code " + hashCode + " does not exist!");
		}

		//Look at all tweets in same bucket, find the closest one
		double minDistance = Double.POSITIVE_INFINITY;
		Long nearestNeighborID = null;
		for(TinyTweet neighbor : neighbors){
			if(neighbor.ID == tweetID){
				continue;
			}
			double distance = findCosineDistance(words, neighbor.words);
			if(distance < minDistance){
				minDistance = distance;
				nearestNeighborID = neighbor.ID;
			}
		}

		return new NearestNeighbor(tweetID, nearestNeighborID, minDistance);
	}

	//Returns the angle between two points (ie tweets) represented as sets of coordinate indices where point has nonzero value (ie word IDs)
	//Since tweets have binary coordinate values, we can trivially calculate distance from origin as sqrt(size of set)
	private double findCosineDistance(HashSet<Integer> point1, HashSet<Integer> point2){
		double cosAngle = pointDotProduct(point1, point2) / (Math.sqrt(point1.size()) * Math.sqrt(point2.size()));
		return Math.acos(cosAngle);	//angle
	}

	//Returns all the buckets in the table
	public HashSet<HashSet<Long>> getBuckets(){
		HashSet<HashSet<Long>> buckets = new HashSet<HashSet<Long>>();
		for(Queue<TinyTweet> bucket : table.values()){
			HashSet<Long> idBucket = new HashSet<Long>();
			Object[] tweets = bucket.toArray();
			for(Object tweet : tweets){
				TinyTweet castTweet = (TinyTweet) tweet;
				idBucket.add(castTweet.ID);
			}
			buckets.add(idBucket);
		}
		return buckets;
	}
}
