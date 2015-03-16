//Author: Sam Watson

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

//Communicates with Twitter API to query tweet IDs and return their actual content.
public class TweetFinder {
	private Twitter twitter;
	private String[] loginCredentials;
	
	
	public TweetFinder(){
		//Initialize Twitter object and verify credentials
		twitter = TwitterFactory.getSingleton();
		loginCredentials = loadCredentials();
		twitter.setOAuthConsumer(loginCredentials[0], loginCredentials[1]);
		AccessToken accessToken = loadAccessToken();
		twitter.setOAuthAccessToken(accessToken);
	}
	
	private AccessToken loadAccessToken(){
		String token = loginCredentials[2];
		String tokenSecret = loginCredentials[3];
		return new AccessToken(token, tokenSecret);
	}
	
	public Status lookupStatus(Long tweetID) throws NumberFormatException, TwitterException{
		return twitter.showStatus(tweetID);
	}
	
	public String lookupStatusContent(Long tweetID) throws NumberFormatException, TwitterException{
		Status status = twitter.showStatus(tweetID);
		return status.getText();
	}
	
	private String[] loadCredentials(){
		try {
			ObjectInput input = new ObjectInputStream(new BufferedInputStream(new FileInputStream("LoginCredentials")));
			String[] credentials = (String[]) input.readObject();
			input.close();
			return credentials;	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
}
