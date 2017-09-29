package nxt.at;

import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.json.simple.JSONArray;

import nxt.AT;
import nxt.AT.ATState;
import nxt.Account;
import nxt.Attachment;
import nxt.Block;
import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Generator;
import nxt.Nxt;
import nxt.NxtException;
import nxt.NxtException.NotValidException;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Logger;

public final class AT_Controller {

	static {
		Nxt.getBlockchainProcessor().addListener(new Listener<Block>() {
			@Override
			public void notify(Block block) {
                try{
				for (AT_Controller vm : vms.values()) {
                	if (vm.getATId() == 0 )
                		vm.runCreatorATs(block.getHeight(), vm.getAccountId(), vm.getSecretPhrase(), vm.getATId());
                	else
                		vm.runForAnyoneAT(block.getHeight(), vm.getAccountId(), vm.getSecretPhrase(), vm.getATId());                		
                }
                }
				catch ( NxtException.ValidationException e )
				{
					//should not reach ever here
					e.printStackTrace();
				}				
				finally {

				}
                
				try {
					runSystemATs(block.getHeight());
				} catch (NotValidException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}, BlockchainProcessor.Event.BLOCK_PUSHED);//BLOCK_GENERATED);
	};
	
	private static volatile boolean timeStampRetrieved;
    private static final ConcurrentMap<String, AT_Controller> vms = new ConcurrentHashMap<>();
    private static final Collection<AT_Controller> allVMs = Collections.unmodifiableCollection(vms.values());
	
	
	public static int runSteps( AT_Machine_State state, Account executor )
	{
		state.getMachineState().finished = false;
		state.getMachineState().steps = 0;
		state.getAp_code().clear();		
		//an AT has run
		timeStampRetrieved = false;
		AT_API_Platform_Impl.setTimeStampListToNull();

		AT_Machine_Processor processor = new AT_Machine_Processor( state );

		int height = Nxt.getBlockchain().getHeight();
		state.setLastRanBlock(height);

		while ( state.getMachineState().steps < (AT_Constants.getInstance().MAX_STEPS( height )) )
		{
			if ( ( executor.getUnconfirmedBalanceNQT() < AT_Constants.getInstance().STEP_FEE( height )) )
			{
				Logger.logDebugMessage( "stopped - not enough balance" );
				return 3;
			}

			state.getMachineState().steps++;
			int rc = processor.processOp( false , false );
			
			/*
			 * codeBlock must run at once, otherwise rollback BLK_SET, then terminate runSteps
			 * BLK_SET 0 is end of the block
			 */
			if (state.getMachineState().codeBlockSteps > 0 && !state.getMachineState().inCodeBlock) {
				if (state.getMachineState().steps +state.getMachineState().codeBlockSteps < (AT_Constants.getInstance().MAX_STEPS( height ) )) {
					state.getMachineState().inCodeBlock = true;
				} 
				else {
					state.getMachineState().steps--;
					state.getMachineState().pc = state.getMachineState().pc -2;	
					break;
				}
			}
			if (state.getMachineState().codeBlockSteps == 0 && state.getMachineState().inCodeBlock) {
				state.getMachineState().inCodeBlock = false;				
			}

			if ( rc >= 0 )
			{
				if ( state.getMachineState().stopped )
				{
					//TODO add freeze
					Logger.logDebugMessage( "stopped" );
					return 2;
				}
				else if ( state.getMachineState().finished )
				{
					Logger.logDebugMessage( "finished" );
					return 1;
				}
			}
			else
			{
				if ( rc == -1 )
					Logger.logDebugMessage( "error: overflow" );
				else if ( rc==-2 )
					Logger.logDebugMessage( "error: invalid code" );
				else
					Logger.logDebugMessage( "unexpected error" );
				return 0;
			}
		}
		return 5;
	}

	public static void resetMachine( AT_Machine_State state ) {
		state.getMachineState( ).reset( );
		listCode( state , true , true );
		//listCode( state , true , false );	show machine code in console
	}

	public static void listCode( AT_Machine_State state , boolean disassembly , boolean determine_jumps ) {

		AT_Machine_Processor machineProcessor = new AT_Machine_Processor( state );

		int opc = state.getMachineState().pc;
		int osteps = state.getMachineState().steps;

		state.getAp_code().order( ByteOrder.LITTLE_ENDIAN );
		state.getAp_data().order( ByteOrder.LITTLE_ENDIAN );

		state.getMachineState( ).pc = 0;
		state.getMachineState( ).opc = opc;

		while ( true )
		{
			int rc= machineProcessor.processOp( disassembly , determine_jumps );
			if ( rc<=0 ) break;

			state.getMachineState().pc += rc;
		}

		state.getMachineState().steps = osteps;
		state.getMachineState().pc = opc;
	}

	public static int checkCreationBytes( byte[] machineCode, byte[] machineData, byte[] creation , int height ) throws AT_Exception{
		int totalPages = 0;
		try 
		{
			AT_Constants instance = AT_Constants.getInstance();
			int pageSize = ( int ) instance.PAGE_SIZE( height );
			
			int codePages = (int)Math.ceil((float)machineCode.length / pageSize) ;
			if ( codePages > instance.MAX_MACHINE_CODE_PAGES( height ) )
			{
				throw new AT_Exception( AT_Error.INCORRECT_CODE_PAGES.getDescription() );
			}

			int dataPages = (int)Math.ceil((float)machineData.length / pageSize) ;
			if ( dataPages > instance.MAX_MACHINE_DATA_PAGES( height ) )
			{
				throw new AT_Exception( AT_Error.INCORRECT_DATA_PAGES.getDescription() );
			}
			
			ByteBuffer b = ByteBuffer.allocate( creation.length );
			b.order( ByteOrder.LITTLE_ENDIAN );
			
			b.put(  creation );
			b.clear();

			short version = b.getShort();
			if ( version > instance.AT_VERSION( height ) )
			{
				throw new AT_Exception( AT_Error.INCORRECT_VERSION.getDescription() );
			}

			short reserved = b.getShort(); //future: reserved for future needs

			/*short callStackPages = b.getShort();
			if ( callStackPages > instance.MAX_MACHINE_CALL_STACK_PAGES( height ) )
			{
				throw new AT_Exception( AT_Error.INCORRECT_CALL_PAGES.getDescription() );
			}

			short userStackPages = b.getShort();
			if ( userStackPages > instance.MAX_MACHINE_USER_STACK_PAGES( height ) )
			{
				throw new AT_Exception( AT_Error.INCORRECT_USER_PAGES.getDescription() );
			}
			
			totalPages = codePages + dataPages + userStackPages + callStackPages;
			Logger.logDebugMessage("codePages: " + codePages + "totalpage: "+ totalPages);			
			*/
			//TODO note: run code in demo mode for checking if is valid

		} catch ( BufferUnderflowException e ) 
		{
			throw new AT_Exception( AT_Error.INCORRECT_CREATION_TX.getDescription() );
		}
		return totalPages;
	}
	
    public static AT_Controller startATVirtualMachine(String secretPhrase, long atId) {
    	AT_Controller vm = new AT_Controller(secretPhrase, atId);
    	AT_Controller old = vms.putIfAbsent(secretPhrase, vm);
        if (old != null) {
            Logger.logDebugMessage("Account " + Convert.toUnsignedLong(old.getAccountId()) + " is already forging");
            return old;
        }
        Logger.logDebugMessage("Account " + Convert.toUnsignedLong(vm.getAccountId()) + " AT virtual machine started");
        return vm;
    }
	
	/*
	 * decide AT will run or not
	 * after runsteps, will generate fee txs
	 */
			
	public static int getATResult(AT at,Long atId, int currentBlockHeight, int orderedATHeight, Account account ) {

		/*ConcurrentSkipListMap< Integer , byte[] > blockATBytesAtHeights = at.getBlockATBytesAtHeights();		
		/*
		 * handle pop block
		 *
		if (at.getLastRanBlock() >= currentBlockHeight) {
			Logger.logDebugMessage("Block is popped to height "+currentBlockHeight+ " from height: "+ at.getLastRanBlock() );
			if (blockATBytesAtHeights.containsKey(currentBlockHeight - 1 )){
				at.setStateAndData(blockATBytesAtHeights.get(currentBlockHeight - 1) );
			}
		}*/
		
		//start when reach a setting block which is set in createion of FSM
		if ( at.getStartBlock() > currentBlockHeight)
		{
			return 0;
		}
		
		long atAccountBalance = getATAccountBalance( atId );
		long atStateBalance = at.getG_balance() + at.getStepsFeeNQT();

		AT_API_Platform_Impl.setEndHeight(currentBlockHeight-1);
		
		//AT's balance doesnt change, skip
		if ( at.getFreezeWhenSameBalance() && atAccountBalance == atStateBalance )
		{
			return 0;
		}

		//update g_balance, because of new tx sending money to AT's account
		if (atAccountBalance != atStateBalance) {
			at.setG_balance( atAccountBalance - at.getStepsFeeNQT());
		}

		if ( ( at.getDelayBlocks() == 0 || at.getDelayBlocks() > 0 && currentBlockHeight - at.getCreationBlockHeight() >= at.getDelayBlocks()) )
		{
			Logger.logDebugMessage("AT is running"+ atId.toString());					
			at.clearTransactions();
			//waiting for blocks after create an AT 
			if (at.getDelayBlocks() > 0) {
				at.setDelayBlocks( 0 );				
			}
			
			runSteps ( at, account );
			
			/*save at state to blockatbytesATHeights
			at.setLastRanBlock(currentBlockHeight);				
			if (!blockATBytesAtHeights.containsKey(currentBlockHeight)){
				blockATBytesAtHeights.put(currentBlockHeight,at.getBytesWithoutTransactions());

				if (blockATBytesAtHeights.containsKey( currentBlockHeight - AT_Constants.AT_POP_BLOCK))
				{
					blockATBytesAtHeights.remove(currentBlockHeight - AT_Constants.AT_POP_BLOCK);
				}
				
			}*/						
			
			return getCostOfOneAT();			
		}
		else
			return 0;
	}
	
	/*
	 * atId = 0: only run account's ATs
	 * >0 : only run the AT which must be with RunType=FOR_ANYONE or which is the account's AT, but isn't SYSTEM_AT.  
	 */
	public static int runCreatorATs( int blockHeight, long accountId, String secretPhrase, long atId) throws NotValidException{

		int atCost;
		int totalSteps = 0; 
		long lastStateId =0L;
		int lastRanHeight = 0;
		
		Logger.logDebugMessage("ATs of creator will be  running");
		int orderedATHeight = 0;
		Account account = Account.getAccount(accountId);
		
		try (DbIterator<AT> ats = AT.getATsIssuedBy(accountId, 0, 10))	{
		while ( ats.hasNext() && account.getUnconfirmedBalanceNQT() > AT_Constants.getInstance().MAX_STEPS(blockHeight) * Constants.AUTOMATED_TRANSACTIONS_STEP_COST_NQT)
		{
			/*load AT machine code, get state from AT_State
			 * reset machine_state
			 * add machineState.jumps(call listCode(at , true , true )) 	
			 */
			AT at = ats.next();
			listCode( at , true , true );
			
			Logger.logDebugMessage("atId " + AT_API_Helper.getLong(at.getId()));
            try (DbIterator<AT.ATState> atStates = at.getATStates(0, 1)) {
                if (atStates.hasNext()) {
                   AT.ATState atState = atStates.next(); 
                   //the AT has stopped
                   lastRanHeight = atState.getLastRanHeight();
                   if (atState.getPc() < 0 || at.getSleepBetween() < blockHeight - lastRanHeight )  
                	   continue;
                   
                   at.getMachineState().pc = atState.getPc(); 
                   long timeStamp = atState.getTimeStamp();
                   lastStateId = atState.getId();
                   Logger.logDebugMessage("height,pc " + atState.getTimeStamp()+ " "+at.getMachineState().pc);
                   
                   //load update of FSM
                   try (DbIterator<AT.ATState> atStateUpdates = at.getATStateUpdates(0, 1)) {                   
                   if ( atStateUpdates.hasNext()) {
                	   atState = atStateUpdates.next();
                       if (atState.getMachineData() != null) {
                       	at.setAp_data(atState.getMachineData());                		
                       }                	
                    }
                   }
                   /*
                    * when send a message to FSM, the message will rewrite all data area,
                    * and timestamp is on $address 0 
                    * so timestamp must be set after the rewriting
                    */
                   at.setTimeStamp(timeStamp);
                }
                else {
                	at.setTimeStamp(AT_API_Helper.getLongTimestamp(at.getCreationBlockHeight(),0));                	
                	Logger.logDebugMessage("AT doesn't have AT_State record, set timestamp to createion height " );                	
                }              	
            }

			at.setLastRanSteps(totalSteps);
			//if have txs, must update payload 				
			atCost =getATResult(at,AT_API_Helper.getLong(at.getId()),blockHeight, orderedATHeight,account );
			//generate a AT_State tx
			if (atCost >0 ) {
				try {
					List<AT_Transaction> atTransactions = at.getTransactions();				
					//Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactions,secretPhrase, AT_API_Helper.getLong(at.getId()),(short)at.getMachineState().pc,(short)at.getMachineState().steps,at.getMachineState().timeStamp,lastStateId);
					Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactions,secretPhrase, at, lastStateId, lastRanHeight);					
					transaction.validate();
					transaction.sign(secretPhrase);
                    Nxt.getTransactionProcessor().broadcast(transaction);
                    Logger.logDebugMessage("FSM payment transactions success");                    
                
                //Long Id = transaction.getId();
                //Logger.logDebugMessage("new transaction id " + Id);
				}
				catch ( NxtException.ValidationException e )
				{
					//should not reach ever here
					e.printStackTrace();
				}				
				finally {
					
				}
			}
			
			if (totalSteps >= AT_Constants.getInstance().MAX_STEPS( blockHeight )) {
				break;
			}
		}
        }

		return totalSteps;
	}

	/*
	 * atId : 1..MAX_AUTOMATED_TRANSACTION_SYSTEM  
	 */
	public static int runSystemATs( int blockHeight) throws NotValidException{

		int atCost;
		int totalSteps = 0;
		long lastStateId =0L;
		int lastRanHeight = 0;
		
		Logger.logDebugMessage("System ATs will be  running");
		int orderedATHeight = 0;
		
		try (DbIterator<AT> ats = AT.getSystemATs(Constants.MAX_AUTOMATED_TRANSACTION_SYSTEM))	{
		while ( ats.hasNext() )
		{
			/*load AT machine code, get state from AT_State
			 * reset machine_state
			 * add machineState.jumps(call listcode) 	
			 */
			AT at = ats.next();
			Account account = Account.getAccount(at.getLongId());
			if (account.getUnconfirmedBalanceNQT() < AT_Constants.getInstance().MAX_STEPS(blockHeight) * Constants.AUTOMATED_TRANSACTIONS_STEP_COST_NQT
					|| at.getStartBlock() > blockHeight || at.getDelayBlocks() >= blockHeight - at.getCreationBlockHeight())
				continue;
			
			listCode( at , true , true );
			
			Logger.logDebugMessage("atId " + AT_API_Helper.getLong(at.getId()));
            try (DbIterator<AT.ATState> atStates = at.getATStates(0, 1)) {
                if (atStates.hasNext()) {
                   AT.ATState atState = atStates.next(); 
                   //the AT has stopped || sleepbetween
                   lastRanHeight = atState.getLastRanHeight();
                   if (atState.getPc() < 0 || at.getSleepBetween() > blockHeight - lastRanHeight ) 
                	   continue;
                   
                   at.getMachineState().pc = atState.getPc(); 
                   long timeStamp = atState.getTimeStamp();
                   lastStateId = atState.getId();
                   Logger.logDebugMessage("height,pc " + atState.getTimeStamp()+ " "+at.getMachineState().pc);
                   
                   //load update of FSM
                   try (DbIterator<AT.ATState> atStateUpdates = at.getATStateUpdates(0, 1)) {                   
                   if ( atStateUpdates.hasNext()) {
                	   atState = atStateUpdates.next();
                       if (atState.getMachineData() != null) {
                       	at.setAp_data(atState.getMachineData());                		
                       }                	
                    }
                   }
                   /*
                    * when send a message to FSM, the message will rewrite all data area,
                    * and timestamp is on $address 0 
                    * so timestamp must be set after the rewriting
                    */
                   at.setTimeStamp(timeStamp);
                }
                else {
                	at.setTimeStamp(AT_API_Helper.getLongTimestamp(at.getCreationBlockHeight(),0));                	
                	Logger.logDebugMessage("AT doesn't have AT_State record, set timestamp to createion height " );                	
                }              	
            }

			at.setLastRanSteps(totalSteps);
			//if have txs, must update payload 				
			atCost =getATResult(at,AT_API_Helper.getLong(at.getId()),blockHeight, orderedATHeight,account );
			//generate a AT_State tx
			String atSecretPhrase = "SIGNED_BY_SYSMTEM_AT" + AT_API_Helper.getLong(at.getId());
			lastRanHeight = blockHeight;
			
			if (atCost >0 ) {
				try {
				//if (AT_API_Helper.getLong(at.getId()) != Constants.GAME_PREDISTRIBUTE_FSM_ID) {
					List<AT_Transaction> atTransactions = at.getTransactions();				
					//Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactions,secretPhrase, AT_API_Helper.getLong(at.getId()),(short)at.getMachineState().pc,(short)at.getMachineState().steps,at.getMachineState().timeStamp,lastStateId);
					Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactions, atSecretPhrase, at, lastStateId, lastRanHeight);					
					transaction.validate();
					transaction.sign(atSecretPhrase);
                    Nxt.getTransactionProcessor().broadcast(transaction);
                    Logger.logDebugMessage("FSM transactions broadcast succeed");
				//}
				/*else {
					Iterator<AT_Transaction> atTransactions = at.getTransactions().iterator();
					List<AT_Transaction> atTransactionsTmp = new ArrayList<AT_Transaction>();
					while ( atTransactions.hasNext() ) {
						if (!atTransactionsTmp.isEmpty())
							atTransactionsTmp.clear();
						atTransactionsTmp.add(atTransactions.next());
						Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactionsTmp, atSecretPhrase, at, lastStateId, lastRanHeight);					
						transaction.validate();
						transaction.sign(atSecretPhrase);
	                    Nxt.getTransactionProcessor().broadcast(transaction);
	                    Logger.logDebugMessage("FSM predistribute transaction success. ");
	                    //break;//test use
					}
					
				}*/
                
                //Long Id = transaction.getId();
                //Logger.logDebugMessage("new transaction id " + Id);
				}
				catch ( NxtException.ValidationException e )
				{
					//should not reach ever here
					e.printStackTrace();
				}				
				finally {
					
				}
			}
					
			if (totalSteps >= AT_Constants.getInstance().MAX_STEPS( blockHeight )) {
				break;
			}
		}
        }

		return totalSteps;
	}
	
