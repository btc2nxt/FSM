package nxt.at;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class AT_Machine_State
{

	public class Machine_State 
	{
		transient boolean running;
		transient boolean stopped;
		transient boolean finished;

		int pc;
		int pcs;

		transient int opc;

		int steps;
		int codeBlockSteps;
		boolean inCodeBlock;
		long timeStamp;
        byte[] machineCodeUpdate;
        byte[] machineDataUpdate;        		
		

		private byte[] A1 = new byte[ 8 ];
		private byte[] A2 = new byte[ 8 ];
		private byte[] A3 = new byte[ 8 ];
		private byte[] A4 = new byte[ 8 ];

		private byte[] B1 = new byte[ 8 ];
		private byte[] B2 = new byte[ 8 ];
		private byte[] B3 = new byte[ 8 ];
		private byte[] B4 = new byte[ 8 ];

		byte[] flags = new byte[ 4 ];

		TreeSet<Integer> jumps = new TreeSet<Integer>();

		Machine_State()
		{
			pcs=0;
			reset();
		}

		boolean isRunning()
		{
			return running;
		}

		void reset()
		{
			pc = pcs;
			opc = 0;
			steps = 0;
			codeBlockSteps =0;
			inCodeBlock = false;
			timeStamp = 0;
			machineCodeUpdate = null;
			machineDataUpdate = null;
			if ( !jumps.isEmpty() )
				jumps.clear();
			stopped = false;
			finished = false;
		}

		void run()
		{
			running = true;
		}
		
		public int getSteps()
		{
			return steps;
		}

		public int getPc()
		{
			return pc;
		}
		
		public long getTimeStamp()
		{
			timeStamp = ap_data.getLong(0);		

			return timeStamp;
		}
		
		public byte[] getMachineCodeUpdate()
		{
			return machineCodeUpdate;
		}
	
		public byte[] getMachineDataUpdate()
		{
			return machineDataUpdate;
		}		
		
		protected byte[] getMachineStateBytes()
		{

			int size = 4 + 1 + 4 + 4 + 4 + 4 + 4;
			ByteBuffer bytes = ByteBuffer.allocate(size);
			bytes.order( ByteOrder.LITTLE_ENDIAN );
			bytes.put( flags );
			bytes.put( ( byte ) ( machineState.running == true ? 1 : 0 ) );
			bytes.putInt( machineState.pc );
			bytes.putInt( machineState.pcs );
			bytes.putInt( machineState.steps );
			return bytes.array();
		}
	}

	private short version;

	private long g_balance;
	private long p_balance;  // can use for temporary balance e.g. in lottery case

	private Machine_State machineState;

	private int csize;
	private int dsize;

	public byte[] atId = new byte[ AT_Constants.AT_ID_SIZE ];
	private byte[] creator = new byte[ AT_Constants.AT_ID_SIZE ];

	private long minimumFee;
	private int creationBlockHeight;
	private int delayBlocks;
	private boolean freezeWhenSameBalance;
	private int sleepBetween;	
	private volatile int runBlock;  //when block reaches this block, AT will continue.  
	private volatile long stepsFeeNQT;  //an AT should pay steps fee when run one time
	private volatile int retrievedHeight;  //last call 0x0304 function	
	// first active at the blockHeight	
	private int startBlock;
	private volatile int lastRanBlock;  //AT ran at last block.
	private static int lastRanSteps;  //ATs has ran steps before the next AT.	
	
	/*
	 * <height,blockATbytes> for pop AT blocks, because AT doesn't know previous blcokATs
	 * maximized blockATbytes is 720 blocks
	 */
	private ConcurrentSkipListMap< Integer , byte[] > blockATBytesAtHeights = new ConcurrentSkipListMap<>();		

	private transient ByteBuffer ap_data;
	private transient ByteBuffer ap_code;
	private int constBytes; //must be a multiple of 8, because an address or a value is 8 bytes.
	private int varBytes;

	private List<AT_Transaction> transactions;

    public AT_Machine_State(ResultSet rs) throws SQLException {
		ByteBuffer bf = ByteBuffer.allocate( 8 );
		bf.order( ByteOrder.LITTLE_ENDIAN );

		bf.putLong( rs.getLong("id") );

		byte[] id = new byte[ 8 ];
		bf.clear();
		bf.get( id , 0 , 8 );   	
        this.atId = id;
        
		this.delayBlocks = rs.getInt("delay_blocks");
		this.sleepBetween = rs.getInt("sleep_between");
		this.freezeWhenSameBalance = rs.getBoolean("freeze_when_same_balance");
		this.startBlock = rs.getInt("start_block");	
		this.varBytes = rs.getInt("var_bytes");		
		this.creationBlockHeight = rs.getInt("height");;		
			
		int pageSize = ( int ) AT_Constants.getInstance().PAGE_SIZE( creationBlockHeight );
		int nLength = rs.getBytes("machinecode").length;
		int codePages = (int)Math.ceil((float)nLength / pageSize) +1;
		this.csize =  nLength < pageSize ? nLength : codePages * pageSize;

		nLength = rs.getBytes("data").length + varBytes;	
		int dataPages = (int)Math.ceil((float)nLength / pageSize); 
		this.dsize = nLength < pageSize ? nLength : dataPages  * pageSize;;
		
		this.ap_code = ByteBuffer.allocate( this.csize );
		this.ap_code.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_code.put( rs.getBytes("machinecode"));

		this.ap_data = ByteBuffer.allocate( this.dsize );
		this.ap_data.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_data.put(rs.getBytes("data"));		
		
		this.constBytes = (int)rs.getBytes("data").length;
		this.minimumFee = ( codePages + 
				dataPages) * AT_Constants.getInstance().COST_PER_PAGE(rs.getInt("height"));
		this.transactions = new ArrayList<AT_Transaction>();
		this.g_balance = 0;
		this.p_balance = 0;
		this.machineState = new Machine_State();		
		
    }	

	public AT_Machine_State( byte[] atId , byte[] creator ,  byte[] machineCode, byte[] machineData, byte[] properties , int height ) 
	{
		this.version = AT_Constants.getInstance().AT_VERSION( height );
		this.atId = atId;
		this.creator = creator;
		//256
		int pageSize = ( int ) AT_Constants.getInstance().PAGE_SIZE( height );
		
		ByteBuffer b = ByteBuffer.allocate( properties.length );
		b.order( ByteOrder.LITTLE_ENDIAN );
		
		b.put( properties );
		b.clear();
		
		this.version = b.getShort();
		b.getShort(); //future: reserved for future needs
		
		this.delayBlocks = b.getInt();
		this.sleepBetween = b.getInt();		
		this.freezeWhenSameBalance = b.get()== 0 ? false : true;
		this.startBlock = b.getInt();	
		this.varBytes = b.getInt();
		this.retrievedHeight = Integer.MAX_VALUE;
		this.stepsFeeNQT = 0;
		this.constBytes = machineData.length;

		int codePages = (int)Math.ceil((float)machineCode.length / pageSize) +1;
		int dataPages = (int)Math.ceil((float)(machineData.length + varBytes) / pageSize) ;
		
		int nLength = machineCode.length;	
		this.csize =  nLength < pageSize ? nLength : codePages * pageSize;

		nLength = machineData.length + varBytes;	
		this.dsize = nLength < pageSize ? nLength : dataPages  * pageSize;;

		this.ap_code = ByteBuffer.allocate( csize );
		this.ap_code.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_code.put( machineCode );
	
		this.ap_data = ByteBuffer.allocate( this.dsize );
		this.ap_data.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_data.put( machineData );

		this.creationBlockHeight = height;
		this.minimumFee = ( codePages + 
				dataPages) * AT_Constants.getInstance().COST_PER_PAGE( height );

		this.transactions = new ArrayList<AT_Transaction>();
		this.g_balance = 0;
		this.p_balance = 0;
		this.machineState = new Machine_State();
	}

	protected byte[] get_A1()
	{
		ap_data.clear();
		ap_data.position(constBytes);
		ap_data.get( machineState.A1, 0 , 8 );		
		return this.machineState.A1;
	}

	protected byte[] get_A2()
	{
		ap_data.clear();
		ap_data.position(constBytes + 8);
		ap_data.get( machineState.A2, 0 , 8 );	
		return machineState.A2;
	}

	protected byte[] get_A3()
	{
		ap_data.clear();
		ap_data.position(constBytes + 16);
		ap_data.get( machineState.A3, 0 , 8 );	
		return machineState.A3;
	}

	protected byte[] get_A4()
	{
		ap_data.clear();
		ap_data.position(constBytes + 24);
		ap_data.get( machineState.A4, 0 , 8 );	
		return machineState.A4;
	}

	protected byte[] get_B1()
	{
		ap_data.clear();
		ap_data.position(constBytes + 32);
		ap_data.get( machineState.B1, 0 , 8 );	
		return machineState.B1;
	}

	protected byte[] get_B2()
	{
		ap_data.clear();
		ap_data.position(constBytes + 40);
		ap_data.get( machineState.B2, 0 , 8 );	
		return machineState.B2;
	}

	protected byte[] get_B3()
	{
		ap_data.clear();
		ap_data.position(constBytes + 48);
		ap_data.get( machineState.B3, 0 , 8 );	
		return machineState.B3;
	}

	protected byte[] get_B4()
	{
		ap_data.clear();
		ap_data.position(constBytes + 56);
		ap_data.get( machineState.B4, 0 , 8 );	
		return machineState.B4;
	}

	protected void set_A1( byte[] A1 )
	{
		//this.machineState.A1 = A1.clone();
		ap_data.clear();
		ap_data.position(constBytes);		
		ap_data.put (A1, 0, A1.length);
	}

	protected void set_A2( byte[] A2 ){
		ap_data.clear();
		ap_data.position(constBytes + 8);		
		ap_data.put (A2, 0, A2.length);		
	}

	protected void set_A3( byte[] A3 )
	{
		ap_data.clear();
		ap_data.position(constBytes + 16);		
		ap_data.put (A3, 0, A3.length);		
	}

	protected void set_A4( byte[] A4 )
	{
		ap_data.clear();
		ap_data.position(constBytes + 24);		
		ap_data.put (A4, 0, A4.length);		
	}

	protected void set_B1( byte[] B1 )
	{
		ap_data.clear();
		ap_data.position(constBytes + 32);		
		ap_data.put (B1, 0, B1.length);		
	}

	protected void set_B2( byte[] B2 )
	{
		ap_data.clear();
		ap_data.position(constBytes + 40);		
		ap_data.put (B2, 0, B2.length);		
	}

	protected void set_B3( byte[] B3 )
	{
		ap_data.clear();
		ap_data.position(constBytes + 48);		
		ap_data.put (B3, 0, B3.length);		
	}

	protected void set_B4( byte[] B4 )
	{
		ap_data.clear();
		ap_data.position(constBytes + 56);		
		ap_data.put (B4, 0, B4.length);		
	}

	protected void addTransaction(AT_Transaction tx)
	{
		transactions.add(tx);
	}

	protected void clearTransactions()
	{
		transactions.clear();
	}

	public List<AT_Transaction> getTransactions()
	{
		return transactions;
	}

	public ByteBuffer getAp_code() 
	{
		return ap_code;
	}

	public ByteBuffer getAp_data() 
	{
		return ap_data;
	}

	public void setAp_code(byte[] machineCode) 
	{
		this.ap_code = ByteBuffer.allocate( this.csize );
		this.ap_code.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_code.put( machineCode );
	
	}

	public void setAp_data(byte[] machineData) 
	{
		this.ap_data = ByteBuffer.allocate( this.dsize );
		this.ap_data.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_data.put( machineData );

	}
	
	//only save var data to AT_State
	public void setVarAp_data(byte[] machineData) 
	{
		this.ap_data.clear();
		this.ap_data.position(constBytes);
		this.ap_data.put( machineData, 0, varBytes);

	}
	
	protected int getCsize() 
	{
		return csize;
	}

	protected int getDsize() 
	{
		return dsize;
	}

	public Long getG_balance() 
	{
		return g_balance;
	}

	public Long getP_balance() 
	{
		return p_balance;
	}

	public byte[] getId()
	{
		return atId;
	}

	public Long getLongId()
	{
		byte[] bId = getId();
		byte[] b = new byte[ 8 ];
		for ( int i = 0; i < 8; i++ )
		{
			b[ i ] = bId[ i ];
		}

		Long atLongId = AT_API_Helper.getLong( b );
		return atLongId;
	}
	
	public Machine_State getMachineState() 
	{
		return machineState;
	}

	public long getMinimumFee()
	{
		return minimumFee;
	}

	protected void setCsize(int csize) 
	{
		this.csize = csize;
	}

	protected void setDsize(int dsize) 
	{
		this.dsize = dsize;
	}

	public void setG_balance(Long g_balance) 
	{
		this.g_balance = g_balance;
	}

	public void setG_balanceMinus(Long balance) 
	{
		this.g_balance = g_balance - balance;
	}
	
	public void setP_balance(Long p_balance) 
	{
		this.p_balance = p_balance;
	}

	public void setMachineState(Machine_State machineState) 
	{
		this.machineState = machineState;
	}

	public void setDelayBlocks(int delayBlocks) 
	{
		this.delayBlocks = delayBlocks;
	}

	public int getDelayBlocks()
	{
		return this.delayBlocks;
	}

	public byte[] getCreator() 
	{
		return this.creator;
	}

	public int getCreationBlockHeight() 
	{
		return this.creationBlockHeight;
	}

	public boolean getFreezeWhenSameBalance()
	{
		return this.freezeWhenSameBalance;
	}
	
	public short getVersion()
	{
		return version;
	}

	public byte[] getTransactionBytes( )
	{
		ByteBuffer b = ByteBuffer.allocate( (creator.length + 8 ) * transactions.size() );
		b.order( ByteOrder.LITTLE_ENDIAN );
		for (AT_Transaction tx : transactions )
		{
			b.put( tx.getRecipientId() );
			b.putLong( tx.getAmount() );
		}
		return b.array();

	}

	public byte[] getBytes()
	{
		byte[] txBytes = getTransactionBytes();
		byte[] stateBytes = machineState.getMachineStateBytes();
		byte[] dataBytes = ap_data.array();

		ByteBuffer b = ByteBuffer.allocate( atId.length + txBytes.length + stateBytes.length + dataBytes.length );
		b.order( ByteOrder.LITTLE_ENDIAN );

		b.put( atId );
		b.put( stateBytes );
		b.put( dataBytes );
		b.put( txBytes );

		return b.array();

	}
	
	public byte[] getBytesWithoutTransactions()
	{
		byte[] stateBytes = machineState.getMachineStateBytes();
		byte[] dataBytes = ap_data.array();

		ByteBuffer b = ByteBuffer.allocate( stateBytes.length + dataBytes.length +8 +4);
		b.order( ByteOrder.LITTLE_ENDIAN );

		b.put( stateBytes );
		b.put( dataBytes );
		b.putLong(stepsFeeNQT);
		b.putInt(retrievedHeight);	

		return b.array();
	}
	
	public void setStateAndData(byte[] stateAndData) {
		ByteBuffer b = ByteBuffer.allocate( stateAndData.length );
		b.order( ByteOrder.LITTLE_ENDIAN );
		
		b.put(  stateAndData );
		b.clear();

		b.get( machineState.flags );
		machineState.running = (b.get()==1 ? true :false );	
		machineState.pc = b.getInt();
		machineState.pcs = b.getInt();
		machineState.steps = b.getInt();
		
		byte[] machineData = new byte[ this.dsize ];
		b.get(machineData);
		this.ap_data.order( ByteOrder.LITTLE_ENDIAN );
		this.ap_data.put( machineData );
		
		stepsFeeNQT =b.getLong();
		b.getInt(retrievedHeight);
	}
	
	public long getStepsFeeNQT(){
		return stepsFeeNQT;
	}
	
	public void setRunBlock(int runBlock){
		this.runBlock= runBlock;// + this.sleepBetween;
	}        

	public void setRunBlock(){
		this.runBlock= this.runBlock + this.sleepBetween;
	}        

	public void setStepsFeeNQT(long stepsFeeNQT){
		this.stepsFeeNQT= stepsFeeNQT;
	}
	
	public int getRetrievedHeight(){
		return retrievedHeight;
	}
	
	public void setRetrievedHeight( int retrievedHeight){
		this.retrievedHeight = retrievedHeight;
	}

	public ConcurrentSkipListMap< Integer , byte[] > getBlockATBytesAtHeights() {
		return this.blockATBytesAtHeights;	
	}

	public int getLastRanBlock(){
		return this.lastRanBlock;
	}
	
	public void setLastRanBlock(int blockHeight){
		this.lastRanBlock= blockHeight;
	}        

	public int getLastRanSteps(){
		return this.lastRanSteps;
	}
	
	public void setLastRanSteps(int lastRanSteps){
		this.lastRanSteps= lastRanSteps;
	}
	
	public void setTimeStamp(long timeStamp){
		this.machineState.timeStamp = timeStamp;
		//transfer timestamp to ap_data. Update ap_data may cause error(solved). 
		this.ap_data.putLong(0,timeStamp);
	}
	
	public int getSleepBetween(){
		return this.sleepBetween;
	}
	
	public int getStartBlock(){
		return this.startBlock;
	}
	
	public int getConstBytes(){
		return this.constBytes;
	}
	
	public int getVarBytes(){
		return this.varBytes;
	}
	
	public byte[] getVarMachinData()
	{
		byte[] varMachinData = new byte[this.varBytes];
		ap_data.clear();
		ap_data.position(constBytes);
		ap_data.get( varMachinData, 0 , varBytes );	
		return varMachinData;

	}		

	
}
