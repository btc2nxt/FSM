package nxt.http;

import nxt.AT;
import nxt.Asset;
import nxt.at.AT_API_Helper;
import nxt.db.DbIterator;
import nxt.util.Convert;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.INCORRECT_ASSET;
import static nxt.http.JSONResponses.UNKNOWN_ASSET;

public final class GetATIds extends APIServlet.APIRequestHandler {

    static final GetATIds instance = new GetATIds();

    private GetATIds() {
        super(new APITag[] {APITag.AT}, "firstIndex", "lastIndex");        
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

    	JSONArray atIds = new JSONArray();
        try (DbIterator<AT> ats = AT.getAllATs(firstIndex, lastIndex)) {
            while (ats.hasNext()) {
                atIds.add(Convert.toUnsignedLong(AT_API_Helper.getLong(ats.next().getId())));
            }
        }
    	
        JSONObject response = new JSONObject();
        response.put("atIds", atIds);
        return response;
    }

}