	public static int runForAnyoneAT( int blockHeight, long accountId, String secretPhrase, long atId) throws NotValidException{

		int atCost;
		int totalSteps = 0; 
		long lastStateId =0L;
		int lastRanHeight = 0;
		
		Logger.logDebugMessage("For Anyone AT will be running");
		int orderedATHeight = 0;
		Account account = Account.getAccount(accountId);
		AT at = AT.getAT(atId);		
		
		if ( (at.getRunType()== AT.ATRunType.FOR_ANYONE || at.getRunType()== AT.ATRunType.CREATEOR_ONLY && AT_API_Helper.getLong(at.getCreator()) == accountId ) 
				&& account.getUnconfirmedBalanceNQT() > AT_Constants.getInstance().MAX_STEPS(blockHeight) * Constants.AUTOMATED_TRANSACTIONS_STEP_COST_NQT)
		{
			/*load AT machine code, get state from AT_State
			 * reset machine_state
			 * add machineState.jumps(call listcode) 	
			 */

			listCode( at , true , true );
			
			Logger.logDebugMessage("atId " + AT_API_Helper.getLong(at.getId()));
            try (DbIterator<AT.ATState> atStates = at.getATStates(0, 1)) {
                if (atStates.hasNext()) {
                   AT.ATState atState = atStates.next();
                   lastRanHeight = atState.getLastRanHeight();
                   if (atState.getPc() < 0 || at.getSleepBetween() < blockHeight - lastRanHeight ) 
                	   return 0;                   
                   
                   at.getMachineState().pc = atState.getPc();
                   long timeStamp = atState.getTimeStamp();
                   lastStateId = atState.getId();
                   Logger.logDebugMessage("height,pc " + atState.getTimeStamp()+ " "+at.getMachineState().pc);
                   
                   //load update of FSM
                   try (DbIterator<AT.ATState> atStateUpdates = at.getATStateUpdates(0, 1)) {                   
                   if ( atStateUpdates.hasNext()) {
                	   atState = atStateUpdates.next();
                       if (atState.getMachineData() != null) {
                       	at.setAp_data(atState.getMachineData());                		
                       }                	
                    }
                   }
                   /*
                    * when send a message to FSM, the message will rewrite all data area,
                    * and timestamp is on $address 0 
                    * so timestamp must be set after the rewriting
                    */
                   at.setTimeStamp(timeStamp);
                }
                else {
                	at.setTimeStamp(AT_API_Helper.getLongTimestamp(at.getCreationBlockHeight(),0));                	
                	Logger.logDebugMessage("AT doesn't have AT_State record, set timestamp to createion height " );                	
                }              	
            }

			at.setLastRanSteps(totalSteps);
			//if have txs, must update payload 				
			atCost =getATResult(at,AT_API_Helper.getLong(at.getId()),blockHeight, orderedATHeight,account );
			//generate a AT_State tx
			if (atCost >0 ) {
				try {
					List<AT_Transaction> atTransactions = at.getTransactions();				
					//Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactions,secretPhrase, AT_API_Helper.getLong(at.getId()),(short)at.getMachineState().pc,(short)at.getMachineState().steps,at.getMachineState().timeStamp,lastStateId);
					Transaction transaction = Nxt.getTransactionProcessor().parseTransaction(atTransactions,secretPhrase, at, lastStateId, lastRanHeight);					
					transaction.validate();
					transaction.sign(secretPhrase);
                    Nxt.getTransactionProcessor().broadcast(transaction);
                    Logger.logDebugMessage("FSM payment transactions success");                    
                
                //Long Id = transaction.getId();
                //Logger.logDebugMessage("new transaction id " + Id);
				}
				catch ( NxtException.ValidationException e )
				{
					//should not reach ever here
					e.printStackTrace();
				}				
				finally {
					
				}
			}
			
		}

		return totalSteps;
	}
	
