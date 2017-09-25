package nxt.at;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import nxt.AT;
import nxt.Account;
import nxt.Db;
import nxt.Nxt;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.Constants;

//NXT API IMPLEMENTATION

public class AT_API_Platform_Impl extends AT_API_Impl {

	private final static AT_API_Platform_Impl instance = new AT_API_Platform_Impl();

	private static List<Long> timeStampList = new ArrayList<>();
	private static int timeStampIndex;
	private static int startHeight;
	private static int endHeight;	
	private static int numOfTx;		

	AT_API_Platform_Impl()
	{

	}

	public static AT_API_Platform_Impl getInstance()
	{
		return instance;
	}

	@Override
	public long get_Block_Timestamp( AT_Machine_State state ) 
	{

		int height = Nxt.getBlockchain().getHeight();
		return AT_API_Helper.getLongTimestamp( height , 0 );

	}

	public long get_Creation_Timestamp( AT_Machine_State state ) //0x0301
	{
		return AT_API_Helper.getLongTimestamp( state.getCreationBlockHeight() , 0 );
	}

	@Override
	public long get_Last_Block_Timestamp( AT_Machine_State state ) 
	{

		int height = Nxt.getBlockchain().getHeight() - 1;
		return AT_API_Helper.getLongTimestamp( height , 0 );
	}

	@Override
	public void put_Last_Block_Hash_In_A( AT_Machine_State state ) {
		ByteBuffer b = ByteBuffer.allocate( state.get_A1().length * 4 );
		b.order( ByteOrder.LITTLE_ENDIAN );

		b.put( Nxt.getBlockchain().getLastBlock().getPreviousBlockHash() );

		byte[] temp = new byte[ 8 ];

		b.get( temp, 0 , 8 );
		state.set_A1( temp );

		b.get( temp , 0 , 8 );
		state.set_A2( temp );

		b.get( temp , 0 , 8 );
		state.set_A3( temp );

		b.get( temp , 0 , 8 );
		state.set_A4( temp );


	}

	@Override
	/*  0x33(EXT_FUN_DAT) 0x0304(FUN) 
	 *  330304:get tx sending AT account from timestamp(val)
	 *  if can not find a tx , store atId -> A3, now height+num -> A4
	 *  when read timestamp from A,	if A1==0 and A3= atId, then timestamp=A4
	 *  
	 *  sometimes there are too many these txs, so need parameters to filter them 
	 */
	public void A_to_Tx_after_Timestamp( long val , AT_Machine_State state ) {

		int height = AT_API_Helper.longToHeight( val );
		int numOfTx = AT_API_Helper.longToNumOfTx( val );

		Long atId = state.getLongId();		
		Long transactionId = 0L;
		Logger.logDebugMessage("get tx after timestamp "+val + " height: "+ height+" atId: "+ atId);
		
		//when AT first runs the function, or re-catch the timestamp, e.g. refunding 
		if (!AT_Controller.getTimeStampRetrieved() || height < state.getRetrievedHeight()){ 
			startHeight = AT_API_Helper.longToHeight( val );
			numOfTx = AT_API_Helper.longToNumOfTx( val );
			timeStampList = findATTransactions(startHeight, atId, numOfTx);
			AT_Controller.setTimeStampRetrieved(true);
			timeStampIndex = 0;
			
			AT at =AT.getAT(atId);
			//endHeight =blockHeight + at.getSleepBetween() +at.getWaitForNumberOfBlocks();				
			//endHeight =startHeight + 1;// +at.getWaitForNumberOfBlocks();
			//endHeight = Nxt.getBlockchain().getLastBlock().getHeight();//- at.getWaitForNumberOfBlocks() - 1;//at.getSleepBetween();			
		}
			
		//more then one block
		while (startHeight <= endHeight) {
			Logger.logDebugMessage("loop.... "+ startHeight + "-" + numOfTx);				
			if (timeStampList.isEmpty()) {
				numOfTx = 0;
				startHeight++;
				if (startHeight > endHeight){
					break;
				}
				timeStampList = findATTransactions(startHeight, atId, numOfTx);
				timeStampIndex = 0;					
			}
			if (!timeStampList.isEmpty() && timeStampList.size()>timeStampIndex){
				transactionId = timeStampList.get(timeStampIndex++);
				numOfTx++;
				break;
			}
			//get txs from next block				
			if (transactionId ==0){
				timeStampList.clear();
			}	
		}
		// if this block contain zero tx, next time will scan next block
		//if (startHeight > endHeight ) {
		//	startHeight= endHeight ;
		//}
						
		state.setRetrievedHeight( startHeight);
		
		Logger.logInfoMessage("tx with id "+transactionId+" found");
		clear_A( state );
		state.set_A1( AT_API_Helper.getByteArray( transactionId ) );
		// can not find a tx
		if (transactionId == 0) {
			state.set_A3( AT_API_Helper.getByteArray( atId ) );
			state.set_A4( AT_API_Helper.getByteArray( AT_API_Helper.getLongTimestamp( startHeight, 0 ) ) );			
		}
	}

