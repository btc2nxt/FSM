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

    public static class LandCompleted {

        private final int x;
        private final int y;
        private final DbKey dbKey;
        private long lifeValue;        

        private LandCompleted(int x, int y, long lifeValue) {
            this.x = x;
            this.y = y;
            this.lifeValue = lifeValue;
            this.dbKey = landCompletedDbKeyFactory.newKey(this.x, this.y);
        }

        private LandCompleted(ResultSet rs) throws SQLException {
            this.x = rs.getInt("x_coordinate");
            this.y = rs.getInt("y_coordinate");
            this.lifeValue = rs.getLong("life_Value");
            this.dbKey = landCompletedDbKeyFactory.newKey(this.x, this.y);
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO land_completed "
                    + "(x_coordinate, y_coordinate, life_Value, height, latest) "
                    + "KEY (x_coordinate, y_coordinate) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setInt(++i, this.x);
                pstmt.setLong(++i, this.y);
                pstmt.setLong(++i, this.lifeValue);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
        
        public long getLifeValue() {
            return this.lifeValue;
        }        
    }
    
    private static final DbKey.LinkKeyFactory<LandCompleted> landCompletedDbKeyFactory = new DbKey.LinkKeyFactory<LandCompleted>("x_coordinate", "y_coordinate") {

        @Override
        public DbKey newKey(LandCompleted landCompleted) {
            return landCompleted.dbKey;
        }

    };

    public static final EntityDbTable<LandCompleted> landCompletedTable = new EntityDbTable<LandCompleted>("land_completed", landCompletedDbKeyFactory) {

        @Override
        protected LandCompleted load(Connection con, ResultSet rs) throws SQLException {
            return new LandCompleted(rs);
        }

        @Override
        protected void save(Connection con, LandCompleted landCompleted) throws SQLException {
        	landCompleted.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY x, y ";
        }

    };
    
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

    public static LandCompleted getLandCompleted(int x, int y) {
        return landCompletedTable.get(landCompletedDbKeyFactory.newKey(x, y));
    }
    
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
   
    private static final class getConsumerXYClause extends DbClause {
    	private final int x;
    	private final int y;
    	private final String moveType;

        private getConsumerXYClause(final int x, int y, String moveType) {
        	super(" x_coordinate = ? AND y_coordinate = ? and step = ?");
            this.x = x;
            this.y = y;
            this.moveType = moveType;
        }

        @Override
        public int set(PreparedStatement pstmt, int index) throws SQLException {
        	pstmt.setInt(index++, x);
            pstmt.setInt(index++, y);
            DbUtils.setString( pstmt , index++ , moveType );
            return index;
        }

    }

    public static int  getCoordinateConsumersCount( int x, int y, String moveType) {
            return moveTable.getCount(new getConsumerXYClause(x, y, moveType));
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
    	int x;
    	int y;
    	//DbIterator<Move> moves = getMovesByPlayer(senderId, 0, 1);
        //if (!moves.hasNext()) {
    	x = attachment.getXCoordinate();
    	y = attachment.getYCoordinate();
    	Move move = getMove(senderId);
    	if (move == null) {
            move = new Move(transaction, attachment);
        } else {
            //move = moves.next();
        	if (!attachment.getAppendixName().equals("QUIT_GAME")) {
        		move.xCoordinate = x;
        		move.yCoordinate = y;
        		move.step = MoveType.valueOf(attachment.getAppendixName().toUpperCase());
        		--move.collectPower;
            
        		if (attachment.getAppendixName().equals("Build")) {
        			LandCompleted landCompleted = getLandCompleted(x,y);
        			if (landCompleted != null) {
        				if (landCompleted.lifeValue < Constants.MAX_HOTEL_RESTAURANT_LIFEVALUE)
        					landCompleted.lifeValue = landCompleted.lifeValue + Constants.GAME_BRICK_RATE;
        				
        				if (landCompleted.lifeValue <= Constants.MAX_HOTEL_RESTAURANT_LIFEVALUE)
        					landCompletedTable.insert(landCompleted);
        			}
        			else {
        				landCompletedTable.insert(new LandCompleted(x, y,Constants.GAME_BRICK_RATE ));
        			}
        			move.lifeValue = Constants.GAME_BRICK_RATE;
        			move.assetId = ((Attachment.GameBuild) attachment).getAssetId();
                }
        		else if (attachment.getAppendixName().equals("Check_In")) {
        			move.lifeValue = ((Attachment.GameCheckIn) attachment).getAmountNQT();
        			move.assetId = ((Attachment.GameCheckIn) attachment).getAssetId();
        		}
        		else if (attachment.getAppendixName().equals("Eat")) {
        			move.lifeValue = ((Attachment.GameEat) attachment).getAmountNQT();
        			move.assetId = ((Attachment.GameCheckIn) attachment).getAssetId();        			
        		}
        	}
        	else
        		move.step = MoveType.valueOf(attachment.getAppendixName().toUpperCase());
        		
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
    private long lifeValue;    
    private long assetId;    
    
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
        this.lifeValue = 0;
        
    }
    
    private Move(long accountId, int collectPower, int attackPower, int defenseValue, int healthyIndex, int xCoordinate, int yCoordinate, String step, long lifeValue) {
        this.accountId = accountId;
        this.dbKey = moveDbKeyFactory.newKey(this.accountId);
        this.collectPower = collectPower;
        this.attackPower = attackPower;
        this.defenseValue = defenseValue;
        this.healthyIndex = healthyIndex;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.step = MoveType.valueOf(step);
        this.lifeValue = lifeValue;
        this.assetId = assetId;
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
		this.lifeValue = rs.getLong("life_Value");		
		this.assetId = rs.getLong("asset_id");		
    }	

	private void save(Connection con)
	{
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO move (account_id, collect_power, "
                + "attack_power, defense_value, healthy_index, x_coordinate, y_coordinate, step, life_Value, asset_id, height) "
        		+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            pstmt.setLong(++i, this.getLifeValue());
            pstmt.setLong(++i, this.getAssetId());
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
    
    public long getLifeValue() {
        return lifeValue;
    }
    
    public long getAssetId() {
        return assetId;
    }
    
    /*void setAccountPlayer(int xCoordinate, int yCoordinate, MoveType step) {
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
    }*/
}