	public static boolean validateATs( Transaction tx, int runBlockHeight ) {

        Attachment.AutomatedTransactionsState attachment = (Attachment.AutomatedTransactionsState) tx.getAttachment();                
        List<AT_Transaction> atPayments = attachment.getATPayments();
        //Account atAccount = Account.getAccount(attachment.getATId());
		
		AT at = AT.getAT(attachment.getATId());
		listCode(at, true,true);
		Logger.logDebugMessage("Validating FSM receieved transaction with atId " + AT_API_Helper.getLong(at.getId()));
        AT.ATState atLastState = at.getATState(attachment.getLastStateId());
        int atCost = 0;
        if (atLastState != null) {
        	at.getMachineState().pc = atLastState.getPc();
            long timeStamp = atLastState.getTimeStamp();
            
            //load update of FSM
            try (DbIterator<AT.ATState> atStateUpdates = at.getATStateUpdates(0, 1)) {                   
            if ( atStateUpdates.hasNext()) {
            	atLastState = atStateUpdates.next();
                if (atLastState.getMachineData() != null) {
                	at.setAp_data(atLastState.getMachineData());                		
                }                	
             }
            }
            at.setTimeStamp(timeStamp);            
        }
        else if (attachment.getLastStateId() == 0) {
        	at.setTimeStamp(AT_API_Helper.getLongTimestamp(at.getCreationBlockHeight(),0));        	
        }
        
        Account sender = Account.getAccount(tx.getSenderId());
		atCost =getATResult(at,AT_API_Helper.getLong(at.getId()),runBlockHeight, 0, sender );
		
        /* compare the two at_transactions
         return (atCost >0 && at.getTransactions(). equals(atPayments));
         strange: ATTransaction.modcount of two objects aren't same, others are same ???
         *should compare everything in the transaction
         */
        if (atCost >0 && at.getTransactions().size() == atPayments.size() ) {
        	int i=0; 
			for (AT_Transaction atTx : at.getTransactions()) {
				//if (!atTx.equals(atPayments.get(i++))) {
				if (atTx.getAmount() != atPayments.get(i).getAmount()) {
					return false;
				}
				if (atTx.getRecipientIdLong() != atPayments.get(i++).getRecipientIdLong()) {
					return false;
				}	    			
				
			} 
			return true;
        }
        else 
        	return false;
        
	}

