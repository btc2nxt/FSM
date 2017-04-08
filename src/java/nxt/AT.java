/*
 * Some portion .. Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.

 * Some portion .. Copyright (c) 2015 FSM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.

 */

package nxt;


import nxt.Account.AccountAsset;
import nxt.at.AT_API_Helper;
import nxt.at.AT_Constants;
import nxt.at.AT_Controller;
import nxt.at.AT_Exception;
import nxt.at.AT_Machine_State;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.EntityDbTable;
import nxt.util.Listener;
import nxt.util.Logger;
import nxt.Account;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AT extends AT_Machine_State implements Cloneable  {

	static {
		Nxt.getBlockchainProcessor().addListener(new Listener<Block>() {
			@Override
			public void notify(Block block) {
				/*try {
					if (block.getBlockATs()!=null)
					{
						LinkedHashMap<Long,byte[]> blockATs = AT_Controller.getATsFromBlock(block.getBlockATs());
						if (!blockATs.isEmpty() && blockATs.keySet().iterator().hasNext()) {
							Long atId =  blockATs.keySet().iterator().next();
							priorityIndex = ringATs.indexOf(atId);
							Logger.logDebugMessage("after popped block AT priority: " + priorityIndex);													
						}
					}
				} catch (AT_Exception e) {
					e.printStackTrace();
				}*/	

			}

		}, BlockchainProcessor.Event.BLOCK_POPPED);		
	}    

	public static class ATState {

	    static void init() {}
	    
		private final long atStateId;
		private final DbKey dbKey;
		private long atId;		
		private short pc;
		private short steps;
		private long timeStamp;
		private long lastStateId;		
        private final byte[] machineCodeUpdate;
        private final byte[] machineDataUpdate;        		

	    private ATState(Transaction transaction, Attachment.AutomatedTransactionsState attachment) {
	        this.atStateId = transaction.getId();
			this.dbKey = atStateDbKeyFactory.newKey(this.atStateId);
	        this.atId = attachment.getATId();			
			this.pc = attachment.getPc();;
			this.steps = attachment.getSteps();
			this.timeStamp = attachment.getTimeStamp();		
			this.lastStateId = attachment.getLastStateId();
			this.machineCodeUpdate = attachment.getMachineCode();;			
			this.machineDataUpdate = attachment.getMachineData();;			
	    }

	    private ATState(ResultSet rs) throws SQLException {
			this.atStateId = rs.getLong("id");			
			this.dbKey = atStateDbKeyFactory.newKey(this.atStateId);
			this.atId = rs.getLong("at_id");			
			this.pc = rs.getShort("pc");
			this.steps = rs.getShort("steps");
			this.timeStamp = rs.getLong("timeStamp");
			this.lastStateId = rs.getLong("last_State_Id");
			this.machineCodeUpdate = rs.getBytes("machinecode");
			this.machineDataUpdate = rs.getBytes("data");			
		}

		private void save(Connection con) throws SQLException {
			try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO at_state (id, at_id, pc, steps, timeStamp, machinecode,data, last_state_id, height, latest) "
					+ "KEY (at_id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
				int i = 0;
	            pstmt.setLong(++i, this.getId());				
				pstmt.setLong(++i, atId);
				pstmt.setInt( ++i , pc);
				pstmt.setInt(++i, steps);
				pstmt.setLong(++i, timeStamp);
				pstmt.setBytes(++i, machineCodeUpdate);
				pstmt.setBytes(++i, machineDataUpdate);				
				pstmt.setLong(++i, lastStateId);				
				pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
				pstmt.executeUpdate();
			}
		}

	    public long getId() {
	        return atStateId;
	    }

		public long getATId() {
			return atId;
		}

		public int getPc() {
			return pc;
		}

		public int getSteps() {
			return steps;
		}
		
		public long getTimeStamp() {
			return timeStamp;
		}
	
		public long getLastStateId() {
			return lastStateId;
		}
		
		public byte[] getMachineCode() { 
			return machineCodeUpdate; 
		}
		
		public byte[] getMachineData() { 
			return machineDataUpdate; 
		}		

		public void setPc(short pc) {
			this.pc = pc;
		}
		
		public void setSteps(short steps) {
			this.steps = steps;
		}
		
		public void setTimeStamp(long timeStamp) {
			this.timeStamp = timeStamp;
		}
		
		public void setLastStateId(long lastStateId) {
			this.lastStateId = lastStateId;
		}	
				
	}

	public static class ATPayment {

	    static void init() {}
	    
		private final DbKey dbKey;
		private long atStateId;		
		private short paymentNo;
		private long recipientId;		
		private long amount;
	
	    /*private ATPayment(Transaction transaction, Attachment.AutomatedTransactionsState attachment) {
	        this.atPaymentId = transaction.getId();
			this.dbKey = atStateDbKeyFactory.newKey(this.atPaymentId);
	        this.atId = attachment.getATId();			
			this.pc = attachment.getPc();;
			this.steps = attachment.getSteps();				
	    }*/
	    
		private ATPayment(long atStateId, short paymentNo, long recipientId, long amount ) {
			this.atStateId = atStateId;			
			this.paymentNo = paymentNo;			
			this.dbKey = atPaymentDbKeyFactory.newKey(this.atStateId, this.paymentNo);
			this.recipientId = recipientId;
			this.amount = amount;
		}
		
		private ATPayment(ResultSet rs) throws SQLException {			
			this.dbKey = atPaymentDbKeyFactory.newKey(this.atStateId, this.paymentNo);
			this.atStateId = rs.getLong("at_state_id");			
			this.recipientId = rs.getShort("recipient_id");
			this.amount = rs.getShort("amount");
		}

		private void save(Connection con) throws SQLException {
			try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO at_payment (at_state_id, payment_no, recipient_id, amount, height, latest) "
					+ "KEY (at_state_id, payment_no) VALUES (?, ?, ?, ?, ?, TRUE)")) {
				int i = 0;			
				pstmt.setLong(++i, atStateId);
				pstmt.setShort(++i, paymentNo);				
				pstmt.setLong( ++i , recipientId);
				pstmt.setLong(++i, amount);
				pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
				pstmt.executeUpdate();
			}
		}

		public long getATStateId() {
			return atStateId;
		}
		
	    public long getPaymentNo() {
	        return paymentNo;
	    }

		public long getRecipientId() {
			return recipientId;
		}	
	}

	private static final DbKey.LongKeyFactory<AT> atDbKeyFactory = new DbKey.LongKeyFactory<AT>("id") {

        @Override
        public DbKey newKey(AT at) {
            return at.dbKey;
        }

    };

    private static final EntityDbTable<AT> atTable = new EntityDbTable<AT>("at", atDbKeyFactory) {

        @Override
        protected AT load(Connection con, ResultSet rs) throws SQLException {
            return new AT(rs);
        }

        @Override
        protected void save(Connection con, AT at) throws SQLException {
            at.save(con);
        }

    };

    private static final DbKey.LongKeyFactory<ATState> atStateDbKeyFactory = new DbKey.LongKeyFactory<ATState>("id") {

        @Override
        public DbKey newKey(ATState atState) {
            return atState.dbKey;
        }

    };

    private static final EntityDbTable<ATState> atStateTable = new EntityDbTable<ATState>("at_state", atStateDbKeyFactory) {

        @Override
        protected ATState load(Connection con, ResultSet rs) throws SQLException {
            return new ATState(rs);
        }

        @Override
        protected void save(Connection con, ATState atState) throws SQLException {
            atState.save(con);
        }

    };

    private static final DbKey.LinkKeyFactory<ATPayment> atPaymentDbKeyFactory = new DbKey.LinkKeyFactory<ATPayment>("at_state_id", "payment_no") {

        @Override
        public DbKey newKey(ATPayment atPayment ) {
            return atPayment.dbKey;
        }

    };
       
    private static final EntityDbTable<ATPayment> atPaymentTable = new EntityDbTable<ATPayment>("at_payment", atPaymentDbKeyFactory) {

        @Override
        protected ATPayment load(Connection con, ResultSet rs) throws SQLException {
            return new ATPayment(rs);
        }

        @Override
        protected void save(Connection con, ATPayment atPayment) throws SQLException {
            atPayment.save(con);
        }

    };

    static void addATState(Transaction transaction, Attachment.AutomatedTransactionsState attachment) {
        atStateTable.insert(new ATState(transaction, attachment));
    }
    
    static void addATPayment(long atStateId, short paymentNo, long recipientId, long amount ) {
        atPaymentTable.insert(new ATPayment( atStateId,  paymentNo,  recipientId,  amount ) );
    }

    
    public static DbIterator<AT> getAllATs(int from, int to) {
        return atTable.getAll(from, to);
    }

    public static int getCount() {
        return atTable.getCount();
    }

    public static int getATCount(long atId) {
        return atTable.getCount(new DbClause.LongClause("id", atId));
    }

    public static AT getAT(long id) {
        return atTable.get(atDbKeyFactory.newKey(id));
    }
    
	public static AT getAT(byte[] id) {
        return atTable.get(atDbKeyFactory.newKey(AT_API_Helper.getLong(id)));
	}    

    public static DbIterator<AT> getATsIssuedBy(long accountId, int from, int to) {
        return atTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }

    public static DbIterator<AT> searchATs(String query, int from, int to) {
        return atTable.search(query, DbClause.EMPTY_CLAUSE, from, to);
    }

    public DbIterator<ATState> getATStates(int from, int to) {
        return atStateTable.getManyBy(new DbClause.LongClause("at_id", AT_API_Helper.getLong(this.atId)), from, to);
    }
    
    private DbClause getStateUpdateClause() {
        return new DbClause(" at_id = ? AND data <> ? ") {
            @Override
            public int set(PreparedStatement pstmt, int index) throws SQLException {
                pstmt.setLong(index++, AT_API_Helper.getLong(getId()));
                pstmt.setString(index++, "");
                return index;
            }
        };
    }

    public  DbIterator<ATState> getATStateUpdates(int from, int to) {
        return atStateTable.getManyBy(getStateUpdateClause(),from, to);
    }

    public ATState getATState(long atStateId) {
        return atStateTable.get(atStateDbKeyFactory.newKey(atStateId));
    }
    
    
    protected int getPreviousBlock() {
		return this.previousBlock;
	}

	static void addAT(Long atId, Long senderAccountId, String name, String description, byte[] machineCode ,byte[] machineData ,byte[] creationBytes , int height) {

		ByteBuffer bf = ByteBuffer.allocate( 8 + 8 );
		bf.order( ByteOrder.LITTLE_ENDIAN );

		bf.putLong( atId );

		byte[] id = new byte[ 8 ];

		bf.putLong( 8 , senderAccountId );

		byte[] creator = new byte[ 8 ];
		bf.clear();
		bf.get( id , 0 , 8 );
		bf.get( creator , 0 , 8);
		AT at = new AT( id , creator , name , description , machineCode, machineData, creationBytes , height );

		AT_Controller.resetMachine(at);
		
		atTable.insert(at);		
		Account account = Account.addOrGetAccount(atId);
		account.apply(new byte[32], height);
		Logger.logDebugMessage("add new AT");		
	}

	private void setPreviousBlock(int height) {
		this.previousBlock = height;

	}

	static void removeAT(long atId) {
		Logger.logDebugMessage("remove AT from ringATs error, no AT with id: " + atId);			
	}

	static boolean isATAccountId(long atId) {
		return getATCount(atId) == 1; 
	}

    static void init() {
        ATState.init();
        ATPayment.init();    	
    }
    
	private final DbKey dbKey;	
	private final String name;    
	private final String description;
	private int previousBlock;	

	private AT( byte[] atId , byte[] creator , String name , String description , byte[] machineCode, byte[] machineData, byte[] creationBytes , int height ) {
		super( atId , creator , machineCode, machineData, creationBytes , height );
		dbKey = atDbKeyFactory.newKey(AT_API_Helper.getLong(atId));		
		this.name = name;
		this.description = description;
		this.previousBlock = 0;

	}
	
    private AT(ResultSet rs) throws SQLException {
    	super(rs);
        this.dbKey = atDbKeyFactory.newKey(AT_API_Helper.getLong(this.getId()));
        this.name = rs.getString("name");
        this.description = rs.getString("description");
    }	

	private void save(Connection con)
	{
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO at (id, account_id, name, "
                + "description, version, machinecode, data, delay_blocks, freeze_when_same_balance, "
        		+ "sleep_between, start_block, Properties, height) "
        		+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong( ++i , AT_API_Helper.getLong( this.getId() ) );
			pstmt.setLong( ++i, AT_API_Helper.getLong( this.getCreator() ) );
			DbUtils.setString( pstmt , ++i , this.getName() );
			DbUtils.setString( pstmt , ++i , this.getDescription() );
			pstmt.setShort( ++i , this.getVersion() );
			DbUtils.setBytes( pstmt , ++i , this.getAp_code().array() );
			DbUtils.setBytes( pstmt , ++i , this.getAp_data().array() );			
			pstmt.setInt( ++i , this.getDelayBlocks() );
			pstmt.setBoolean( ++i , this.getFreezeWhenSameBalance() );
			pstmt.setInt( ++i , this.getSleepBetween() );
			pstmt.setInt( ++i , this.getStartBlock() );
			DbUtils.setString( pstmt , ++i , this.getProperties() );						
			pstmt.setInt( ++i , Nxt.getBlockchain().getHeight() );

			pstmt.executeUpdate();
				}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

	}
	
    public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

}
