package nxt.http;

import nxt.AT;
import nxt.Account;
import nxt.Asset;
import nxt.NxtException;
import nxt.db.DbIterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

public final class GetPredistributeCoordinates extends APIServlet.APIRequestHandler {

    static final GetPredistributeCoordinates instance = new GetPredistributeCoordinates();

    private GetPredistributeCoordinates() {
        super(new APITag[] {APITag.AT}, "at"); 
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        AT at = ParameterParser.getAT(req);
        long atId = at.getLongId();
        AT.ATState atState = AT.getATStateById(atId);
        
        JSONObject response = new JSONObject();
        JSONArray xyJSONArray = new JSONArray();
        
        response.put("coordinates", xyJSONArray);

        try (DbIterator<AT.ATPayment> atPayments = AT.getATPaymentByStateId(atState.getId(),0, 5)) {
        	while (atPayments.hasNext()) {
                	xyJSONArray.add(JSONData.atPayment(atPayments.next()));
            }
        }
        return response;
    }

}
