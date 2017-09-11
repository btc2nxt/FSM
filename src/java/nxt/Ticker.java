/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.

 *
 * Some portion .. Copyright (c) 2015 FSM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.

 */

package nxt;

import nxt.db.DbKey;

public class Ticker{
	
    private static final DbKey.LongKeyFactory<Ticker> tickerDbKeyFactory = new DbKey.LongKeyFactory<Ticker>("id") {

        @Override
        public DbKey newKey(Ticker ticker) {
            return ticker.dbKey;
        }

    };
	
    private long id; 
    private final DbKey dbKey;    
    private String name; 
    private String symbol; 
    private int rank; 
    private long price_usd; 
    private long price_btc; 
    private long volume_usd_24h; 
    private long market_cap_usd; 
    private long available_supply; 
    private long total_supply; 
    private long percent_change_1h; 
    private int last_updated;
    
 	public Ticker(long id, String name, String symbol, int rank, long price_usd, long price_btc, long volume_usd_24h,
			long market_cap_usd, long available_supply, long total_supply, long percent_change_1h, int last_updated) {
		super();
		this.id = id;
		this.dbKey = tickerDbKeyFactory.newKey(this.id);
		this.name = name;
		this.symbol = symbol;
		this.rank = rank;
		this.price_usd = price_usd;
		this.price_btc = price_btc;
		this.volume_usd_24h = volume_usd_24h;
		this.market_cap_usd = market_cap_usd;
		this.available_supply = available_supply;
		this.total_supply = total_supply;
		this.percent_change_1h = percent_change_1h;
		this.last_updated = last_updated;
	}	
}
