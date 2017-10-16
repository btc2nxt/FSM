package nxt;

import java.util.Calendar;
import java.util.TimeZone;

public final class Constants {

	public static final boolean isTestnet = Nxt.getBooleanProperty("nxt.isTestnet");
	
	public static final int BLOCK_HEADER_LENGTH = 232;
    public static final int MAX_NUMBER_OF_TRANSACTIONS = 1023;
    public static final int MAX_PAYLOAD_LENGTH = MAX_NUMBER_OF_TRANSACTIONS * 176;
    public static final long MAX_BALANCE_NXT = 1000000000L;
    public static final long ONE_NXT = 100000000;
    public static final long MAX_BALANCE_NQT = MAX_BALANCE_NXT * ONE_NXT;
    public static final long INITIAL_BASE_TARGET = 153722867;
    public static final long MAX_BASE_TARGET = MAX_BALANCE_NXT * INITIAL_BASE_TARGET;
    public static final int MAX_ROLLBACK = Nxt.getIntProperty("nxt.maxRollback");

    static {
        if (MAX_ROLLBACK < 1440) {
            throw new RuntimeException("nxt.maxRollback must be at least 1440");
        }
    }

    public static final int MAX_ALIAS_URI_LENGTH = 1000;
    public static final int MAX_ALIAS_LENGTH = 100;

    public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 1000;
    public static final int MAX_ENCRYPTED_MESSAGE_LENGTH = 1000;

    public static final int MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;

    public static final long MAX_ASSET_QUANTITY_QNT = 1000000000L * 100000000L;
    public static final int MIN_ASSET_NAME_LENGTH = 3;
    public static final int MAX_ASSET_NAME_LENGTH = 10;
    public static final int MAX_ASSET_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_ASSET_TRANSFER_COMMENT_LENGTH = 1000;

    public static final int MAX_POLL_NAME_LENGTH = 100;
    public static final int MAX_POLL_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_POLL_OPTION_LENGTH = 100;
    public static final int MAX_POLL_OPTION_COUNT = 100;

    public static final int MAX_DGS_LISTING_QUANTITY = 1000000000;
    public static final int MAX_DGS_LISTING_NAME_LENGTH = 100;
    public static final int MAX_DGS_LISTING_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_DGS_LISTING_TAGS_LENGTH = 100;
    public static final int MAX_DGS_GOODS_LENGTH = 10240;

    public static final int MAX_HUB_ANNOUNCEMENT_URIS = 100;
    public static final int MAX_HUB_ANNOUNCEMENT_URI_LENGTH = 1000;
    public static final long MIN_HUB_EFFECTIVE_BALANCE = 100000;
    
    public static final int GENESIS_FORGING_BLOCK = isTestnet ? Integer.MAX_VALUE : 2880;
    public static final boolean isOffline = Nxt.getBooleanProperty("nxt.isOffline");
    
    public static final int TRANSPARENT_FORGING_BLOCK = 0;
    public static final int TRANSPARENT_FORGING_BLOCK_7 = Integer.MAX_VALUE;
    public static final int TRANSPARENT_FORGING_BLOCK_8 = isTestnet ? 0 : 89000;
    public static final int NQT_BLOCK = 0;
    public static final int ASSET_EXCHANGE_BLOCK = 0;
    public static final int REFERENCED_TRANSACTION_FULL_HASH_BLOCK = isTestnet ? 0 : 1000;
    public static final int VOTING_SYSTEM_BLOCK = Integer.MAX_VALUE;
    public static final int DIGITAL_GOODS_STORE_BLOCK = isTestnet ? 1 : 89000;
    public static final int PUBLIC_KEY_ANNOUNCEMENT_BLOCK = Integer.MAX_VALUE; //never forced
    public static final int LAST_KNOWN_BLOCK = isTestnet ? 0 : 185100;
    public static final String LAST_KNOWN_BLOCK_ID = isTestnet ? "3754974291626296881" : "4559214760898950463";
    
