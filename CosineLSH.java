//Author: Sam Watson

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;


/* Estimates the cosine distance of points (tweets) to their nearest neighbors using locality sensitive hashing.
 * See included readme for a detailed explanation of how this works.
 */
public class CosineLSH {
	private HashSet<HashTable> tables; //A set of LSH HashTables, which map tweet hash codes to buckets of (probably) similar tweets
	
	/* numTables = total number of tables to use 
	 * numHyperplanes = number of hyperplanes to use in each table 
	 * maxTweetsPerBucket = maximum number of tweets a bucket can contain. 
	 * allWords = list of all words in the dataset, including duplicates. Useful for strategically generating hyperplanes.
	 */
	public CosineLSH(int numTables, int numHyperplanes, int maxTweetsPerBucket, List<Integer> allWords){
		//Generate hashtables
		tables = new HashSet<HashTable>();
		Random rando = new Random(2015); //Needed for seeding random hyperplanes in each table
		for(int i=0;i<numTables;i++){
			tables.add(new HashTable(numHyperplanes, maxTweetsPerBucket, allWords, rando.nextInt()));
		}
	} 
	
	//Adds a given tweet to each of the tables.
	//Returns the NearestNeighbor found for the tweet across all the tables (faster than adding/finding distance separately)
	public NearestNeighbor addTweet(Long tweetID, HashSet<Integer> words){
		//Add tweet to each table
		//and find nearest neighbor in each. Keep track of best.
		NearestNeighbor actualNearestNeighbor = new NearestNeighbor(tweetID, null, Double.POSITIVE_INFINITY); //Initialize dummy nearest neighbor
		for(HashTable table : tables){
			BitSet hashCode = table.addTweet(tweetID, words);
			NearestNeighbor candidateNearestNeighbor = table.findNearestNeighbor(tweetID, words, hashCode);
			double distance = candidateNearestNeighbor.getDistance();
			if(distance < actualNearestNeighbor.getDistance()){
				actualNearestNeighbor = candidateNearestNeighbor;
			}
		}
		return actualNearestNeighbor;
	}
}