	@Override
	public long get_Type_for_Tx_in_A( AT_Machine_State state ) {
		long txid = AT_API_Helper.getLong( state.get_A1() );

		Transaction tx = Nxt.getBlockchain().getTransaction( txid );

		if ( tx != null )
		{
			if (tx.getType().getType() == 1 )
			{
				return 1;
			}
		}
		return 0;
	}

	@Override  //0x0306
	public long get_Amount_for_Tx_in_A( AT_Machine_State state ) {
		long txId = AT_API_Helper.getLong( state.get_A1() );

		Transaction tx = Nxt.getBlockchain().getTransaction( txId );
		long amount = 0;
		if ( tx != null )
		{
			amount = tx.getAmountNQT();
		}
		return amount;
	}

	@Override //0x0307
	public long get_Timestamp_for_Tx_in_A( AT_Machine_State state ) {
		long txId = AT_API_Helper.getLong( state.get_A1() );
		long timeStamp;
		
		Logger.logInfoMessage("get timestamp for tx with id " + txId + " found");
		Transaction tx;
		
		if (txId != 0) {
			tx = Nxt.getBlockchain().getTransaction( txId );			
		} else 
			tx = null;

		if ( tx != null )
		{
			int blockHeight = tx.getHeight();

			byte[] bId = state.getId();
			byte[] b = new byte[ 8 ];
			for ( int i = 0; i < 8; i++ )
			{
				b[ i ] = bId[ i ];
			}

			int txHeight = findTransactionHeight( txId , blockHeight , AT_API_Helper.getLong( b ) );

			timeStamp = AT_API_Helper.getLongTimestamp( blockHeight , txHeight );
		}
		else {
			//Long atId = ;	
			Long atId = AT_API_Helper.getLong(state.getId());
			if ( atId == AT_API_Helper.getLong( state.get_A3() ) ) {
				Logger.logDebugMessage("get timestamp from A4");				
				timeStamp = AT_API_Helper.getLong( state.get_A4() );				
			}
			else
				timeStamp = AT_API_Helper.getLongTimestamp( startHeight+1 , 0 );
			//return AT_API_Helper.getLongTimestamp( Integer.MAX_VALUE , Integer.MAX_VALUE );
		}
    	state.setTimeStamp(timeStamp);
		return timeStamp;
	}

	@Override
	public long get_Ticket_Id_for_Tx_in_A( AT_Machine_State state ) {
		long txId = AT_API_Helper.getLong( state.get_A1() );

		Transaction tx = Nxt.getBlockchain().getTransaction( txId );

		if ( tx !=null )
		{
			int txBlockHeight = tx.getHeight();


			int blockHeight = Nxt.getBlockchain().getHeight();

			if ( blockHeight - txBlockHeight < AT_Constants.getInstance().BLOCKS_FOR_TICKET( blockHeight ) ){ //for tests - for real case 1440
				state.setDelayBlocks( (int)AT_Constants.getInstance().BLOCKS_FOR_TICKET( blockHeight ) - ( blockHeight - txBlockHeight ) );
				state.getMachineState().pc -= 11;
				state.setG_balance( 0L ); // hack to halt and continue from that point
				return 0;
			}

			MessageDigest digest = Crypto.sha256();

			byte[] senderPublicKey = tx.getSenderPublicKey();

			ByteBuffer bf = ByteBuffer.allocate( 2 * Long.SIZE + senderPublicKey.length );
			bf.order( ByteOrder.LITTLE_ENDIAN );
			bf.putLong( Nxt.getBlockchain().getLastBlock().getId() );
			bf.putLong( tx.getId() );
			bf.put( senderPublicKey);

			digest.update(bf.array());
			byte[] byteTicket = digest.digest();

			long ticket = Math.abs( AT_API_Helper.getLong( byteTicket ) );

			Logger.logDebugMessage( "info: ticket for txid: " + Convert.toUnsignedLong( tx.getId() ) + "is: " + ticket );
			return ticket;
		}
		return 0;
	}

