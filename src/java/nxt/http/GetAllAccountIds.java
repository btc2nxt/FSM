package nxt.http;

import nxt.Account;
import nxt.db.DbIterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllAccountIds extends APIServlet.APIRequestHandler {

    static final GetAllAccountIds instance = new GetAllAccountIds();

    private GetAllAccountIds() {
        super(new APITag[] {APITag.ACCOUNTS});
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = new JSONObject();
        JSONArray accountJSONArray = new JSONArray();
        response.put("accounts", accountJSONArray);
        try (DbIterator<Account> accounts = Account.getAllAccounts(0, Integer.MAX_VALUE)) {
            while (accounts.hasNext()) {
            	JSONObject json = new JSONObject();
                JSONData.putAccount(json, "account", accounts.next().getId());
                accountJSONArray.add(json);
            }
        }
        return response;
    }

}
