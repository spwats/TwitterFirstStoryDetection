//Author: Sam Watson

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

import twitter4j.TwitterException;


//Detects hot topics on twitter and the first tweet to initiate the conversation (if it exists in the dataset)
//uses cosine distance and locality sensitive hashing. Inspired by the approach described by Petrovic, Osborne, and Lavrenko
//in their paper Streaming First Story Detection with Application to Twitter (2010)
//See readme for more details
public class FirstStoryDetector {
	private HashMap<Long, TweetThread> parentsToThreads; //Map parent IDs (ie first tweet in thread) to TweetThread objects
	private HashMap<Long, Long> tweetsToParents; //Map tweet IDs to the parent ID of the thread they belong to 
	private CosineLSH lsh;
	private static TweetFinder finder = new TweetFinder();; //Queries Twitter for actual tweet content given a tweet ID
	private double noveltyThreshold = 0.75; //Threshold for considering a tweet significantly different that it discusses a "new" topic. 0.75 was the best value found via experimentation.
	private static Comparator<TweetThread> bigToSmallThreadComparator = new Comparator<TweetThread>(){ //Orders threads from largest to smallest
		@Override
		public int compare(TweetThread thread1, TweetThread thread2) {
			int size1 = thread1.getSize();
			int size2 = thread2.getSize();
			return size1 > size2 ? -1 : size1 == size2 ? 0 : 1;
		}
	};
	private static Comparator<TweetThread> smallToBigThreadComparator = new Comparator<TweetThread>(){ //Orders threads from smallest to largest
		@Override
		public int compare(TweetThread thread1, TweetThread thread2) {
			int size1 = thread1.getSize();
			int size2 = thread2.getSize();
			return size1 < size2 ? -1 : size1 == size2 ? 0 : 1;
		}
	};
	
	public FirstStoryDetector(int numTables, int numHyperplanes, int maxTweetsPerBucket, List<Integer> allWords){
		parentsToThreads = new HashMap<Long, TweetThread>();
		tweetsToParents = new HashMap<Long, Long>();
		lsh = new CosineLSH(numTables, numHyperplanes, maxTweetsPerBucket, allWords);
	}
	
	//Represents a conversational thread, ie tweets all discussing the same topic
	//Tweets are added to threads based on their nearest neighbors
	private class TweetThread{
		private long parentID; //ID of first tweet to discuss this topic
		private HashSet<Long> tweetsInThread; //All tweets in thread (except parent)
		private int size;
		private int totalNumWords; //Number of unique words in thread (useful for calculating entropy)
		private HashMap<Integer, Integer> wordCounts; //Map word IDs to the total number of times they appear in the thread (also useful for entropy)
		
		private TweetThread(Long parentID){
			this.parentID = parentID;
			tweetsInThread = new HashSet<Long>();
			size = 1;
			totalNumWords = 0;
			wordCounts = new HashMap<Integer, Integer>();
		}
		
		//Adds a tweet to the thread
		private void addTweet(Long tweetID, HashSet<Integer> words){
			tweetsInThread.add(tweetID);
			size++;
			totalNumWords += words.size();
			//Update word counts
			for(Integer word : words){
				if(wordCounts.containsKey(word)){
					wordCounts.put(word, wordCounts.get(word) + 1);
				}
				else{
					wordCounts.put(word, 1);
				}
			}
		}
		
		//Calculates entropy of a thread.
		//Entropy is a measure of the variance of words in a thread.
		//If all the tweets in a thread are very similar/identical, it has low entropy and is probably full of spam
		public double calculateEntropy(){
			double entropy = 0.0;
			for(Integer wordCount : wordCounts.values()){
				double fractionOfTotalWords = (double)wordCount/totalNumWords;
				entropy += fractionOfTotalWords * Math.log(fractionOfTotalWords);
			}
			return 0 - entropy;
		}

		public long getParentID() {
			return parentID;
		}

		public HashSet<Long> getTweetsInThread() {
			return tweetsInThread;
		}

		public int getSize() {
			return size;
		}
	}
	