	@Override //0x0309
	public long message_from_Tx_in_A_to_B( long val, AT_Machine_State state ) {
		long txid = AT_API_Helper.getLong( state.get_A1() );
		int highIntVal, lowIntVal;
		
		highIntVal = new Long(val / 4294967296L).intValue();  // div 2^32, java=power(2,32)
		lowIntVal = new Long(val % 4294967296L).intValue();		

		Transaction tx = Nxt.getBlockchain().getTransaction( txid );
		byte[] message = tx.getMessage().getMessage();

		String[] bets = Convert.toString(message).split(";");				
		
		//if ( message.length > state.get_A1().length * 4 )
		//	return 0;
		
		String regex;
        switch ( lowIntVal ) { 
        	case   4: regex = "[0-9][0-9];|[0-9][0-9]\\*([0-9]+)";
        		break;
        	case   5: regex = "(big|small|BIG|SMALL)(\\*([0-9]+)){0,1}";
        		break;
        	case   6: regex = "(even|odd|EVEN|ODD)(\\*([0-9]+)){0,1}";
    			break;        		
        	case   7: regex = "(red|green|blue|RED|GREEN|BLUE)(\\*([0-9]+)){0,1}";
    			break;        		
        	default :
        		regex = "";
        }
        
        if (lowIntVal >3 ) {
    		Pattern p = Pattern.compile(regex);
    		String foundBet="";
            for (int i = 0; i < bets.length; i++) {    
                if (p.matcher(bets[i]).matches()) {
                	foundBet = bets[i];
                	Logger.logDebugMessage("bet type:" + lowIntVal + " "+bets[i]+","+p.matcher(bets[i]).matches());                	
                	break;
                }
            }
            if (foundBet != ""){
            	switch (lowIntVal) {
            	case 4: SingleBet( foundBet, highIntVal, state);
            		break;
            	case 5: BigEvenBet( lowIntVal,foundBet, highIntVal, state);
            		break;
            	case 6: BigEvenBet( lowIntVal,foundBet, highIntVal, state);
            		break;
            	case 7: ColorBet( foundBet, highIntVal, state);
            		break;
            	default:      
            	}
            }
        } //update FSM message
        else if (lowIntVal == 1){
    		state.getMachineState().machineCodeUpdate = getMessageBytes(bets[0],"CODE");
    		state.getMachineState().machineDataUpdate = getMessageBytes(bets[1],"DATA");    		
        }
		/*
        ByteBuffer b = ByteBuffer.allocate( state.get_A1().length * 4 );
		b.order( ByteOrder.LITTLE_ENDIAN );
		b.put( message );
		b.clear();

		byte[] temp = new byte[ 8 ];

		b.get( temp, 0 , 8 );
		state.set_B1( temp );

		b.get( temp , 0 , 8 );
		state.set_B2( temp );

		b.get( temp , 0 , 8 );
		state.set_B3( temp );

		b.get( temp , 0 , 8 );
		state.set_B4( temp );
*/
		return 1;
	}
	
