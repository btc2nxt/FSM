package nxt.http;

import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_MACHINECODE_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_MACHINEDATA_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_STACK_LENGTH;
import static nxt.http.JSONResponses.MISSING_NAME;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import nxt.util.Logger;

import org.json.simple.JSONStreamAware;

public final class CreateATProgram extends CreateTransaction {
	static final CreateATProgram instance = new CreateATProgram();
	
	private CreateATProgram() {
		super (new APITag[] {APITag.AT, APITag.CREATE_TRANSACTION}, "atVersion", "name", "description", "machineCode" , "machineData", "variables");
	}
	
	@Override
	JSONStreamAware processRequest (HttpServletRequest req) throws NxtException {
		//String atVersion = req.getParameter("atVersion");		
		String name = req.getParameter("name");
		String description = req.getParameter("description");
		
		if (name == null) {
            return MISSING_NAME;
        }

        name = name.trim();
        if (name.length() > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
            return INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH;
        }
        String normalizedName = name.toLowerCase();
        for (int i = 0; i < normalizedName.length(); i++) {
            if (Constants.ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                return INCORRECT_AUTOMATED_TRANSACTION_NAME;
            }
        }

        if (description != null && description.length() > Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH) {
            return INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION;
        }
        
        byte[] variables = ParameterParser.getVariables( req );
        
        
        /*short machineCodePages = ParameterParser.getMachineCodePages(req);
        if (machineCodePages * Constants.AUTOMATED_TRANSACTION_PAGE_SIZE > Constants.MAX_AUTOMATED_TRANSACTIONS_MACHINECODE_LENGTH) {
			return INCORRECT_AUTOMATED_TRANSACTION_MACHINECODE_LENGTH;
		}*/
        
		byte[] machineCode = ParameterParser.getMachineCode(req);
		//if (machineCode.length > machineCodePages*Constants.AUTOMATED_TRANSACTION_PAGE_SIZE) {
		//	return INCORRECT_AUTOMATED_TRANSACTION_MACHINECODE_LENGTH;
		//}
		
		/*short machineDataPages = ParameterParser.getMachineDataPages(req);
		if (machineDataPages * Constants.AUTOMATED_TRANSACTION_PAGE_SIZE > Constants.MAX_AUTOMATED_TRANSACTIONS_MACHINEDATA_LENGTH) {
			return INCORRECT_AUTOMATED_TRANSACTION_MACHINEDATA_LENGTH;
		}*/
		
		byte[] machineData = ParameterParser.getMachineData(req);
		//if (machineData.length > machineDataPages*Constants.AUTOMATED_TRANSACTION_PAGE_SIZE) {
		//	return INCORRECT_AUTOMATED_TRANSACTION_MACHINEDATA_LENGTH;
		//}
		
		/*short machineStackPages = ParameterParser.getMachineStackPages(req);
		
		if (machineStackPages*Constants.AUTOMATED_TRANSACTION_PAGE_SIZE > Constants.MAX_AUTOMATED_TRANSACTIONS_STACK_LENGTH){
			return INCORRECT_AUTOMATED_TRANSACTION_STACK_LENGTH;
		}
			

		int waitForNumberOfBlocks = ParameterParser.getWaitForNumberOfBlocks(req);
		long minimunFee = ParameterParser.getMinimumFee(req);		
		int sleepBetween = ParameterParser.getSleepBetween(req);	
		
		*/
        Account account = ParameterParser.getSenderAccount(req);
		Attachment attachment = new Attachment.AutomatedTransactionsCreation( name, description, machineCode, machineData, variables );
		
		Logger.logDebugMessage("AT "+ name +" added succesfully ..");
		return createTransaction(req,account,attachment);
	}
	
}
