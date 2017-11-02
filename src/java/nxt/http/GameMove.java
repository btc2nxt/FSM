package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.NxtException;
import org.json.simple.JSONStreamAware;

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
        if (actionName.equals("Walk"))
        	attachment = new Attachment.GameWalk(x, y);        
        else if (actionName.equals("Quit"))
        	attachment = new Attachment.GameQuit(x, y);        
        else attachment = null;
        
        return createTransaction(req, account, attachment);
    }

}
