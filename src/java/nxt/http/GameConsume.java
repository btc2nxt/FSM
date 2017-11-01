package nxt.http;

import nxt.Account;
import nxt.Asset;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import org.json.simple.JSONStreamAware;

import static nxt.http.JSONResponses.MISSING_NAME;
import static nxt.http.JSONResponses.INCORRECT_AMOUNT;
import static nxt.http.JSONResponses.INCORRECT_ASSET;

import javax.servlet.http.HttpServletRequest;

public final class GameConsume extends CreateTransaction {

    static final GameConsume instance = new GameConsume();

    private GameConsume() {
        super(new APITag[] {APITag.GAME, APITag.CREATE_TRANSACTION}, "x", "y", "actionName", "asset", "amountNQT");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        short x = ParameterParser.getCoordinateX(req);
        short y = ParameterParser.getCoordinateY(req);  
		String actionName = req.getParameter("actionName");
		
		if (actionName == null) {
            return MISSING_NAME;
        }
       
        long amountNQT = ParameterParser.getAmountNQT(req); 
		Account consumer = ParameterParser.getSenderAccount(req);
        
		Asset asset = ParameterParser.getAsset(req);

        if (asset.getLandId() <= 0 ) {
            return INCORRECT_ASSET;
        }
        
        Attachment attachment;
        if (actionName.equals("CheckIn")) {
        	if (amountNQT < Constants.GAME_ROOM_RATE || amountNQT > Constants.GAME_ROOM_RATE + Constants.ONE_NXT)
        		return INCORRECT_AMOUNT;
        	attachment = new Attachment.GameCheckIn(x, y, asset.getId(), amountNQT);
        }
        else if (actionName.equals("Eat")) {
        	if (amountNQT < Constants.GAME_MEAL_RATE || amountNQT > Constants.GAME_MEAL_RATE + Constants.ONE_NXT)
        		return INCORRECT_AMOUNT;
        	attachment = new Attachment.GameEat(x, y, asset.getId(), amountNQT);
        }
        else attachment = null;
        
        return createTransaction(req, consumer, Constants.GAME_DIVIDEND_FSM_ID, amountNQT, attachment);
    }

}
