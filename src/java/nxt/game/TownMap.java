package nxt.game;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nxt.Constants;
import nxt.Nxt;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.EntityDbTable;

public final class TownMap {
    public static enum LandDescription {
        WALL_AREA, COIN_AREA, HOTEL, RESTAURANT, USER_AREA, WITNESS_HALL, JUSTICE_HALL, HEALTHY_CLUB, MARTIL_CENTER, WEAPON_FACTORY
    }	
    
    public static final int TOWN_X = 0;
    public static final int TOWN_Y = 0;
    public static final int TOWN_X1 = 99;
    public static final int TOWN_Y1 = 99;
    public static final int TOWN_MAX_LAND = 23;    
    public static final int COIN_LAND_BEGIN = 0;
    public static final int COIN_LAND_END = 4;
    public static final int HOTEL_LAND_BEGIN = 5;
    public static final int HOTEL_LAND_END = 8;
    public static final int RESTAURANT_LAND_BEGIN = 9;
    public static final int RESTAURANT_LAND_END = 12;
	
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
    		this.landType = LandDescription.valueOf(rs.getString("land_Type"));
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
    	
    	public long getAssetId() {
    		return assetId;
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
    
    public static Land getLand(int id) {
        return id == 0 ? null : landTable.get(landDbKeyFactory.newKey(id));
    }
    
    public static DbIterator<Land> getAllLands(int from, int to) {
        return landTable.getAll(from, to);
    }
    
    public static Land[] lands;

    
    private final static TownMap instance = new TownMap();
    
    public static void init() {}
    
    private TownMap() {
   	
    	/*
    	 *coin area : 5, black
    	 *hotel : 4, red
    	 *restaurant : 4, pink
    	 *witness hall : 1, green
    	 *justice hall : 1, purple
    	 *user area : 9, blue 
    	 */
    	lands = new Land[TOWN_MAX_LAND + 1];

    	lands[0] = new Land(0, 0,  0, 39, 39,LandDescription.COIN_AREA,(long) -1);
    	lands[1] = new Land(1, 60, 0, 99, 39,LandDescription.COIN_AREA,(long) -1);   	
    	lands[2] = new Land(2, 0, 60, 39, 99,LandDescription.COIN_AREA,(long) -1);
    	lands[3] = new Land(3, 60, 60, 99, 99,LandDescription.COIN_AREA,(long) -1);
    	lands[4] = new Land(4, 40, 40, 59, 59,LandDescription.COIN_AREA,(long) -1);
    	
    	lands[5] = new Land(5, 10, 40, 11, 59,LandDescription.HOTEL,(long) -1);
    	lands[6] = new Land(6, 70, 40, 71, 59,LandDescription.HOTEL,(long) -1);
    	lands[7] = new Land(7, 40, 10, 59, 11,LandDescription.HOTEL,(long) -1);
    	lands[8] = new Land(8, 40, 70, 59, 71,LandDescription.HOTEL,(long) -1);
    	
    	lands[9] = new Land(9, 30, 40, 31, 59,LandDescription.RESTAURANT,(long) -1);    	
    	lands[10] = new Land(10, 90, 40, 91, 59,LandDescription.RESTAURANT,(long) -1);    	
    	lands[11] = new Land(11, 40, 30, 59, 31,LandDescription.RESTAURANT,(long) -1);    	
    	lands[12] = new Land(12, 40, 90, 59, 91,LandDescription.RESTAURANT,(long) -1);
    	
    	lands[13] = new Land(13, 40, 0, 59, 1, LandDescription.WITNESS_HALL,(long) -1);
    	lands[14] = new Land(14, 40, 2, 48, 2, LandDescription.JUSTICE_HALL,(long) -1);
    	
    	lands[15] = new Land(15, 40, 4, 59, 5, LandDescription.USER_AREA,(long) -1);    	
    	lands[16] = new Land(16, 40, 7, 59, 8, LandDescription.USER_AREA,(long) -1);
    	lands[17] = new Land(17, 40, 13, 59, 14, LandDescription.USER_AREA,(long) -1);
    	lands[18] = new Land(18, 40, 16, 59, 17, LandDescription.USER_AREA,(long) -1);
    	lands[19] = new Land(19, 40, 19, 59, 20, LandDescription.USER_AREA,(long) -1);    	
    	lands[20] = new Land(20, 40, 22, 59, 23, LandDescription.USER_AREA,(long) -1);    	
    	lands[21] = new Land(21, 40, 25, 59, 26, LandDescription.USER_AREA,(long) -1);
    	lands[22] = new Land(22, 40, 33, 59, 34, LandDescription.USER_AREA,(long) -1);
    	lands[23] = new Land(23, 40, 36, 59, 37, LandDescription.USER_AREA,(long) -1);
	
    }
    
	public static TownMap getInstance( ){
		return instance;
	}
	   
    public static Land getLandFromArray(int nLand) {
        return lands[nLand];
    }
    
    public static void addLand(int landId, long assetId) {
    	Land land = lands[landId];
    	land.assetId = assetId;
    	landTable.insert(land);
    }
}