	void SingleBet(String bet, int extraNumber, AT_Machine_State state){
		int multiple ;
		int betValue;
		multiple = bet.indexOf("*");
		if (multiple > 0){
			betValue =  Integer.parseInt(bet.substring(0, multiple));
			multiple =  Integer.parseInt(bet.substring( multiple+1,bet.length()));			
		}
		else {
			betValue =  Integer.parseInt(bet);
			multiple =1;
		}
			
		state.set_B1(AT_API_Helper.getByteArray(multiple)); 	    
		state.set_B2(AT_API_Helper.getByteArray(betValue == extraNumber ? 1:0));	
			
	}
	
	void BigEvenBet(int betType, String bet, int extraNumber, AT_Machine_State state){
		int multiple ;
		int betValue;
		String betString;
		
		multiple = bet.indexOf("*");
		if (multiple > 0){
			betString = bet.substring(0, multiple).toUpperCase();
			multiple =  Integer.parseInt(bet.substring( multiple+1,bet.length()));			
		}
		else {
			betString =  bet;
			multiple =1;
		}
			
		state.set_B1(AT_API_Helper.getByteArray(multiple)); 	    
		if (betType == 5){
			state.set_B2(AT_API_Helper.getByteArray((extraNumber > 24 && betString == "BIG" ) || (extraNumber < 24 && betString == "SMALL" ) ? 1:0));
		}
		else {
			state.set_B2(AT_API_Helper.getByteArray((extraNumber % 2 ==0 && betString == "EVEN" ) || (extraNumber % 2 ==1 && betString == "ODD" ) ? 1:0));			
		}		
	}

	void ColorBet(String bet, int extraNumber, AT_Machine_State state){
		int multiple ;
		int betFound;
		String betString;
		
		int redArray[]   = {1,2,3};
		int greenArray[] = {4,5,6};
		int blueArray[]  = {7,8,9};		
		
		multiple = bet.indexOf("*");
		if (multiple > 0){
			betString = bet.substring(0, multiple).toUpperCase();
			multiple =  Integer.parseInt(bet.substring( multiple+1,bet.length()));			
		}
		else {
			betString =  bet;
			multiple =1;
		}
			
		switch (betString) {
		case "RED":   betFound = Arrays.binarySearch (redArray, extraNumber);
			break;
		case "GREEN": betFound = Arrays.binarySearch (greenArray, extraNumber);
			break;
		case "BLUE":  betFound = Arrays.binarySearch (blueArray, extraNumber);
			break;
		default:
			betFound = -1;
		}
		state.set_B1(AT_API_Helper.getByteArray(multiple)); 	    
		state.set_B2(AT_API_Helper.getByteArray(betFound > 0 ? 1:0));
			
	}

	byte[] getMessageBytes(String bet, String match) {
		int multiple ;
		String betString;
				
		multiple = bet.indexOf("=");
		if (multiple > 0){
			betString = bet.substring(0, multiple).toUpperCase();
			if (betString.equals(match)) {
				return Convert.parseHexString(bet.substring( multiple+1,bet.length()));
			} 
			else {
				return null;
			}
		}
		else {
			return null;
		}
	}
	
	@Override  //0x030a
	public long B_to_Address_of_Tx_in_A( AT_Machine_State state ) {

		long tx = AT_API_Helper.getLong( state.get_A1() );
		
		long address = Nxt.getBlockchain().getTransaction( tx ).getSenderId();

		clear_B( state );

		state.set_B1( AT_API_Helper.getByteArray( address ) );

		return 1;
	}

	@Override
	public void B_to_Address_of_Creator( AT_Machine_State state ) {
		long creator = AT_API_Helper.getLong( state.getCreator() );

		clear_B( state );

		state.set_B1( AT_API_Helper.getByteArray( creator ) );

	}

	@Override
	/* 0x0350 EXT_FUN_DAT_2
	 * get tx of timestamp(val) with the type
	 */
	public void A_to_Tx_after_Timestamp_with_Type( long val , long type, AT_Machine_State state ) {
		int height = AT_API_Helper.longToHeight( val );
		int numOfTx = AT_API_Helper.longToNumOfTx( val );

		Long atId = state.getLongId();		
		Long transactionId = 0L;
		Logger.logDebugMessage("get tx after timestamp "+val + " height: "+ height+" atId: "+ atId+ " type "+ type);
		
	    transactionId = findTransactionToAT(startHeight, atId, (int)type);
		if (transactionId != 0) {
		    Logger.logInfoMessage("tx with id "+transactionId+" found");			
		}

		clear_A( state );
		state.set_A1( AT_API_Helper.getByteArray( transactionId ) );
		// can not find a tx
		if (transactionId == 0) {
			state.set_A3( AT_API_Helper.getByteArray( atId ) );
			state.set_A4( AT_API_Helper.getByteArray( AT_API_Helper.getLongTimestamp( startHeight, 0 ) ) );			
		}
	}
	