	//Detects hot topics and tweets that started them in a given file of encoded tweets
	public void detect(String fileName, int numTweets){
		System.out.println("Reading data from " + fileName+ "...");
		BufferedReader reader;
		try {
			//Read encoded tweets from file
			reader = new BufferedReader(new FileReader(fileName));
			String line = null;
			int count = 0;
			while((line = reader.readLine()) != null && count < numTweets){
				if((count % 1000) == 0){
					System.out.println("Processed " + count + " tweets in this set.");
				}
				
				//Identify nearest neighbor/novelty of each tweet using LSH
				String[] splitLine = line.split(", ");
				if(splitLine.length == 1){ //Some tweets have no actual content once stopwords are removed
					//System.out.println(splitLine[0]);
					continue;
				}
				Long ID = Long.parseLong(splitLine[0]);
				String[] wordIDStrings = splitLine[1].split(" ");
				HashSet<Integer> wordIDs = new HashSet<Integer>();
				for(String idString : wordIDStrings){
					wordIDs.add(Integer.parseInt(idString));
				}
				NearestNeighbor nearestNeighbor = lsh.addTweet(ID, wordIDs);
				
				//Determine if tweet belongs in an existing thread or if it is novel enough to deserve a new one
				TweetThread thread;
				if(nearestNeighbor.getDistance() > noveltyThreshold){
					thread = new TweetThread(ID);
				}
				else{
					Long threadID = tweetsToParents.get(nearestNeighbor.getNeighborID());
					thread = parentsToThreads.get(threadID);
					thread.addTweet(ID, wordIDs);
				}
				//Update thread tables
				parentsToThreads.put(thread.getParentID(), thread);
				tweetsToParents.put(ID, thread.getParentID());
				count++;
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//Returns the top fastest growing threads in the dataset
	private PriorityQueue<TweetThread> findTopThreads(int capacity, Collection<TweetThread> threads){
		PriorityQueue<TweetThread> topThreads = new PriorityQueue<TweetThread>(capacity + 2, smallToBigThreadComparator); //If we sort threads small->large first, it's easy to dump the small ones
		
		for(TweetThread thread : threads){
			if(thread.calculateEntropy() < 2.7 || thread.getSize() < 35){ //(try to) ignore spam and tiny threads
				continue;
			}
			topThreads.add(thread);
			if(topThreads.size() > capacity){
				topThreads.poll();
			}
		}
		//Now sort threads large->small
		PriorityQueue<TweetThread> topThreadsBigToSmall = new PriorityQueue<TweetThread>(capacity + 2, bigToSmallThreadComparator); 
		topThreadsBigToSmall.addAll(topThreads);
		return topThreadsBigToSmall;
	}
	
	//Prints out data related to a given thread
	//numSubtweets = number of tweet IDs in that thread to display
	private static void printThread(TweetThread thread, int numSubtweets){
		System.out.println("Parent ID: " + thread.getParentID());
		try {
			System.out.println("Parent tweet content: " + finder.lookupStatusContent(thread.getParentID()));
		} catch (NumberFormatException | TwitterException e) {
			e.printStackTrace();
		}
		System.out.println("Size of thread: " + thread.getSize());
		System.out.println("Entropy: " + thread.calculateEntropy());
		System.out.println(numSubtweets +" tweets from this thread: ");
		int subcount = 0;
		for(Long subtweet : thread.getTweetsInThread()){
			System.out.println(subtweet);
			if(++subcount > numSubtweets){
				break;
			}
		}
		System.out.println();
	}
	
	public static void main(String[] args){
		PriorityQueue<TweetThread> globalTopThreads = new PriorityQueue<TweetThread>(50, smallToBigThreadComparator); //Index of overall fastest growing threads from every set analyzed
		FirstStoryDetector fsd;
		for(int i=25;i<=25;i++){ //Read raw data file 25 as an example. This is half a million tweets.
			//Initialize new encoder for each raw data file, which split each file into 5 smaller encoded files
			TweetEncoder encoder = new TweetEncoder();;
			int uniqueWordCount = 0;
			List<Integer> allWords = null;
			encoder.encode("cleanTweets_"+i+"_500000.txt", "codeTweets_" + i, 100000);
			
			//We split the raw file into 5 encoded files (sets), so process each separately
			for(int encodeFileID=1;encodeFileID<6;encodeFileID++){
				//Retrieve relevant data for this iteration from encoder
				uniqueWordCount = encoder.getUniqueWordCount(encodeFileID-1);
				System.out.println("Unique word count: " + uniqueWordCount);
				allWords = encoder.getAllWords(encodeFileID-1);
				//Find those threads!
				fsd = new FirstStoryDetector(25, 200, 70, allWords); //Use 25 tables, 200 hyperplanes/table, 70 words max/bucket. These values chosen via experimentation.
				System.gc(); //Now is a good time to clean up old stuff from last iteration
				fsd.detect("codeTweets_" + i + "_" + encodeFileID + ".txt", 100000);
				
				//Print info for top 10 threads for this iteration (less than 10 threads that meet selection criteria may exist)
				PriorityQueue<TweetThread> topThreads = fsd.findTopThreads(10, fsd.parentsToThreads.values());
				if(topThreads.isEmpty()){
					System.out.println("No good threads in this set!");
				}
				for(int j=0;j<10;j++){
					if(topThreads.isEmpty()){
						break;
					}
					
					TweetThread thread = topThreads.poll();
					printThread(thread, 5);
					//Add thread to global top threads. If it's in the top 50 so far, it'll stick.
					globalTopThreads.add(thread);
					if(globalTopThreads.size() > 50){
						globalTopThreads.poll();
					}
					System.out.println("**************");
				}
				
			System.out.println("######### Done with this set ###########");
			}
		}
		//Show top threads found overall
		PriorityQueue<TweetThread> globalTopThreadsBigToSmall = new PriorityQueue<TweetThread>(50, smallToBigThreadComparator); //Order biggest to smallest
		globalTopThreadsBigToSmall.addAll(globalTopThreads);
		System.out.println("\n\n");
		System.out.println("**Top Overall fastest growing threads: **");
		for(TweetThread thread : globalTopThreadsBigToSmall){
			printThread(thread, 10);
		}
	System.out.println("Done");
	}	
}
			


