package nxt.game;

import nxt.crypto.Crypto;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.EntityDbTable;
import nxt.util.Listener;
import nxt.util.Logger;
import nxt.Account;
import nxt.Asset;
import nxt.Attachment;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import nxt.AT.ATRunType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Move {

    public static enum PlayerMove {
        COLLECTOR, WORKER, OUTSIDER
    }
    
	private static final DbKey.LongKeyFactory<Move> moveDbKeyFactory = new DbKey.LongKeyFactory<Move>("account_id") {

        @Override
        public DbKey newKey(Move move) {
            return move.dbKey;
        }

    };

    private static final EntityDbTable<Move> moveTable = new EntityDbTable<Move>("playing", moveDbKeyFactory) {

        @Override
        protected Move load(Connection con, ResultSet rs) throws SQLException {
            return new Move(rs);
        }

        @Override
        protected void save(Connection con, Move move) throws SQLException {
        	move.save(con);
        }

    };

	public static DbIterator<Move> getMoveByPlayer(long accountId, int from, int to) {
        return moveTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }
   
    static void addMove(Transaction transaction, Attachment.GameMove attachment) {
        moveTable.insert(new Move(transaction, attachment));
    }
    
    static void init() {}    
    
    private final long accountId;
    private final DbKey dbKey;        
	public int collectPower; 
    public int attackPower; 
    public int defenseValue; 
    public int healthyIndex;
    public int xCoordinate;
    public int yCoordinate;
    public PlayerMove step;


    private Move(Transaction transaction, Attachment.GameMove attachment) {
        this.accountId = transaction.getSenderId();
        this.dbKey = moveDbKeyFactory.newKey(this.accountId);
        this.xCoordinate = attachment.getXCoordinate();
        this.yCoordinate = attachment.getYCoordinate();
        this.step = PlayerMove.valueOf(attachment.getAppendixName());
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
        this.step = PlayerMove.valueOf(step);        
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
        this.step = PlayerMove.values()[rs.getInt("step")];
        
		
    }	

	private void save(Connection con)
	{
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO move (account_id, collect_power, "
                + "attack_power, defense_value, healthy_index, x_coordinate, y_coordinate, step, height) "
        		+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
            pstmt.setInt(++i, this.getCollectPower());
            pstmt.setInt(++i, this.getAttackPower());
            pstmt.setInt(++i, this.getDefenseValue());
            pstmt.setInt(++i, this.getHealthyIndex());
            pstmt.setInt(++i, this.getXCoordinate());
            pstmt.setInt(++i, this.getYCoordinate());
            pstmt.setInt(++i, this.getPlayerMove().ordinal());
			pstmt.setInt( ++i , Nxt.getBlockchain().getHeight() );

			pstmt.executeUpdate();
				}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

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
    
    public PlayerMove getPlayerMove() {
        return step;
    }
    
    void setAccountPlayer(int xCoordinate, int yCoordinate, PlayerMove step) {
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.step = step;
        moveTable.insert(this);
    }
    
    void playerMoveTo(int xCoordinate, int yCoordinate, PlayerMove step) {
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        if (this.attackPower > 1) 
        	--this.attackPower;
        moveTable.insert(this);
    }
}
