package nxt.http;

import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION;
import static nxt.http.JSONResponses.MISSING_NAME;
import static nxt.http.JSONResponses.INCORRECT_AT_RUN_TYPE;

import javax.servlet.http.HttpServletRequest;

import nxt.AT;
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
		String runType = req.getParameter("runType");		
		
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
        
        runType = runType.toUpperCase().trim();
        try {
            AT.ATRunType.valueOf(runType);
        } 
        catch (Exception e) {
            return INCORRECT_AT_RUN_TYPE;
        }      
        
        byte[] properties = ParameterParser.getVariables( req );       
		byte[] machineCode = ParameterParser.getMachineCode(req);
		byte[] machineData = ParameterParser.getMachineData(req);
			
        Account account = ParameterParser.getSenderAccount(req);
		Attachment attachment = new Attachment.AutomatedTransactionsCreation( name, description, runType, machineCode, machineData, properties );
		
		Logger.logDebugMessage("AT "+ name +" created succesfully ..");
		return createTransaction(req,account,attachment);
	}
	
}