	public static LinkedHashMap< Long , byte[] > getATsFromBlock( byte[] blockATs ) throws AT_Exception
	{
		if ( blockATs.length > 0 )
		{
			if ( blockATs.length % (getCostOfOneAT() ) != 0 )
			{
				throw new AT_Exception("blockATs must be a multiple of cost of one AT ( " + getCostOfOneAT() +" )" );
			}
		}
		
		ByteBuffer b = ByteBuffer.wrap( blockATs );
		b.order( ByteOrder.LITTLE_ENDIAN );

		byte[] temp = new byte[ AT_Constants.AT_ID_SIZE ];
		byte[] md5 = new byte[ 16 ];

		//LinkedHashMap< byte[] , byte[] > ats = new LinkedHashMap<>();
		LinkedHashMap< Long , byte[] > ats = new LinkedHashMap<>();

		while ( b.position() < b.capacity() )
		{
			b.get( temp , 0 , temp.length );
			b.get( md5 , 0 , md5.length );			
			ats.put( AT_API_Helper.getLong(temp) , md5.clone() );			
		}
		
		if ( b.position() != b.capacity() )
		{
			throw new AT_Exception("bytebuffer not matching");
		}
		
		return ats;
	}

	private static byte[] getBlockATBytes(List<AT> processedATs , int payload ) throws NoSuchAlgorithmException {

		ByteBuffer b = ByteBuffer.allocate( payload );
		b.order( ByteOrder.LITTLE_ENDIAN );
		byte[] md5 = new byte[ getCostOfOneAT() ];
		
		MessageDigest digest = MessageDigest.getInstance( "MD5" );
		for ( AT at : processedATs )
		{
			b.put( at.getId() );
			md5 = digest.digest( at.getBytes() );			
			//digest.update( at.getBytes() );
			//b.put( digest.digest() );
			b.put(md5);
		}

		return b.array();
	}

