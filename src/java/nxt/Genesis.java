package nxt;

import java.math.BigInteger;

public final class Genesis {

    public static final long GENESIS_BLOCK_ID = 3754974291626296881L;//1203667376540169366L;
    public static final long CREATOR_ID = new BigInteger("15927676928343209118").longValue() ;
    public static final byte[] CREATOR_PUBLIC_KEY = {
        	-71 ,-95 ,-53 ,116 ,-100 ,-24 ,75 ,-103 ,27 ,17 ,-118 ,82 ,-5 ,-123 ,92 ,15 ,
        	40 ,62 ,-78 ,13 ,123 ,-64 ,-65 ,48 ,-128 ,-109 ,44 ,114 ,-42 ,-110 ,57 ,110 ,   	
    };

    public static final long[] GENESIS_RECIPIENTS = {
            (new BigInteger("15929495520049788271")).longValue(),
            (new BigInteger("15721056878623392063")).longValue(),
            (new BigInteger("1")).longValue()};


    public static final int[] GENESIS_AMOUNTS = {
    	20000000,
    	20000000,
    	960000000
    };

    public static final byte[][] GENESIS_SIGNATURES = {
            {7, 73, -107, -24, 70, -9, -42, 19, -34, -127, -121, 102, 36, 111, -92, -76, 104, -72, -118, 31, -80, -21, -25, -23, 101, -56, 112, 127, 111, 103, 43, 5, -14, 38, 55, 92, -5, 66, -26, 75, 80, 13, -123, -11, -81, 64, 57, 70, -99, -9, 60, 2, -115, 30, 71, -101, 70, -33, -50, -103, 31, -125, -25, -41},
            {-63, -43, -3, -113, -35, -2, -92, -26, -93, -11, -78, 40, 45, 92, 23, -58, 42, 109, -28, -47, -77, -78, 36, 40, -10, 124, -1, 82, 32, 60, -53, 11, 56, -102, 30, 11, -18, 76, 94, 109, 73, -60, 92, -19, 125, 100, 49, 126, 65, -108, -81, -76, -35, 78, -30, 49, -124, -114, 47, 75, 2, -118, -116, -10},
            {-93, 51, -48, 88, 20, 31, -16, -5, -57, -93, 25, -92, 110, 20, 114, 76, 61, 79, -61, -35, -74, -60, -93, -89, 14, -65, 18, 106, 49, 4, -102, 10, 67, -58, 53, -93, 94, 46, 28, -62, 104, 102, 78, -91, 50, -120, -22, -104, 48, -24, 64, -6, -124, -39, 29, 20, -88, -20, 14, 115, -27, -46, 33, 82}
    	};

    public static final byte[] GENESIS_BLOCK_SIGNATURE = new byte[]{
    		-99, 84, -127, -119, 100, -88, -81, 74, 5, 118, -12, 6, -79, 67, -34, 39, 65, 75, 28, 114, -77, 70, 63, 110, -3, 111, 67, -78, -110, -68, -78, 9, 68, -21, 84, -93, -27, -74, -42, 120, -41, -15, 53, 24, -125, -35, 38, -92, -80, 99, -94, -26, 19, 101, 126, -24, 121, -69, 24, -16, -78, 112, -54, -61
    };

    private Genesis() {} // never

}
