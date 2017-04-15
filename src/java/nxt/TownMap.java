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
    				this.playersPerPoint = Constants.MAX_PLAYERS_PER_COORDINATE;
    				break;
    			case BUILDING_AREA:
    				this.playersPerPoint = Constants.MAX_PLAYERS_PER_COORDINATE_WITHIN_BUILDING;
    				this.availablePoints = Constants.MAX_PLAYERS_CAPAITY_OF_BUILDING;
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
    	townX1 = 110;
    	townY1 = 110;
    	
    	//there are 72 buildings, 7 coin areas.ignore walls
    	lands = new Land[79];

    	//block 1-1 from (1,41) to (49,49), total 10 buildings
    	lands[0] = new Land( 1, 41,  4, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[1] = new Land( 6, 41,  9, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[2] = new Land(11, 41, 14, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[3] = new Land(16, 41, 19, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[4] = new Land(21, 41, 24, 49,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[5] = new Land(16, 41, 29, 49,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[6] = new Land(31, 41, 34, 49,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[7] = new Land(36, 41, 39, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[8] = new Land(41, 41, 44, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[9] = new Land(46, 41, 49, 49,LandDescription.BUILDING_AREA,(long) -1);
    	
    	//block 1-2 from (41,1) to (49,39), total 8 buildings
    	lands[10] = new Land(41,  1, 49,  4,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[11] = new Land(41,  6, 49,  9,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[12] = new Land(41, 11, 49, 14,LandDescription.BUILDING_AREA,(long) -1);
    	lands[13] = new Land(41, 16, 49, 19,LandDescription.BUILDING_AREA,(long) -1);
    	lands[14] = new Land(41, 21, 49, 24,LandDescription.BUILDING_AREA,(long) -1);
    	lands[15] = new Land(41, 26, 49, 29,LandDescription.BUILDING_AREA,(long) -1);
    	lands[16] = new Land(41, 31, 49, 34,LandDescription.BUILDING_AREA,(long) -1);
    	lands[17] = new Land(41, 36, 49, 39,LandDescription.BUILDING_AREA,(long) -1);

    	//block 2-1 from (61,41) to (109,49), total 10 buildings
    	lands[18] = new Land(61, 41, 64, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[19] = new Land(66, 41, 69, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[20] = new Land(71, 41, 74, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[21] = new Land(76, 41, 79, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[22] = new Land(81, 41, 84, 49,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[23] = new Land(86, 41, 89, 49,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[24] = new Land(91, 41, 94, 49,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[25] = new Land(96, 41, 99, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[26] = new Land(101, 41, 104, 49,LandDescription.BUILDING_AREA,(long) -1);
    	lands[27] = new Land(106, 41, 109, 49,LandDescription.BUILDING_AREA,(long) -1);
    	
    	//block 2-2 from (61,1) to (69,39), total 8 buildings
    	lands[28] = new Land(61,  1, 69,  4,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[29] = new Land(61,  6, 69,  9,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[30] = new Land(61, 11, 69, 14,LandDescription.BUILDING_AREA,(long) -1);
    	lands[31] = new Land(61, 16, 69, 19,LandDescription.BUILDING_AREA,(long) -1);
    	lands[32] = new Land(61, 21, 69, 24,LandDescription.BUILDING_AREA,(long) -1);
    	lands[33] = new Land(61, 26, 69, 29,LandDescription.BUILDING_AREA,(long) -1);
    	lands[34] = new Land(61, 31, 69, 34,LandDescription.BUILDING_AREA,(long) -1);
    	lands[35] = new Land(61, 36, 69, 39,LandDescription.BUILDING_AREA,(long) -1);
    	
    	//block 3-1 from (1,61) to (49,69), total 10 buildings
    	lands[36] = new Land( 1, 61,  4, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[37] = new Land( 6, 61,  9, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[38] = new Land(11, 61, 14, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[39] = new Land(16, 61, 19, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[40] = new Land(21, 61, 24, 69,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[41] = new Land(16, 61, 29, 69,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[42] = new Land(31, 61, 34, 69,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[43] = new Land(36, 61, 39, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[44] = new Land(41, 61, 44, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[45] = new Land(46, 61, 49, 69,LandDescription.BUILDING_AREA,(long) -1);
    	
    	//block 3-2 from (41,71) to (49,109), total 8 buildings
    	lands[46] = new Land(41, 71, 49, 74,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[47] = new Land(41, 76, 49, 79,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[48] = new Land(41, 81, 49, 84,LandDescription.BUILDING_AREA,(long) -1);
    	lands[49] = new Land(41, 86, 49, 89,LandDescription.BUILDING_AREA,(long) -1);
    	lands[50] = new Land(41, 91, 49, 94,LandDescription.BUILDING_AREA,(long) -1);
    	lands[51] = new Land(41, 96, 49, 99,LandDescription.BUILDING_AREA,(long) -1);
    	lands[52] = new Land(41, 101, 49, 104,LandDescription.BUILDING_AREA,(long) -1);
    	lands[53] = new Land(41, 106, 49, 109,LandDescription.BUILDING_AREA,(long) -1);

    	//block 4-1 from (61,61) to (109,69), total 10 buildings
    	lands[54] = new Land(61, 61, 64, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[55] = new Land(66, 61, 69, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[56] = new Land(71, 61, 74, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[57] = new Land(76, 61, 79, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[58] = new Land(81, 61, 84, 69,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[59] = new Land(86, 61, 89, 69,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[60] = new Land(91, 61, 94, 69,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[61] = new Land(96, 61, 99, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[62] = new Land(101, 61, 104, 69,LandDescription.BUILDING_AREA,(long) -1);
    	lands[63] = new Land(106, 61, 109, 69,LandDescription.BUILDING_AREA,(long) -1);
    	
    	//block 4-2 from (61,71) to (69,109), total 8 buildings
    	lands[64] = new Land(61, 71, 69, 74,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[65] = new Land(61, 76, 69, 79,LandDescription.BUILDING_AREA,(long) -1);    	
    	lands[66] = new Land(61, 81, 69, 84,LandDescription.BUILDING_AREA,(long) -1);
    	lands[67] = new Land(61, 86, 69, 89,LandDescription.BUILDING_AREA,(long) -1);
    	lands[68] = new Land(61, 91, 69, 94,LandDescription.BUILDING_AREA,(long) -1);
    	lands[69] = new Land(61, 96, 69, 99,LandDescription.BUILDING_AREA,(long) -1);
    	lands[70] = new Land(61, 101, 69, 104,LandDescription.BUILDING_AREA,(long) -1);
    	lands[71] = new Land(61, 106, 69, 109,LandDescription.BUILDING_AREA,(long) -1);
    	
    	//7 coin areas
    	lands[72] = new Land( 1,  1, 39, 39,LandDescription.COIN_AREA,(long) -1);    	
    	lands[73] = new Land(71,  1, 109, 39,LandDescription.COIN_AREA,(long) -1);    	
    	lands[74] = new Land( 1, 71, 39, 109,LandDescription.COIN_AREA,(long) -1);
    	lands[75] = new Land(71, 71, 109, 109,LandDescription.COIN_AREA,(long) -1);
    	lands[76] = new Land( 1, 51, 109, 59,LandDescription.COIN_AREA,(long) -1);
    	lands[77] = new Land(51,  1, 59, 50,LandDescription.COIN_AREA,(long) -1);
    	lands[78] = new Land(51, 60, 59, 109,LandDescription.COIN_AREA,(long) -1);    	
    	
    }
    

}
