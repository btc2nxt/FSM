package nxt.at;

import java.util.SortedMap;
import java.util.TreeMap;


public class AT_Transaction{
	
	private static SortedMap<Long,SortedMap<Long,AT_Transaction>> all_AT_Txs = new TreeMap<>();
	
	//private byte[] senderId = new byte[ AT_Constants.AT_ID_SIZE ];
	private byte[] recipientId = new byte[ AT_Constants.AT_ID_SIZE ];
	private long amount;
	private byte[] message;
	private int x;
	private int y;
	private long assetId;
	
	public AT_Transaction( byte[] recipientId , long amount , byte[] message, int x, int y, long assetId ){
		//this.senderId = senderId.clone();
		this.recipientId = recipientId.clone();
		this.amount = amount;
		this.message = (message != null) ? message.clone() : null;
		this.x = x;
		this.y = y;
		this.assetId = assetId;		
	}
	
	public long getAmount(){
		return amount;
	}
	
	public long getAssetId(){
		return assetId;
	}
	
	public byte[] getRecipientId(){
		return recipientId;
	}
	
	public long getRecipientIdLong(){
		return AT_API_Helper.getLong(recipientId);		
	}

	public byte[] getMessage() {
		return message;
	}

	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}

	public void addTransaction( long atId , Long height) {
		
		
		if (all_AT_Txs.containsKey(atId)){
			all_AT_Txs.get(atId).put(height, this);
		}
		else
		{
			SortedMap< Long , AT_Transaction > temp = new TreeMap<>();
			temp.put( (Long) height , this );
			all_AT_Txs.put( atId , temp );
		}
		
	}
	
	public static AT_Transaction getATTransaction(Long atId, Long height){
		if (all_AT_Txs.containsKey(atId)){
			return all_AT_Txs.get(atId).get(height);
		}
		return null;
	}
	
}
