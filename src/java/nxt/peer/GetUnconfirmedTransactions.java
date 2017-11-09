package nxt.peer;

import nxt.Attachment.AutomatedTransactionsState;
import nxt.Constants;
import nxt.Nxt;
import nxt.Transaction;
import nxt.TransactionType;
import nxt.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class GetUnconfirmedTransactions extends PeerServlet.PeerRequestHandler {

    static final GetUnconfirmedTransactions instance = new GetUnconfirmedTransactions();

    private GetUnconfirmedTransactions() {}


    @Override
    JSONStreamAware processRequest(JSONObject request, Peer peer) {

        JSONObject response = new JSONObject();

        JSONArray transactionsData = new JSONArray();
        try (DbIterator<? extends Transaction> transactions = Nxt.getTransactionProcessor().getAllUnconfirmedTransactions()) {
            while (transactions.hasNext()) {
                Transaction transaction = transactions.next();
                if (transaction.getType() == TransactionType.AutomatedTransactions.AUTOMATED_TRANSACTION_STATE) {
                	AutomatedTransactionsState attachment = (AutomatedTransactionsState) transaction.getAttachment();
                	if (attachment.getATId() >0 && attachment.getATId() <= Constants.MAX_AUTOMATED_TRANSACTION_SYSTEM)
                		continue;
                }
                transactionsData.add(transaction.getJSONObject());
            }
        }
        response.put("unconfirmedTransactions", transactionsData);


        return response;
    }

}