	private static int getCostOfOneAT() {
		return AT_Constants.AT_ID_SIZE + 16;
	}
	
	//platform based implementations
	//platform based 
	private static long makeTransactions( AT at ) {
		long totalAmount = 0;
		for (AT_Transaction tx : at.getTransactions() )
		{
			totalAmount += tx.getAmount();
			Logger.logInfoMessage("Transaction to " + Convert.toUnsignedLong(AT_API_Helper.getLong(tx.getRecipientId())) + " amount " + tx.getAmount() );
			
		}
		return totalAmount;
	}
	
	//platform based
	private static long getATAccountBalance( Long id ) {
		//Long accountId = AT_API_Helper.getLong( id );
		Account atAccount = Account.getAccount( id );

		if ( atAccount != null )
		{
			return atAccount.getBalanceNQT();
		}

		return 0;

	}

	public static boolean getTimeStampRetrieved(){
		return timeStampRetrieved;
	}

	public static void setTimeStampRetrieved(boolean retrievedTimeStamp1){
		timeStampRetrieved = retrievedTimeStamp1;
	}

    private final long accountId;
    private final long atId;
    private final String secretPhrase;
    private final byte[] publicKey;

    private AT_Controller(String secretPhrase, long atId) {
        this.secretPhrase = secretPhrase;
        this.atId = atId;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.accountId = Account.getId(publicKey);
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getAccountId() {
        return accountId;
    }
    
    public String getSecretPhrase() {
        return secretPhrase;
    }
    
    public long getATId() {
        return atId;
    }
       
}
