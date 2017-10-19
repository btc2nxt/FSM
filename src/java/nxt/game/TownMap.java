package nxt.game;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nxt.Asset;
import nxt.Constants;
import nxt.Nxt;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.EntityDbTable;
import nxt.game.Move.LandCompleted;
import nxt.game.Move.MoveType;
import nxt.util.Logger;

public final class TownMap {
    public static enum LandDescription {
        WALL_AREA, COIN_AREA, HOTEL, RESTAURANT, HEALTHY_CLUB, MARTIL_CENTER, WEAPON_FACTORY
    }	
    
    public static class Land {
    	private int landId;
    	private final DbKey dbKey;    	
    	private int x;
    	private int y;
    	private int x1;
    	private int y1;
    	private LandDescription landType;
    	private int playersPerPoint;
    	private long  assetId;
    	
    	public Land(int landId, int x, int y, int x1, int y1, LandDescription landType, long assetId) {
    		this.landId = landId;
            this.dbKey = landDbKeyFactory.newKey(this.landId);    		
    		this.x = x;
    		this.y = y;
    		this.x1 = x1;
    		this.y1 = y1;
    		this.landType = landType;
    		this.assetId = assetId;
    		switch (this.landType) {
    			case COIN_AREA:
    				this.playersPerPoint = Constants.MAX_PLAYERS_PER_COORDINATE;
    				break;
    			case HOTEL:
    			case RESTAURANT:
    			case HEALTHY_CLUB:	
    			case MARTIL_CENTER:
    			case WEAPON_FACTORY:
    				this.playersPerPoint = Constants.MAX_CONSUMER_PER_COORDINATE;
    				//this.availablePoints = Constants.MAX_PLAYERS_CAPAITY_OF_BUILDING;
    				//lifeValues = new long[(x1 - x + 1) * (y1 - y + 1)];
    				break;
    			default:
    				this.playersPerPoint = 0;
    		}
    				
    	}
    	
