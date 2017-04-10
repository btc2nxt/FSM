package nxt;

import nxt.Constants;

public final class TownMap {
    public static enum LandDescription {
        WALL_AREA, COIN_AREA, BUILDING_AREA
    }	
    
    public class Land {
    	private int x;
    	private int y;
    	private int x1;
    	private int y1;
    	private LandDescription landType;
    	private int playersPerPoint;
    	private int availablePoints;
    	private long accountId;
    	
    	public Land(int x, int y, int x1, int y1, LandDescription landType, long accountId) {
    		this.x = x;
    		this.y = y;
    		this.x1 = x1;
    		this.y1 = y1;
    		this.landType = landType;
    		switch (this.landType) {
    			case COIN_AREA:
    				this.playersPerPoint = Constants.MAX_PLAYERS_PER_POINT;
    				break;
    			case BUILDING_AREA:
    				this.playersPerPoint = 1;
    				this.availablePoints = Constants.MAX_BUILDING_POINT;
    				break;
    			default:
    				this.playersPerPoint = 0;
    		}
    				
    		this.accountId = accountId;
    	}
    }
    
    public static Land[] lands;
    public static int townX;
    public static int townY;
    public static int townX1;
    public static int townY1;
    
    private TownMap() {
    	townX = 0;
    	townY = 0;
    	townX1 = 100;
    	townY1 = 100;
    	
    	lands = new Land[74];
    	lands[0] = new Land(0,0,10,10,LandDescription.COIN_AREA,(long) -1);
    	lands[1] = new Land(0,0,10,10,LandDescription.COIN_AREA,(long) -1);
    	lands[2] = new Land(0,0,10,10,LandDescription.COIN_AREA,(long) -1);
    }
    

}
