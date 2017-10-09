package nxt.game;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.EntityDbTable;
import nxt.db.VersionedEntityDbTable;
import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.Nxt;
import nxt.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Move {

    public static enum MoveType {
        OUTSIDER, BE_COLLECTOR, BE_WORKER, COLLECT, CHECK_IN, EAT, BUILD, ATTACK, KEEP_FIT, PRACTISE_MARTIAL, BUY_ARMOR, IN_COMA, WAKEUP, QUIT_GAME 
    }
    
	private static final DbKey.LongKeyFactory<Move> moveDbKeyFactory = new DbKey.LongKeyFactory<Move>("account_id") {

        @Override
        public DbKey newKey(Move move) {
            return move.dbKey;
        }

    };

    private static final VersionedEntityDbTable<Move> moveTable = new VersionedEntityDbTable<Move>("move", moveDbKeyFactory) {

        @Override
        protected Move load(Connection con, ResultSet rs) throws SQLException {
            return new Move(rs);
        }

        @Override
        protected void save(Connection con, Move move) throws SQLException {
        	move.save(con);
        }

    };

    public static Move getMove(long accountId) {
        return accountId == 0 ? null : moveTable.get(moveDbKeyFactory.newKey(accountId));
    }
    
    public static DbIterator<Move> getMovesByPlayer(long accountId, int from, int to) {
        return moveTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }
   
    private static final class getCoordinateXYClause extends DbClause {
    	private final int x;
    	private final int y;

        private getCoordinateXYClause(final int x, int y) {
        	super(" x_coordinate = ? AND y_coordinate = ? ");
            this.x = x;
            this.y = y;
        }

        @Override
        public int set(PreparedStatement pstmt, int index) throws SQLException {
        	pstmt.setInt(index++, x);
            pstmt.setInt(index++, y);
            return index;
        }

    }

    public static int  getCoordinatePlayersCount( int x, int y) {
            return moveTable.getCount(new getCoordinateXYClause(x,y));
    }
   
    public static int getAccountCountByHeight(long accountId, int height) {
        if (height < 0) {
            return 0;
        }
        return moveTable.getCount(new DbClause.LongClause("account_id", accountId), height);
    }
    
    static void addMove(Transaction transaction, Attachment.GameMove attachment) {
        moveTable.insert(new Move(transaction, attachment));
    }
    
    public static void addOrUpdateMove(Transaction transaction, Attachment.GameMove attachment) {
    	long senderId = transaction.getSenderId();
    	DbIterator<Move> moves = getMovesByPlayer(senderId, 0, 1);
    	Move move;
        if (!moves.hasNext()) {
            move = new Move(transaction, attachment);
        } else {
            move = moves.next();
            move.xCoordinate = attachment.getXCoordinate();
            move.yCoordinate = attachment.getYCoordinate();
            move.step = MoveType.valueOf(attachment.getAppendixName().toUpperCase());
            --move.collectPower;
        }
        moveTable.insert(move);
        setPlayerStatus(move.getAccountId(), move.getMoveType());
    }

    
    public static void init() {}    
    
    private final long accountId;
    private final DbKey dbKey;        
    private int collectPower; 
	private int attackPower; 
    private int defenseValue; 
    private int healthyIndex;
    private int xCoordinate;
    private int yCoordinate;
    private MoveType step;

    public static void setPlayerStatus(long accountId, MoveType step) {
    	Account.PlayerType player = null;
    	switch(step) {
    	case QUIT_GAME: 
    		player = Account.PlayerType.OUTSIDER;
    		break;
    	case BE_WORKER: 
    		player = Account.PlayerType.WORKER;
    		break;
    	case BE_COLLECTOR:
    		player = Account.PlayerType.COLLECTOR;
        }
    	
    	if (player == null) 
    		return;
    	else {
    		Account account = Account.getAccount(accountId);
    		if (account.getPlayer() != player)
    			account.setPlayer(player);
    	}
    }
        
    private Move(Transaction transaction, Attachment.GameMove attachment) {
        this.accountId = transaction.getSenderId();
        this.dbKey = moveDbKeyFactory.newKey(this.accountId);
        this.xCoordinate = attachment.getXCoordinate();
        this.yCoordinate = attachment.getYCoordinate();
        this.step = MoveType.valueOf(attachment.getAppendixName().toUpperCase());
        
        this.collectPower = Constants.GAME_INIT_COLLECT_POWER;
        this.attackPower = Constants.GAME_INIT_ATTACK_POWER;
        this.defenseValue = Constants.GAME_INIT_DEFENSE_VALUE;
        this.healthyIndex = Constants.GAME_INIT_HEALTHY_INDEX;
        
        //setPlayerStatus(this.step);
    }
    
    private Move(long accountId, int collectPower, int attackPower, int defenseValue, int healthyIndex, int xCoordinate, int yCoordinate, String step) {
        this.accountId = accountId;
        this.dbKey = moveDbKeyFactory.newKey(this.accountId);
        this.collectPower = collectPower;
        this.attackPower = attackPower;
        this.defenseValue = defenseValue;
        this.healthyIndex = healthyIndex;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.step = MoveType.valueOf(step);          
    }
	
    private Move(ResultSet rs) throws SQLException {
    	this.accountId = rs.getLong("account_id");
        this.dbKey = moveDbKeyFactory.newKey(this.accountId);		
        this.collectPower = rs.getInt("collect_power");
        this.attackPower = rs.getInt("attack_power");
        this.defenseValue = rs.getInt("defense_value");
        this.healthyIndex = rs.getInt("healthy_index");
        this.xCoordinate = rs.getInt("x_coordinate");
        this.yCoordinate = rs.getInt("y_coordinate");
		this.step = MoveType.valueOf(rs.getString("step"));		
    }	

	private void save(Connection con)
	{
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO move (account_id, collect_power, "
                + "attack_power, defense_value, healthy_index, x_coordinate, y_coordinate, step, height) "
        		+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
            pstmt.setLong(++i, this.getAccountId());
			pstmt.setInt(++i, this.getCollectPower());
            pstmt.setInt(++i, this.getAttackPower());
            pstmt.setInt(++i, this.getDefenseValue());
            pstmt.setInt(++i, this.getHealthyIndex());
            pstmt.setInt(++i, this.getXCoordinate());
            pstmt.setInt(++i, this.getYCoordinate());
            //pstmt.setInt(++i, this.getMoveType().ordinal());
            DbUtils.setString( pstmt , ++i , this.getMoveTypeStr() );            
			pstmt.setInt( ++i , Nxt.getBlockchain().getHeight() );

			pstmt.executeUpdate();
				}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

	}
	
    public long getAccountId() {
        return accountId;
    }
    
	public int getCollectPower() {
        return collectPower;
    }

    public int getAttackPower() {
        return attackPower;
    }
    
    public int getDefenseValue() {
        return defenseValue;
    }
    
    public int getHealthyIndex() {
        return healthyIndex;
    }
    
    public int getXCoordinate() {
        return xCoordinate;
    }
    
    public int getYCoordinate() {
        return yCoordinate;
    }
    
    public String getMoveTypeStr() {
        return step.name();
    }

    public MoveType getMoveType() {
        return step;
    }
    
    void setAccountPlayer(int xCoordinate, int yCoordinate, MoveType step) {
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.step = step;
        moveTable.insert(this);
    }
    
    void playerMoveTo(int xCoordinate, int yCoordinate, MoveType step) {
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        if (this.attackPower > 1) 
        	--this.attackPower;
        moveTable.insert(this);
    }
}
