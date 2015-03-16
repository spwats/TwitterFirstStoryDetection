//Author: Sam Watson

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


/* Reads a raw data file of tweets and converts it into a set of smaller files where each different word is given a unique integer code within its file
 * Each line contains the ID of the tweet, followed by a comma and then the encoded words separated by spaces, e.g.:
 * Tweet ID Number, wordID1 wordID2 wordID3
 */
public class TweetEncoder {
	private ArrayList<Integer> uniqueWordCounts; //Holds the number of unique words in each file of encoded tweets
	private HashMap<String, Integer> wordIDs; //Map words to respective IDs
	private BufferedWriter writer;
	private List<LinkedList<Integer>> allWords; //Holds lists of the each word encountered (including duplicates) in each encoded file. Useful for strategically generating hyperplanes.
	
	public TweetEncoder(){
		uniqueWordCounts = new ArrayList<Integer>();
		wordIDs = new HashMap<String, Integer>();
		allWords = new ArrayList<LinkedList<Integer>>();
	}

	//Reads raw tweet data from a given file and encodes it into a set of smaller files
	//where tweetsPerFile = the number of lines (tweets) in each subfile.
	//Each subfile is named like so: writeFileName_subfileID#.txt
	public void encode(String readFileName, String writeFileName, int tweetsPerFile){
		try {
			BufferedReader reader = new BufferedReader(new FileReader(readFileName));
			System.out.println("Encoding data from " + readFileName + "...");
			
			//Initialize new file
			int fileCount = 1;
			startNewFile(writeFileName + "_" + fileCount + ".txt");
			uniqueWordCounts.add(0);
			allWords.add(new LinkedList<Integer>());
			
			//Read raw data and encode to file
			int lineCount = 0;
			String line = null;
			while((line = reader.readLine()) != null){
				String tweetID = line.split(", ")[3]; //First line is metadata
				writer.write(tweetID + ", ");
				String[] content = reader.readLine().split(" "); //Next line is actual tweet content
				
				//Retrieve or create each word ID and write to file
				for(String word : content){
					if(word.startsWith("@") || word.startsWith("http")){ //ignore usernames and hyperlinks
						continue;
					}
					int wordID;
					if(! wordIDs.containsKey(word)){
						Integer wordCount = uniqueWordCounts.get(fileCount-1) + 1;
						uniqueWordCounts.set(fileCount -1, wordCount);
						wordID = wordCount;
						wordIDs.put(word, wordID);
					}
					else{
						wordID = wordIDs.get(word);
					}
					writer.write(wordID + " ");
					allWords.get(fileCount-1).add(wordID);
				}
				writer.write("\n");
				writer.flush();
				
				//Create next file if this one has reached the limit
				if(++lineCount > tweetsPerFile){
					startNewFile(writeFileName + "_" + ++fileCount + ".txt");
					lineCount = 0;
					uniqueWordCounts.add(0);
					allWords.add(new LinkedList<Integer>());
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Could not find file to read.");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Could not find file to write.");
			e.printStackTrace();
		}
	}
	
	//Tells the writer to direct output to a new file with a given name.
	private void startNewFile(String newFileName){
		if(writer != null){
			try{writer.close();
			}catch (Exception e){e.printStackTrace();}
		}
		try {writer = new BufferedWriter(new FileWriter(newFileName));
		} catch (IOException e) {e.printStackTrace();}
	}
	
	//Returns the unique word count of the file at a given index
	public int getUniqueWordCount(int index) {
		return uniqueWordCounts.get(index);
	}
	
	//Returns a list of all words in the file (including duplicates) at a given index
	public List<Integer> getAllWords(int index){
		LinkedList<Integer> list = allWords.get(index);
		Collections.shuffle(list); //We want our word list in random order
		return list;
	}
}