        private Land(ResultSet rs) throws SQLException {
            this.landId = rs.getInt("id");
            this.dbKey = landDbKeyFactory.newKey(this.landId);        	
            this.x = rs.getInt("x");
            this.y = rs.getInt("y");
            this.x1 = rs.getInt("x1");
            this.y1 = rs.getInt("y1");     
    		this.landType = LandDescription.valueOf(rs.getString("landType"));
    		this.playersPerPoint = rs.getInt("players_PerPoint");    		
            this.assetId = rs.getLong("asset_id");

        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("insert INTO land "
                    + "(id, x, y, x1, y1, land_type, players_PerPoint, asset_id, height, latest) "
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setInt(++i, this.landId);
                pstmt.setInt(++i, this.x);
                pstmt.setInt(++i, this.y);
                pstmt.setInt(++i, this.x1);
                pstmt.setInt(++i, this.y1); 
                DbUtils.setString( pstmt , ++i , this.landType.name() );                
                pstmt.setInt(++i, this.playersPerPoint);                
                pstmt.setLong(++i, this.assetId);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
        
    	public int getX() {
    		return x;
    	}

    	public int getX1() {
    		return x1;
    	}
    	
    	public int getY() {
    		return y;
    	}

    	public int getY1() {
    		return y1;
    	} 
    	
    	public LandDescription getLandType() {
    		return this.landType;
    	}
    }
    
    private static final DbKey.LongKeyFactory<Land> landDbKeyFactory = new DbKey.LongKeyFactory<Land>("id") {

        @Override
        public DbKey newKey(Land land) {
            return land.dbKey;
        }

    };

    public static final EntityDbTable<Land> landTable = new EntityDbTable<Land>("land", landDbKeyFactory) {

        @Override
        protected Land load(Connection con, ResultSet rs) throws SQLException {
            return new Land(rs);
        }

        @Override
        protected void save(Connection con, Land land) throws SQLException {
        	land.save(con);
        }

    };
    
    public static DbIterator<Land> getAllLands(int from, int to) {
        return landTable.getAll(from, to);
    }
    
    public static Land[] lands;
    static int townX;
    static int townY;
    static int townX1;
    static int townY1;
    static int coinLandBegin;
    static int coinLandEnd;
    static int hotelLandBegin;
    static int hotelLandEnd;
    static int restaurantLandBegin;
    static int restaurantLandEnd;
    
    private final static TownMap instance = new TownMap();
    
    public static void init() {}
    
    private TownMap() {
    	townX = 0;
    	townY = 0;
    	townX1 = 110;
    	townY1 = 110;
    	coinLandBegin = 0;
    	coinLandEnd = 6;
    	hotelLandBegin = 10;
    	hotelLandEnd = 13;    	
    	
    	//there are 72 buildings, 7 coin areas.ignore walls
    	lands = new Land[79];

    	//7 coin areas
    	lands[0] = new Land(0, 1,  1, 39, 39,LandDescription.COIN_AREA,(long) -1);
    	lands[1] = new Land(1, 71, 1, 109, 39,LandDescription.COIN_AREA,(long) -1);   	
    	lands[2] = new Land(2,  1, 71, 39, 109,LandDescription.COIN_AREA,(long) -1);
    	lands[3] = new Land(3, 71, 71, 109, 109,LandDescription.COIN_AREA,(long) -1);
    	lands[4] = new Land(4,  1, 51, 109, 59,LandDescription.COIN_AREA,(long) -1);
    	lands[5] = new Land(5, 51,  1, 59, 50,LandDescription.COIN_AREA,(long) -1);
    	lands[6] = new Land(6, 51, 60, 59, 109,LandDescription.COIN_AREA,(long) -1);    	
    	
    	//block 1-1 from (1,41) to (49,49), total 10 buildings
    	lands[10] = new Land(10, 1, 41,  4, 49,LandDescription.HOTEL,(long) -1);
    	lands[11] = new Land(11, 6, 41,  9, 49,LandDescription.HOTEL,(long) -1);
    	lands[12] = new Land(12, 11, 41, 14, 49,LandDescription.HOTEL,(long) -1);
    	lands[13] = new Land(13, 16, 41, 19, 49,LandDescription.HOTEL,(long) -1);
    	lands[14] = new Land(14, 21, 41, 24, 49,LandDescription.RESTAURANT,(long) -1);    	
    	lands[15] = new Land(15, 16, 41, 29, 49,LandDescription.RESTAURANT,(long) -1);    	
    	lands[16] = new Land(16, 31, 41, 34, 49,LandDescription.RESTAURANT,(long) -1);    	
    	lands[17] = new Land(17, 36, 41, 39, 49,LandDescription.RESTAURANT,(long) -1);
	
    }
    
	public static TownMap getInstance( ){
		return instance;
	}
	
    public int getTownX() {
        return townX;
    }
    
    public int getTownX1() {
        return townX1;
    }

    public static int getTownY() {
        return townY;
    }
    
    public int getTownY1() {
        return townY1;
    }
    
    public static Land getLand(int nLand) {
        return lands[nLand];
    }
    
    public static void addLand(int landId, long assetId) {
    	Land land = lands[landId];
    	land.assetId = assetId;
    	landTable.insert(land);
    }
    
    
    /*
     * (x,y ) ---> Hotel.room
     * (x,y ) ---> Restaurant.table
     * ...
     
    public static void setLifeValueOfLandAsset(int x, int y, long lifeValue) {
		for ( int i = 0; i <10; i++) {
			if (x >= lands[i].x && x <= lands[i].x1
					&& y >= lands[i].y & y <= lands[i].y1) {
				int seq = (y- lands[i].y) * (lands[i].x1 - lands[i].x + 1) + x- lands[i].x + 1;
				lands[i].lifeValues[seq] = lifeValue;
				break;
			}
		}
    }
    
    public static long getLifeValueOfLandAsset(int x, int y) {
		long lifeValue = 0;
		
    	for ( int i = 0; i <10; i++) {
			if (x >= lands[i].x && x <= lands[i].x1
					&& y >= lands[i].y & y <= lands[i].y1) {
				int seq = (y- lands[i].y) * (lands[i].x1 - lands[i].x + 1) + x- lands[i].x + 1;
				lifeValue = lands[i].lifeValues[seq];
				break;
			}
		}
    	return lifeValue;
    }  
    */  
}