	@Override
	/* 0x0351 EXT_FUN_DAT_2
	 * get tx between timestamp(B1,B2),val with the type
	 */
	public void A_To_Tx_between_Timestamps_with_Type( long val , int type, AT_Machine_State state ) {
		int height = AT_API_Helper.longToHeight( val );
		int numOfTx = AT_API_Helper.longToNumOfTx( val );

		Long atId = state.getLongId();		
		Long transactionId = 0L;
		Logger.logDebugMessage("get tx between timestamp "+val + " height: "+ height+" atId: "+ atId+ " type "+ type);
		
		//when AT first runs the function, or re-catch the timestamp, e.g. refunding 
		if (!AT_Controller.getTimeStampRetrieved() || height < state.getRetrievedHeight()){ 
			startHeight = AT_API_Helper.longToHeight( val );
			numOfTx = AT_API_Helper.longToNumOfTx( val );
			timeStampList = findATTransactions(startHeight, endHeight, atId, type, numOfTx);
			AT_Controller.setTimeStampRetrieved(true);
			timeStampIndex = 0;
			
			AT at =AT.getAT(atId);
		}
			
		Logger.logDebugMessage("loop.... "+ startHeight + "-" + numOfTx);				
		if (!timeStampList.isEmpty() && timeStampList.size()>timeStampIndex) {
			transactionId = timeStampList.get(timeStampIndex++);
			numOfTx++;
			Logger.logInfoMessage("tx with id "+transactionId+" found");
			clear_A( state );
			state.set_A1( AT_API_Helper.getByteArray( transactionId ) );
		}
		else {// can not find a tx
			state.set_A3( AT_API_Helper.getByteArray( atId ) );
			state.set_A4( AT_API_Helper.getByteArray( AT_API_Helper.getLongTimestamp( startHeight, 0 ) ) );			
		}
	}
	
	@Override
	/* 0x0352 EXT_FUN_DAT_2
	 * get number of txs between timestamp(B1,B2),val with the type
	 */
	public void A_To_TxsCount_between_Timestamps_with_Type( long val, int type, AT_Machine_State state ) {
		int height = AT_API_Helper.longToHeight( val );

		Long atId = state.getLongId();		
		Logger.logDebugMessage("get number of tx between timestamp "+val + " height: "+ height+" atId: "+ atId+ " type "+ type);
		
		state.set_A1( AT_API_Helper.getByteArray( findATTransactionsCount(startHeight, endHeight, atId, type) ) );
	}
	
	@Override //0x0400
	public long get_Current_Balance( AT_Machine_State state ) {
		long balance = Account.getAccount( AT_API_Helper.getLong(state.getId()) ).getBalanceNQT();
		return balance;
	}

	@Override
	public long get_Previous_Balance( AT_Machine_State state ) {
		return state.getP_balance();
	}

	@Override  //0x402
	public long send_to_Address_in_B( long val , AT_Machine_State state ) {
		
		if ( state.getG_balance() >= Constants.ONE_NXT ) { 
			if ( state.getG_balance() >= val ) {
				//AT_Transaction tx = new AT_Transaction(state.getId(), state.get_B1().clone() , val, null );
				AT_Transaction tx = new AT_Transaction( state.get_B1().clone() , val, null );
				state.addTransaction( tx );
				state.setG_balance( state.getG_balance() - val );
			}
			else {
				AT_Transaction tx = new AT_Transaction( state.get_B1().clone() , state.getG_balance(), null );			
				state.addTransaction( tx );
				state.setG_balance( 0L );
				//at.setG_balance( at.getG_balance() - 123 );
			}
		}
		return 1;
	}

