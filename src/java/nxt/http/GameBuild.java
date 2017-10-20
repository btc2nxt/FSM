package nxt.http;

import nxt.Account;
import nxt.Asset;
import nxt.Attachment;
import nxt.NxtException;
import org.json.simple.JSONStreamAware;

import static nxt.http.JSONResponses.INCORRECT_ASSET;

import javax.servlet.http.HttpServletRequest;

public final class GameBuild extends CreateTransaction {

    static final GameBuild instance = new GameBuild();

    private GameBuild() {
        super(new APITag[] {APITag.GAME, APITag.CREATE_TRANSACTION}, "x", "y", "asset");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        short x = ParameterParser.getCoordinateX(req);
        short y = ParameterParser.getCoordinateY(req);  
       
        Account account = ParameterParser.getSenderAccount(req);
        Asset asset = ParameterParser.getAsset(req);

        if (asset.getLandId() <= 0 ) {
            return INCORRECT_ASSET;
        }
        
        Attachment attachment;
       	attachment = new Attachment.GameBuild(x, y, asset.getId());        
        
        return createTransaction(req, account, attachment);
    }

}
