package nxt.http;

import nxt.Constants;
import nxt.Generator;
import nxt.at.AT_Block;
import nxt.at.AT_Controller;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.MISSING_SECRET_PHRASE;
import static nxt.http.JSONResponses.UNKNOWN_ACCOUNT;


public final class StartAT extends APIServlet.APIRequestHandler {

    static final StartAT instance = new StartAT();

    private StartAT() {
        super(new APITag[]  {APITag.AT}, "secretPhrase");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) {

        String secretPhrase = req.getParameter("secretPhrase");
        if (secretPhrase == null) {
            return MISSING_SECRET_PHRASE;
        }

        AT_Controller ct = AT_Controller.startATVirtualMachine(secretPhrase);        

        JSONObject response = new JSONObject();
        response.put("AT fee", 0);
        return response;

    }

    @Override
    boolean requirePost() {
        return true;
    }

}