	@Override  //0403 320304
	public long send_All_to_Address_in_B( AT_Machine_State state ) {
		
		//long atId = AT_API_Helper.getLong( state.getId() );
		//AT at = AT.getAT( atId );
		//bug: G_balance = 0 ,AT has to stop
		if ( state.getG_balance() >= 0 && AT_API_Helper.getLong(state.get_B1()) > 0 ) {
			AT_Transaction tx = new AT_Transaction( state.get_B1().clone() , state.getG_balance() , null );
			state.addTransaction( tx );
			state.setG_balance( 0L );			
		}

		return 1;
	}

	@Override
	public long send_Old_to_Address_in_B( AT_Machine_State state ) {
		
		AT at = AT.getAT( state.getId() );
		
		if ( at.getP_balance() > at.getG_balance()  )
		{
			AT_Transaction tx = new AT_Transaction( state.get_B1() , state.getG_balance(), null );
			state.addTransaction( tx );
			
			at.setG_balance( 0L );
			at.setP_balance( 0L );
		
		}
		else
		{
			AT_Transaction tx = new AT_Transaction( state.get_B1() , state.getP_balance(),null );
			state.addTransaction( tx );
			
			at.setG_balance( at.getG_balance() - at.getP_balance() );
			at.setP_balance( 0l );
			
		}

		return 1;
	}

	@Override
	public long send_A_to_Address_in_B( AT_Machine_State state ) {
		
		AT at = AT.getAT( state.getId() );

		long amount = AT_API_Helper.getLong( state.get_A1() );

		if ( at.getG_balance() > amount )
		{
		
			AT_Transaction tx = new AT_Transaction( state.get_B1() , amount, null );
			state.addTransaction( tx );
			
			state.setG_balance( state.getG_balance() - amount );
			
		}
		else
		{
			AT_Transaction tx = new AT_Transaction( state.get_B1() , at.getG_balance(), null );
			state.addTransaction( tx );
			
			state.setG_balance( 0L );
		}
		return 1;
	}

	public long add_Minutes_to_Timestamp( long val1 , long val2 , AT_Machine_State state) {
		int height = AT_API_Helper.longToHeight( val1 );
		int numOfTx = AT_API_Helper.longToNumOfTx( val1 );
		int addHeight = height + (int) (val2 / AT_Constants.getInstance().AVERAGE_BLOCK_MINUTES( Nxt.getBlockchain().getHeight() ));

		return AT_API_Helper.getLongTimestamp( addHeight , numOfTx );
	}

	@Override
	/* 0x0450 EXT_FUN_DAT_2
	 * airdrop coins to coordinate(B1,B2), sequence= B3,amount = B4
	 */
	public void AirDrop_Coordinate_In_B( long val , long type, AT_Machine_State state ) {

		int height = AT_API_Helper.longToHeight( val );
		int numOfTx = AT_API_Helper.longToNumOfTx( val );

		Long atId = state.getLongId();		
		Long transactionId = 0L;
		Logger.logDebugMessage("get tx after timestamp "+val + " height: "+ height+" atId: "+ atId+ " type "+ type);
		
	    transactionId = findTransactionToAT(startHeight, atId, (int)type);
		if (transactionId != 0) {
		    Logger.logInfoMessage("tx with id "+transactionId+" found");			
		}

		clear_A( state );
		state.set_A1( AT_API_Helper.getByteArray( transactionId ) );
		// can not find a tx
		if (transactionId == 0) {
			state.set_A3( AT_API_Helper.getByteArray( atId ) );
			state.set_A4( AT_API_Helper.getByteArray( AT_API_Helper.getLongTimestamp( startHeight, 0 ) ) );			
		}
	}
	
