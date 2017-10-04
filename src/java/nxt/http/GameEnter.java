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

public final class GameEnter extends CreateTransaction {

    static final GameEnter instance = new GameEnter();

    private GameEnter() {
        super(new APITag[] {APITag.GAME, APITag.CREATE_TRANSACTION}, "x", "y", "statusName","map");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        short x = ParameterParser.getCoordinateX(req);
        short y = ParameterParser.getCoordinateY(req);  
		String name = req.getParameter("name");
		String map = req.getParameter("map");
		
		if (name == null) {
            return MISSING_NAME;
        }

        name = name.trim();
        if (name.length() > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
            return INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH;
        }
        
        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment;
        if (name.equals("Collector"))
        	attachment = new Attachment.GameBeCollector(x, y);
        else if (name.equals("Worker"))
        	attachment = new Attachment.GameBeWorker(x, y);
        else attachment = null;
        
        return createTransaction(req, account, attachment);
    }

}
