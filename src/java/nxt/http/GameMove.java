package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import nxt.util.Convert;
import org.json.simple.JSONStreamAware;

import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH;
import static nxt.http.JSONResponses.MISSING_NAME;

import javax.servlet.http.HttpServletRequest;

public final class GameMove extends CreateTransaction {

    static final GameMove instance = new GameMove();

    private GameMove() {
        super(new APITag[] {APITag.GAME, APITag.CREATE_TRANSACTION}, "x", "y", "actionName");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        short x = ParameterParser.getCoordinateX(req);
        short y = ParameterParser.getCoordinateY(req);  
		String actionName = req.getParameter("actionName");
		
		if (actionName == null) {
            return MISSING_NAME;
        }
       
        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment;
        if (actionName.equals("Collect"))
        	attachment = new Attachment.GameCollect(x, y);
        else if (actionName.equals("Build"))
        	attachment = new Attachment.GameBuild(x, y);        
        else if (actionName.equals("CheckIn"))
        	attachment = new Attachment.GameCheckIn(x, y);
        else attachment = null;
        
        return createTransaction(req, account, attachment);
    }

}