	//get txs in a block with index > numOfTx
    public static List<Long> findATTransactions(int startHeight ,Long atID, int numOfTx){
    	try (Connection con = Db.db.getConnection();
    			PreparedStatement pstmt = con.prepareStatement("SELECT id FROM transaction WHERE height= ? and recipient_id = ?")){
    		pstmt.setInt(1, startHeight);
    		pstmt.setLong(2, atID);
    		ResultSet rs = pstmt.executeQuery();
    		Long transactionId = 0L;
    		List<Long> transactionIdList = new ArrayList<>();
    		
    		int counter = 1;
    		while (rs.next()) {
                if (counter > numOfTx){
                    transactionId = rs.getLong("id");
                    transactionIdList.add(transactionId);
                }
            }
            rs.close();
            return transactionIdList;
    		
    	} catch (SQLException e) {
    		throw new RuntimeException(e.toString(), e);
    	}
    	
    }

	//get list of txs with conditions and  index > numOfTx
    public static List<Long> findATTransactions(int startHeight, int endHeight, Long atID, int type_subType, int numOfTx){
    	try (Connection con = Db.db.getConnection();
    			PreparedStatement pstmt = con.prepareStatement("SELECT id FROM transaction WHERE height between ? and ? and recipient_id = ? and type*100+subtype = ? ")) {
    		pstmt.setInt(1, startHeight);
    		pstmt.setInt(2, endHeight);    		
    		pstmt.setLong(3, atID);
    		pstmt.setInt(4, type_subType);
    		ResultSet rs = pstmt.executeQuery();
    		Long transactionId = 0L;
    		List<Long> transactionIdList = new ArrayList<>();
    		
    		int counter = 1;
    		while (rs.next()) {
                if (counter > numOfTx){
                    transactionId = rs.getLong("id");
                    transactionIdList.add(transactionId);
                }
            }
            rs.close();
            return transactionIdList;
    		
    	} catch (SQLException e) {
    		throw new RuntimeException(e.toString(), e);
    	}
    	
    }
    
	//get list of txs with conditions and  index > numOfTx
    public static int findATTransactionsCount(int startHeight, int endHeight, Long atID, int type_subType){
    	try (Connection con = Db.db.getConnection();
    			PreparedStatement pstmt = con.prepareStatement("SELECT id FROM transaction WHERE height between ? and ? and recipient_id = ? and type*100+subtype = ? ")) {
    		pstmt.setInt(1, startHeight);
    		pstmt.setInt(2, endHeight);    		
    		pstmt.setLong(3, atID);
    		pstmt.setInt(4, type_subType);
    		ResultSet rs = pstmt.executeQuery();
    		rs.last();
    		return rs.getRow();    		
    	} catch (SQLException e) {
    		throw new RuntimeException(e.toString(), e);
    	}    		
    }
    
    //get the latest tx in in blockchain with height > , and type= ?
    public static Long findTransactionToAT(int startHeight ,Long atID, int transactionType){
    	Long transactionId;
    	try (Connection con = Db.db.getConnection();
    			PreparedStatement pstmt = con.prepareStatement("SELECT top 1 id FROM transaction WHERE height > ? and recipient_id = ? and type= ? order by height desc")){
    		pstmt.setInt(1, startHeight);
    		pstmt.setLong(2, atID);
    		pstmt.setLong(3, transactionType);    		
    		ResultSet rs = pstmt.executeQuery();

    		
    		if (rs.next()) {
                transactionId = rs.getLong("id");
            }
    		else {
        		transactionId = 0L;    			
    		}
    			
            rs.close();
            return transactionId;
    		
    	} catch (SQLException e) {
    		throw new RuntimeException(e.toString(), e);
    	}
    	
    }
    protected static int findTransactionHeight(Long transactionId, int height, Long atID){
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT id FROM transaction WHERE height= ? and recipient_id = ?")){
			pstmt.setInt( 1, height );
			pstmt.setLong( 2, atID );
			ResultSet rs = pstmt.executeQuery();

			int counter = 0;
			while ( rs.next() ) {
				if (rs.getLong( "id" ) == transactionId){
					counter++;
					break;
				}
				counter++;
			}
			rs.close();
			return counter;

		} catch ( SQLException e ) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static void setTimeStampListToNull(){
		if (!timeStampList.isEmpty()) {
			timeStampList.clear();;
		}
	}

	public static void setEndHeight(int lastBlockHeight) {
		endHeight = lastBlockHeight;
	}
}