	protected static final int AUTOMATED_TRANSACTION_BLOCK = 0;
	protected static final long MIN_AUTOMATED_TRANSACTION_FEE = 1000;
	public static final int MAX_AUTOMATED_TRANSACTION_NAME_LENGTH = 30;
	public static final int MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH = 1000 ;
	public static final int MAX_AUTOMATED_TRANSACTIONS_MACHINECODE_LENGTH = 2048;
	public static final int MAX_AUTOMATED_TRANSACTIONS_MACHINEDATA_LENGTH = 2048;
	public static final int MAX_AUTOMATED_TRANSACTIONS_STACK_LENGTH = 512;
	public static final int AUTOMATED_TRANSACTION_PAGE_SIZE = 256;
	public static final int MAX_AUTOMATED_TRANSACTIONS_WAITORSLEEPBLOCKS = 1440;	
	public static final long MAX_AUTOMATED_TRANSACTIONS_MINIMUM_FEE_NQT = 10 * ONE_NXT;	
	public static final int MAX_AUTOMATED_TRANSACTIONS_WAITFORNUMBEROFBLOCKS = 10000;
	public static final int AT_BLOCK_PAYLOAD = MAX_PAYLOAD_LENGTH/2;
	public static final int MAX_AUTOMATED_BLOCK_STEPS = 16;
	public static final long AUTOMATED_TRANSACTIONS_STEP_COST_NQT = 1 * ONE_NXT / 4; 
	public static final long MAX_AUTOMATED_TRANSATIONS_STEP_FEE_NQT = 2 * ONE_NXT;
	public static final int AUTOMATED_TRANSACTION_FINISHED = Integer.MAX_VALUE;
	public static final int MAX_AUTOMATED_TRANSACTION_SYSTEM = 10;	
	
	public static final int MAX_TOWN_XY = 110;
	public static final int MAX_PLAYERS_PER_COORDINATE = 20;
	public static final int MAX_PLAYERS_PER_COORDINATE_WITHIN_BUILDING = 1;
	public static final int MAX_PLAYERS_CAPAITY_OF_BUILDING = 20;
	public static final int GAME_DISTRIBUTE_PACKAGES = 5;	
	public static final long GAME_MEAL_RATE = 25 * ONE_NXT;
	public static final long GAME_ROOM_RATE = 30 * ONE_NXT;
	public static final long GAME_BRICK_RATE = 3 * ONE_NXT;
	public static final long MAX_HOTEL_RESTAURANT_LIFEVALUE = isTestnet ? GAME_BRICK_RATE * 5 : GAME_ROOM_RATE * 90;	
	public static final int GAME_INIT_COLLECT_POWER = 100;
	public static final int GAME_INIT_ATTACK_POWER = 100;
	public static final int GAME_INIT_DEFENSE_VALUE = 100;
	public static final int GAME_INIT_HEALTHY_INDEX = 100;
	
	public static final long GAME_PREDISTRIBUTE_FSM_ID = 1;
	public static final long GAME_AIRDROP_FSM_ID = 2;
	public static final long GAME_DIVIDEND_FSM_ID = 3;
	public static final long GAME_SHARE_FSM_ID = 4;
	public static final String GAME_PREDISTRIBUTE_FSM_NAME = "predistributeFSM";
	public static final String GAME_AIRDROP_FSM_NAME = "airdropFSM";
	public static final String GAME_DIVIDEND_FSM_NAME = "dividendFSM";
	public static final String GAME_SHARE_FSM_NAME = "shareFSM";		
	

    public static final int[] MIN_VERSION = new int[] {1, 2};

    static final long UNCONFIRMED_POOL_DEPOSIT_NQT = (isTestnet ? 50 : 100) * ONE_NXT;

    public static final long EPOCH_BEGINNING;
    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2014);
        calendar.set(Calendar.MONTH, Calendar.JUNE);
        calendar.set(Calendar.DAY_OF_MONTH, 05);
        calendar.set(Calendar.HOUR_OF_DAY, 06);
        calendar.set(Calendar.MINUTE, 21);
        calendar.set(Calendar.SECOND, 19);
        calendar.set(Calendar.MILLISECOND, 83);
        EPOCH_BEGINNING = calendar.getTimeInMillis();
    }

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    public static final int EC_RULE_TERMINATOR = 600; /* cfb: This constant defines a straight edge when "longest chain"
                                                        rule is outweighed by "economic majority" rule; the terminator
                                                        is set as number of seconds before the current time. */

    public static final int EC_BLOCK_DISTANCE_LIMIT = 60;

    private Constants() {} // never

}
