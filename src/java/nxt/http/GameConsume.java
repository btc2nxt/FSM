package nxt.http;

import nxt.Account;
import nxt.Alias;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import org.json.simple.JSONStreamAware;

import static nxt.http.JSONResponses.INCORRECT_ALIAS_NOTFORSALE;
import static nxt.http.JSONResponses.MISSING_NAME;

import javax.servlet.http.HttpServletRequest;

public final class GameConsume extends CreateTransaction {

    static final GameConsume instance = new GameConsume();

    private GameConsume() {
        super(new APITag[] {APITag.GAME, APITag.CREATE_TRANSACTION}, "x", "y", "actionName","amountNQT");
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
		
        Attachment attachment;
        if (actionName.equals("CheckIn"))
        	attachment = new Attachment.GameCheckIn(x, y);
        else if (actionName.equals("Eat"))
        	attachment = new Attachment.GameEat(x, y);        
        else attachment = null;
        
        return createTransaction(req, consumer, Constants.GAME_DIVIDEND_FSM_ID, amountNQT, attachment);
    }

}
