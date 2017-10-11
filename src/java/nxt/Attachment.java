package nxt;

import nxt.Attachment.EmptyAttachment;
import nxt.NxtException.NotValidException;
import nxt.at.AT_API_Helper;
import nxt.at.AT_Controller;
import nxt.at.AT_Error;
import nxt.at.AT_Exception;
import nxt.at.AT_Transaction;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import nxt.util.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public interface Attachment extends Appendix {

    TransactionType getTransactionType();

    abstract static class AbstractAttachment extends AbstractAppendix implements Attachment {

        private AbstractAttachment(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        private AbstractAttachment(JSONObject attachmentData) {
            super(attachmentData);
        }

        private AbstractAttachment(int version) {
            super(version);
        }

        private AbstractAttachment() {}

        @Override
        final void validate(Transaction transaction) throws NxtException.ValidationException {
            getTransactionType().validateAttachment(transaction);
        }

        @Override
        final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            getTransactionType().apply(transaction, senderAccount, recipientAccount);
        }

    }

    abstract static class EmptyAttachment extends AbstractAttachment {

        private EmptyAttachment() {
            super(0);
        }

        @Override
        final int getMySize() {
            return 0;
        }

        @Override
        final void putMyBytes(ByteBuffer buffer) {
        }

        @Override
        final void putMyJSON(JSONObject json) {
        }

        @Override
        final boolean verifyVersion(byte transactionVersion) {
            return true;
        }

    }

    public final static EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

        @Override
        String getAppendixName() {
            return "OrdinaryPayment";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Payment.ORDINARY;
        }

    };

    // the message payload is in the Appendix
    public final static EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

        @Override
        String getAppendixName() {
            return "ArbitraryMessage";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ARBITRARY_MESSAGE;
        }

    };

	/*
    public static final EmptyAttachment AT_STATE = new EmptyAttachment() {

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.AutomatedTransactions.AUTOMATED_TRANSACTION_STATE; 
		}

		@Override
		String getAppendixName() {
			return "AT State";
		}
		
		
	};
*/
    public final static class MessagingAliasAssignment extends AbstractAttachment {

        private final String aliasName;
        private final String aliasURI;

        MessagingAliasAssignment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH).trim();
            aliasURI = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ALIAS_URI_LENGTH).trim();
        }

        MessagingAliasAssignment(JSONObject attachmentData) {
            super(attachmentData);
            aliasName = (Convert.nullToEmpty((String) attachmentData.get("alias"))).trim();
            aliasURI = (Convert.nullToEmpty((String) attachmentData.get("uri"))).trim();
        }

        public MessagingAliasAssignment(String aliasName, String aliasURI) {
            this.aliasName = aliasName.trim();
            this.aliasURI = aliasURI.trim();
        }

        @Override
        String getAppendixName() {
            return "AliasAssignment";
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length + 2 + Convert.toBytes(aliasURI).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] alias = Convert.toBytes(this.aliasName);
            byte[] uri = Convert.toBytes(this.aliasURI);
            buffer.put((byte)alias.length);
            buffer.put(alias);
            buffer.putShort((short) uri.length);
            buffer.put(uri);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
            attachment.put("uri", aliasURI);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_ASSIGNMENT;
        }

        public String getAliasName() {
            return aliasName;
        }

        public String getAliasURI() {
            return aliasURI;
        }
    }

    public final static class MessagingAliasSell extends AbstractAttachment {

        private final String aliasName;
        private final long priceNQT;

        MessagingAliasSell(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
            this.priceNQT = buffer.getLong();
        }

        MessagingAliasSell(JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
            this.priceNQT = Convert.parseLong(attachmentData.get("priceNQT"));
        }

        public MessagingAliasSell(String aliasName, long priceNQT) {
            this.aliasName = aliasName;
            this.priceNQT = priceNQT;
        }

        @Override
        String getAppendixName() {
            return "AliasSell";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_SELL;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte)aliasBytes.length);
            buffer.put(aliasBytes);
            buffer.putLong(priceNQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
            attachment.put("priceNQT", priceNQT);
        }

        public String getAliasName(){
            return aliasName;
        }

        public long getPriceNQT(){
            return priceNQT;
        }
    }

    public final static class MessagingAliasBuy extends AbstractAttachment {

        private final String aliasName;

        MessagingAliasBuy(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
        }

        MessagingAliasBuy(JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
        }

        public MessagingAliasBuy(String aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        String getAppendixName() {
            return "AliasBuy";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_BUY;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte)aliasBytes.length);
            buffer.put(aliasBytes);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
        }

        public String getAliasName(){
            return aliasName;
        }
    }

    public final static class MessagingPollCreation extends AbstractAttachment {

        private final String pollName;
        private final String pollDescription;
        private final String[] pollOptions;
        private final byte minNumberOfOptions, maxNumberOfOptions;
        private final boolean optionsAreBinary;

        MessagingPollCreation(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.pollName = Convert.readString(buffer, buffer.getShort(), Constants.MAX_POLL_NAME_LENGTH);
            this.pollDescription = Convert.readString(buffer, buffer.getShort(), Constants.MAX_POLL_DESCRIPTION_LENGTH);
            int numberOfOptions = buffer.get();
            if (numberOfOptions > Constants.MAX_POLL_OPTION_COUNT) {
                throw new NxtException.NotValidException("Invalid number of poll options: " + numberOfOptions);
            }
            this.pollOptions = new String[numberOfOptions];
            for (int i = 0; i < numberOfOptions; i++) {
                pollOptions[i] = Convert.readString(buffer, buffer.getShort(), Constants.MAX_POLL_OPTION_LENGTH);
            }
            this.minNumberOfOptions = buffer.get();
            this.maxNumberOfOptions = buffer.get();
            this.optionsAreBinary = buffer.get() != 0;
        }

        MessagingPollCreation(JSONObject attachmentData) {
            super(attachmentData);
            this.pollName = ((String) attachmentData.get("name")).trim();
            this.pollDescription = ((String) attachmentData.get("description")).trim();
            JSONArray options = (JSONArray) attachmentData.get("options");
            this.pollOptions = new String[options.size()];
            for (int i = 0; i < pollOptions.length; i++) {
                pollOptions[i] = ((String) options.get(i)).trim();
            }
            this.minNumberOfOptions = ((Long) attachmentData.get("minNumberOfOptions")).byteValue();
            this.maxNumberOfOptions = ((Long) attachmentData.get("maxNumberOfOptions")).byteValue();
            this.optionsAreBinary = (Boolean) attachmentData.get("optionsAreBinary");
        }

        public MessagingPollCreation(String pollName, String pollDescription, String[] pollOptions, byte minNumberOfOptions,
                                     byte maxNumberOfOptions, boolean optionsAreBinary) {
            this.pollName = pollName;
            this.pollDescription = pollDescription;
            this.pollOptions = pollOptions;
            this.minNumberOfOptions = minNumberOfOptions;
            this.maxNumberOfOptions = maxNumberOfOptions;
            this.optionsAreBinary = optionsAreBinary;
        }

        @Override
        String getAppendixName() {
            return "PollCreation";
        }

        @Override
        int getMySize() {
            int size = 2 + Convert.toBytes(pollName).length + 2 + Convert.toBytes(pollDescription).length + 1;
            for (String pollOption : pollOptions) {
                size += 2 + Convert.toBytes(pollOption).length;
            }
            size +=  1 + 1 + 1;
            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.pollName);
            byte[] description = Convert.toBytes(this.pollDescription);
            byte[][] options = new byte[this.pollOptions.length][];
            for (int i = 0; i < this.pollOptions.length; i++) {
                options[i] = Convert.toBytes(this.pollOptions[i]);
            }
            buffer.putShort((short)name.length);
            buffer.put(name);
            buffer.putShort((short)description.length);
            buffer.put(description);
            buffer.put((byte) options.length);
            for (byte[] option : options) {
                buffer.putShort((short) option.length);
                buffer.put(option);
            }
            buffer.put(this.minNumberOfOptions);
            buffer.put(this.maxNumberOfOptions);
            buffer.put(this.optionsAreBinary ? (byte)1 : (byte)0);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", this.pollName);
            attachment.put("description", this.pollDescription);
            JSONArray options = new JSONArray();
            if (this.pollOptions != null) {
                Collections.addAll(options, this.pollOptions);
            }
            attachment.put("options", options);
            attachment.put("minNumberOfOptions", this.minNumberOfOptions);
            attachment.put("maxNumberOfOptions", this.maxNumberOfOptions);
            attachment.put("optionsAreBinary", this.optionsAreBinary);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.POLL_CREATION;
        }

        public String getPollName() { return pollName; }

        public String getPollDescription() { return pollDescription; }

        public String[] getPollOptions() { return pollOptions; }

        public byte getMinNumberOfOptions() { return minNumberOfOptions; }

        public byte getMaxNumberOfOptions() { return maxNumberOfOptions; }

        public boolean isOptionsAreBinary() { return optionsAreBinary; }

    }

    public final static class MessagingVoteCasting extends AbstractAttachment {

        private final long pollId;
        private final byte[] pollVote;

        MessagingVoteCasting(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.pollId = buffer.getLong();
            int numberOfOptions = buffer.get();
            if (numberOfOptions > Constants.MAX_POLL_OPTION_COUNT) {
                throw new NxtException.NotValidException("Error parsing vote casting parameters");
            }
            this.pollVote = new byte[numberOfOptions];
            buffer.get(pollVote);
        }

        MessagingVoteCasting(JSONObject attachmentData) {
            super(attachmentData);
            this.pollId = Convert.parseUnsignedLong((String)attachmentData.get("pollId"));
            JSONArray vote = (JSONArray)attachmentData.get("vote");
            this.pollVote = new byte[vote.size()];
            for (int i = 0; i < pollVote.length; i++) {
                pollVote[i] = ((Long) vote.get(i)).byteValue();
            }
        }

        public MessagingVoteCasting(long pollId, byte[] pollVote) {
            this.pollId = pollId;
            this.pollVote = pollVote;
        }

        @Override
        String getAppendixName() {
            return "VoteCasting";
        }

        @Override
        int getMySize() {
            return 8 + 1 + this.pollVote.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(this.pollId);
            buffer.put((byte) this.pollVote.length);
            buffer.put(this.pollVote);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("pollId", Convert.toUnsignedLong(this.pollId));
            JSONArray vote = new JSONArray();
            if (this.pollVote != null) {
                for (byte aPollVote : this.pollVote) {
                    vote.add(aPollVote);
                }
            }
            attachment.put("vote", vote);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.VOTE_CASTING;
        }

        public long getPollId() { return pollId; }

        public byte[] getPollVote() { return pollVote; }

    }

    public final static class MessagingHubAnnouncement extends AbstractAttachment {

        private final long minFeePerByteNQT;
        private final String[] uris;

        MessagingHubAnnouncement(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.minFeePerByteNQT = buffer.getLong();
            int numberOfUris = buffer.get();
            if (numberOfUris > Constants.MAX_HUB_ANNOUNCEMENT_URIS) {
                throw new NxtException.NotValidException("Invalid number of URIs: " + numberOfUris);
            }
            this.uris = new String[numberOfUris];
            for (int i = 0; i < uris.length; i++) {
                uris[i] = Convert.readString(buffer, buffer.getShort(), Constants.MAX_HUB_ANNOUNCEMENT_URI_LENGTH);
            }
        }

        MessagingHubAnnouncement(JSONObject attachmentData) throws NxtException.NotValidException {
            super(attachmentData);
            this.minFeePerByteNQT = (Long) attachmentData.get("minFeePerByte");
            try {
                JSONArray urisData = (JSONArray) attachmentData.get("uris");
                this.uris = new String[urisData.size()];
                for (int i = 0; i < uris.length; i++) {
                    uris[i] = (String) urisData.get(i);
                }
            } catch (RuntimeException e) {
                throw new NxtException.NotValidException("Error parsing hub terminal announcement parameters", e);
            }
        }

        public MessagingHubAnnouncement(long minFeePerByteNQT, String[] uris) {
            this.minFeePerByteNQT = minFeePerByteNQT;
            this.uris = uris;
        }

        @Override
        String getAppendixName() {
            return "HubAnnouncement";
        }

        @Override
        int getMySize() {
            int size = 8 + 1;
            for (String uri : uris) {
                size += 2 + Convert.toBytes(uri).length;
            }
            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(minFeePerByteNQT);
            buffer.put((byte) uris.length);
            for (String uri : uris) {
                byte[] uriBytes = Convert.toBytes(uri);
                buffer.putShort((short)uriBytes.length);
                buffer.put(uriBytes);
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("minFeePerByteNQT", minFeePerByteNQT);
            JSONArray uris = new JSONArray();
            Collections.addAll(uris, this.uris);
            attachment.put("uris", uris);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.HUB_ANNOUNCEMENT;
        }

        public long getMinFeePerByteNQT() {
            return minFeePerByteNQT;
        }

        public String[] getUris() {
            return uris;
        }

    }

    public final static class MessagingAccountInfo extends AbstractAttachment {

        private final String name;
        private final String description;

        MessagingAccountInfo(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
        }

        MessagingAccountInfo(JSONObject attachmentData) {
            super(attachmentData);
            this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
        }

        public MessagingAccountInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        String getAppendixName() {
            return "AccountInfo";
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_INFO;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

    }

    public final static class ColoredCoinsAssetIssuance extends AbstractAttachment {

        private final String name;
        private final String description;
        private final long quantityQNT;
        private final byte decimals;
        private final byte landId;

        ColoredCoinsAssetIssuance(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ASSET_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ASSET_DESCRIPTION_LENGTH);
            this.quantityQNT = buffer.getLong();
            this.decimals = buffer.get();
            this.landId = buffer.get();            
        }

        ColoredCoinsAssetIssuance(JSONObject attachmentData) {
            super(attachmentData);
            this.name = (String) attachmentData.get("name");
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
            this.decimals = ((Long) attachmentData.get("decimals")).byteValue();
            this.landId = ((Long) attachmentData.get("landId")).byteValue();
        }

        public ColoredCoinsAssetIssuance(String name, String description, long quantityQNT, byte decimals, byte landId) {
            this.name = name;
            this.description = Convert.nullToEmpty(description);
            this.quantityQNT = quantityQNT;
            this.decimals = decimals;
            this.landId = landId;
        }

        @Override
        String getAppendixName() {
            return "AssetIssuance";
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length + 8 + 1 + 1;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
            buffer.putLong(quantityQNT);
            buffer.put(decimals);
            buffer.put(landId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
            attachment.put("quantityQNT", quantityQNT);
            attachment.put("decimals", decimals);
            attachment.put("landId", landId);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASSET_ISSUANCE;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public byte getDecimals() {
            return decimals;
        }
        
        public byte getLandId() {
            return landId;
        }        
    }

    public final static class ColoredCoinsAssetTransfer extends AbstractAttachment {

        private final long assetId;
        private final long quantityQNT;
        private final String comment;

        ColoredCoinsAssetTransfer(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.assetId = buffer.getLong();
            this.quantityQNT = buffer.getLong();
            this.comment = getVersion() == 0 ? Convert.readString(buffer, buffer.getShort(), Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH) : null;
        }

        ColoredCoinsAssetTransfer(JSONObject attachmentData) {
            super(attachmentData);
            this.assetId = Convert.parseUnsignedLong((String) attachmentData.get("asset"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
            this.comment = getVersion() == 0 ? Convert.nullToEmpty((String) attachmentData.get("comment")) : null;
        }

        public ColoredCoinsAssetTransfer(long assetId, long quantityQNT) {
            this.assetId = assetId;
            this.quantityQNT = quantityQNT;
            this.comment = null;
        }

        @Override
        String getAppendixName() {
            return "AssetTransfer";
        }

        @Override
        int getMySize() {
            return 8 + 8 + (getVersion() == 0 ? (2 + Convert.toBytes(comment).length) : 0);
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(assetId);
            buffer.putLong(quantityQNT);
            if (getVersion() == 0 && comment != null) {
                byte[] commentBytes = Convert.toBytes(this.comment);
                buffer.putShort((short) commentBytes.length);
                buffer.put(commentBytes);
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("asset", Convert.toUnsignedLong(assetId));
            attachment.put("quantityQNT", quantityQNT);
            if (getVersion() == 0) {
                attachment.put("comment", comment);
            }
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASSET_TRANSFER;
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public String getComment() {
            return comment;
        }

    }

    abstract static class ColoredCoinsOrderPlacement extends AbstractAttachment {

        private final long assetId;
        private final long quantityQNT;
        private final long priceNQT;

        private ColoredCoinsOrderPlacement(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.assetId = buffer.getLong();
            this.quantityQNT = buffer.getLong();
            this.priceNQT = buffer.getLong();
        }

        private ColoredCoinsOrderPlacement(JSONObject attachmentData) {
            super(attachmentData);
            this.assetId = Convert.parseUnsignedLong((String) attachmentData.get("asset"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
            this.priceNQT = Convert.parseLong(attachmentData.get("priceNQT"));
        }

        private ColoredCoinsOrderPlacement(long assetId, long quantityQNT, long priceNQT) {
            this.assetId = assetId;
            this.quantityQNT = quantityQNT;
            this.priceNQT = priceNQT;
        }

        @Override
        int getMySize() {
            return 8 + 8 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(assetId);
            buffer.putLong(quantityQNT);
            buffer.putLong(priceNQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("asset", Convert.toUnsignedLong(assetId));
            attachment.put("quantityQNT", quantityQNT);
            attachment.put("priceNQT", priceNQT);
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public long getPriceNQT() {
            return priceNQT;
        }
    }

    public final static class ColoredCoinsAskOrderPlacement extends ColoredCoinsOrderPlacement {

        ColoredCoinsAskOrderPlacement(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        ColoredCoinsAskOrderPlacement(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsAskOrderPlacement(long assetId, long quantityQNT, long priceNQT) {
            super(assetId, quantityQNT, priceNQT);
        }

        @Override
        String getAppendixName() {
            return "AskOrderPlacement";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASK_ORDER_PLACEMENT;
        }

    }

    public final static class ColoredCoinsBidOrderPlacement extends ColoredCoinsOrderPlacement {

        ColoredCoinsBidOrderPlacement(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        ColoredCoinsBidOrderPlacement(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsBidOrderPlacement(long assetId, long quantityQNT, long priceNQT) {
            super(assetId, quantityQNT, priceNQT);
        }

        @Override
        String getAppendixName() {
            return "BidOrderPlacement";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.BID_ORDER_PLACEMENT;
        }

    }

    abstract static class ColoredCoinsOrderCancellation extends AbstractAttachment {

        private final long orderId;

        private ColoredCoinsOrderCancellation(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.orderId = buffer.getLong();
        }

        private ColoredCoinsOrderCancellation(JSONObject attachmentData) {
            super(attachmentData);
            this.orderId = Convert.parseUnsignedLong((String) attachmentData.get("order"));
        }

        private ColoredCoinsOrderCancellation(long orderId) {
            this.orderId = orderId;
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(orderId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("order", Convert.toUnsignedLong(orderId));
        }

        public long getOrderId() {
            return orderId;
        }
    }

    public final static class ColoredCoinsAskOrderCancellation extends ColoredCoinsOrderCancellation {

        ColoredCoinsAskOrderCancellation(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        ColoredCoinsAskOrderCancellation(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsAskOrderCancellation(long orderId) {
            super(orderId);
        }

        @Override
        String getAppendixName() {
            return "AskOrderCancellation";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASK_ORDER_CANCELLATION;
        }

    }

    public final static class ColoredCoinsBidOrderCancellation extends ColoredCoinsOrderCancellation {

        ColoredCoinsBidOrderCancellation(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        ColoredCoinsBidOrderCancellation(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsBidOrderCancellation(long orderId) {
            super(orderId);
        }

        @Override
        String getAppendixName() {
            return "BidOrderCancellation";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.BID_ORDER_CANCELLATION;
        }

    }

    public final static class DigitalGoodsListing extends AbstractAttachment {

        private final String name;
        private final String description;
        private final String tags;
        private final int quantity;
        private final long priceNQT;

        DigitalGoodsListing(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.name = Convert.readString(buffer, buffer.getShort(), Constants.MAX_DGS_LISTING_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH);
            this.tags = Convert.readString(buffer, buffer.getShort(), Constants.MAX_DGS_LISTING_TAGS_LENGTH);
            this.quantity = buffer.getInt();
            this.priceNQT = buffer.getLong();
        }

        DigitalGoodsListing(JSONObject attachmentData) {
            super(attachmentData);
            this.name = (String) attachmentData.get("name");
            this.description = (String) attachmentData.get("description");
            this.tags = (String) attachmentData.get("tags");
            this.quantity = ((Long) attachmentData.get("quantity")).intValue();
            this.priceNQT = Convert.parseLong(attachmentData.get("priceNQT"));
        }

        public DigitalGoodsListing(String name, String description, String tags, int quantity, long priceNQT) {
            this.name = name;
            this.description = description;
            this.tags = tags;
            this.quantity = quantity;
            this.priceNQT = priceNQT;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsListing";
        }

        @Override
        int getMySize() {
            return 2 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length + 2
                        + Convert.toBytes(tags).length + 4 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] nameBytes = Convert.toBytes(name);
            buffer.putShort((short) nameBytes.length);
            buffer.put(nameBytes);
            byte[] descriptionBytes = Convert.toBytes(description);
            buffer.putShort((short) descriptionBytes.length);
            buffer.put(descriptionBytes);
            byte[] tagsBytes = Convert.toBytes(tags);
            buffer.putShort((short) tagsBytes.length);
            buffer.put(tagsBytes);
            buffer.putInt(quantity);
            buffer.putLong(priceNQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
            attachment.put("tags", tags);
            attachment.put("quantity", quantity);
            attachment.put("priceNQT", priceNQT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.LISTING;
        }

        public String getName() { return name; }

        public String getDescription() { return description; }

        public String getTags() { return tags; }

        public int getQuantity() { return quantity; }

        public long getPriceNQT() { return priceNQT; }

    }

    public final static class DigitalGoodsDelisting extends AbstractAttachment {

        private final long goodsId;

        DigitalGoodsDelisting(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.goodsId = buffer.getLong();
        }

        DigitalGoodsDelisting(JSONObject attachmentData) {
            super(attachmentData);
            this.goodsId = Convert.parseUnsignedLong((String)attachmentData.get("goods"));
        }

        public DigitalGoodsDelisting(long goodsId) {
            this.goodsId = goodsId;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsDelisting";
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(goodsId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("goods", Convert.toUnsignedLong(goodsId));
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.DELISTING;
        }

        public long getGoodsId() { return goodsId; }

    }

    public final static class DigitalGoodsPriceChange extends AbstractAttachment {

        private final long goodsId;
        private final long priceNQT;

        DigitalGoodsPriceChange(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.goodsId = buffer.getLong();
            this.priceNQT = buffer.getLong();
        }

        DigitalGoodsPriceChange(JSONObject attachmentData) {
            super(attachmentData);
            this.goodsId = Convert.parseUnsignedLong((String)attachmentData.get("goods"));
            this.priceNQT = Convert.parseLong(attachmentData.get("priceNQT"));
        }

        public DigitalGoodsPriceChange(long goodsId, long priceNQT) {
            this.goodsId = goodsId;
            this.priceNQT = priceNQT;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsPriceChange";
        }

        @Override
        int getMySize() {
            return 8 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(goodsId);
            buffer.putLong(priceNQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("goods", Convert.toUnsignedLong(goodsId));
            attachment.put("priceNQT", priceNQT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.PRICE_CHANGE;
        }

        public long getGoodsId() { return goodsId; }

        public long getPriceNQT() { return priceNQT; }

    }

    public final static class DigitalGoodsQuantityChange extends AbstractAttachment {

        private final long goodsId;
        private final int deltaQuantity;

        DigitalGoodsQuantityChange(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.goodsId = buffer.getLong();
            this.deltaQuantity = buffer.getInt();
        }

        DigitalGoodsQuantityChange(JSONObject attachmentData) {
            super(attachmentData);
            this.goodsId = Convert.parseUnsignedLong((String)attachmentData.get("goods"));
            this.deltaQuantity = ((Long)attachmentData.get("deltaQuantity")).intValue();
        }

        public DigitalGoodsQuantityChange(long goodsId, int deltaQuantity) {
            this.goodsId = goodsId;
            this.deltaQuantity = deltaQuantity;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsQuantityChange";
        }

        @Override
        int getMySize() {
            return 8 + 4;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(goodsId);
            buffer.putInt(deltaQuantity);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("goods", Convert.toUnsignedLong(goodsId));
            attachment.put("deltaQuantity", deltaQuantity);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.QUANTITY_CHANGE;
        }

        public long getGoodsId() { return goodsId; }

        public int getDeltaQuantity() { return deltaQuantity; }

    }

    public final static class DigitalGoodsPurchase extends AbstractAttachment {

        private final long goodsId;
        private final int quantity;
        private final long priceNQT;
        private final int deliveryDeadlineTimestamp;

        DigitalGoodsPurchase(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.goodsId = buffer.getLong();
            this.quantity = buffer.getInt();
            this.priceNQT = buffer.getLong();
            this.deliveryDeadlineTimestamp = buffer.getInt();
        }

        DigitalGoodsPurchase(JSONObject attachmentData) {
            super(attachmentData);
            this.goodsId = Convert.parseUnsignedLong((String)attachmentData.get("goods"));
            this.quantity = ((Long)attachmentData.get("quantity")).intValue();
            this.priceNQT = Convert.parseLong(attachmentData.get("priceNQT"));
            this.deliveryDeadlineTimestamp = ((Long)attachmentData.get("deliveryDeadlineTimestamp")).intValue();
        }

        public DigitalGoodsPurchase(long goodsId, int quantity, long priceNQT, int deliveryDeadlineTimestamp) {
            this.goodsId = goodsId;
            this.quantity = quantity;
            this.priceNQT = priceNQT;
            this.deliveryDeadlineTimestamp = deliveryDeadlineTimestamp;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsPurchase";
        }

        @Override
        int getMySize() {
            return 8 + 4 + 8 + 4;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(goodsId);
            buffer.putInt(quantity);
            buffer.putLong(priceNQT);
            buffer.putInt(deliveryDeadlineTimestamp);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("goods", Convert.toUnsignedLong(goodsId));
            attachment.put("quantity", quantity);
            attachment.put("priceNQT", priceNQT);
            attachment.put("deliveryDeadlineTimestamp", deliveryDeadlineTimestamp);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.PURCHASE;
        }

        public long getGoodsId() { return goodsId; }

        public int getQuantity() { return quantity; }

        public long getPriceNQT() { return priceNQT; }

        public int getDeliveryDeadlineTimestamp() { return deliveryDeadlineTimestamp; }

    }

    public final static class DigitalGoodsDelivery extends AbstractAttachment {

        private final long purchaseId;
        private final EncryptedData goods;
        private final long discountNQT;
        private final boolean goodsIsText;

        DigitalGoodsDelivery(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.purchaseId = buffer.getLong();
            int length = buffer.getInt();
            goodsIsText = length < 0;
            if (length < 0) {
                length &= Integer.MAX_VALUE;
            }
            this.goods = EncryptedData.readEncryptedData(buffer, length, Constants.MAX_DGS_GOODS_LENGTH);
            this.discountNQT = buffer.getLong();
        }

        DigitalGoodsDelivery(JSONObject attachmentData) {
            super(attachmentData);
            this.purchaseId = Convert.parseUnsignedLong((String)attachmentData.get("purchase"));
            this.goods = new EncryptedData(Convert.parseHexString((String)attachmentData.get("goodsData")),
                    Convert.parseHexString((String)attachmentData.get("goodsNonce")));
            this.discountNQT = Convert.parseLong(attachmentData.get("discountNQT"));
            this.goodsIsText = Boolean.TRUE.equals(attachmentData.get("goodsIsText"));
        }

        public DigitalGoodsDelivery(long purchaseId, EncryptedData goods, boolean goodsIsText, long discountNQT) {
            this.purchaseId = purchaseId;
            this.goods = goods;
            this.discountNQT = discountNQT;
            this.goodsIsText = goodsIsText;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsDelivery";
        }

        @Override
        int getMySize() {
            return 8 + 4 + goods.getSize() + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(purchaseId);
            buffer.putInt(goodsIsText ? goods.getData().length | Integer.MIN_VALUE : goods.getData().length);
            buffer.put(goods.getData());
            buffer.put(goods.getNonce());
            buffer.putLong(discountNQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("purchase", Convert.toUnsignedLong(purchaseId));
            attachment.put("goodsData", Convert.toHexString(goods.getData()));
            attachment.put("goodsNonce", Convert.toHexString(goods.getNonce()));
            attachment.put("discountNQT", discountNQT);
            attachment.put("goodsIsText", goodsIsText);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.DELIVERY;
        }

        public long getPurchaseId() { return purchaseId; }

        public EncryptedData getGoods() { return goods; }

        public long getDiscountNQT() { return discountNQT; }

        public boolean goodsIsText() {
            return goodsIsText;
        }

    }

    public final static class DigitalGoodsFeedback extends AbstractAttachment {

        private final long purchaseId;

        DigitalGoodsFeedback(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.purchaseId = buffer.getLong();
        }

        DigitalGoodsFeedback(JSONObject attachmentData) {
            super(attachmentData);
            this.purchaseId = Convert.parseUnsignedLong((String)attachmentData.get("purchase"));
        }

        public DigitalGoodsFeedback(long purchaseId) {
            this.purchaseId = purchaseId;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsFeedback";
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(purchaseId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("purchase", Convert.toUnsignedLong(purchaseId));
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.FEEDBACK;
        }

        public long getPurchaseId() { return purchaseId; }

    }

    public final static class DigitalGoodsRefund extends AbstractAttachment {

        private final long purchaseId;
        private final long refundNQT;

        DigitalGoodsRefund(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.purchaseId = buffer.getLong();
            this.refundNQT = buffer.getLong();
        }

        DigitalGoodsRefund(JSONObject attachmentData) {
            super(attachmentData);
            this.purchaseId = Convert.parseUnsignedLong((String)attachmentData.get("purchase"));
            this.refundNQT = Convert.parseLong(attachmentData.get("refundNQT"));
        }

        public DigitalGoodsRefund(long purchaseId, long refundNQT) {
            this.purchaseId = purchaseId;
            this.refundNQT = refundNQT;
        }

        @Override
        String getAppendixName() {
            return "DigitalGoodsRefund";
        }

        @Override
        int getMySize() {
            return 8 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(purchaseId);
            buffer.putLong(refundNQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("purchase", Convert.toUnsignedLong(purchaseId));
            attachment.put("refundNQT", refundNQT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.DigitalGoods.REFUND;
        }

        public long getPurchaseId() { return purchaseId; }

        public long getRefundNQT() { return refundNQT; }

    }

    public final static class AutomatedTransactionsCreation extends AbstractAttachment{
    	
        private final String name;    
        private final String description;       
        private final String runType;        
        private final byte[] machineCode;
        private final byte[] machineData;
        private final byte[] properties;
        private final int totalPages;
    	
    	
		AutomatedTransactionsCreation(ByteBuffer buffer,
				byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			
			this.name = Convert.readString( buffer , buffer.get() , Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH );
			this.description = Convert.readString( buffer , buffer.getShort() , Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH );
			this.runType = Convert.readString( buffer , buffer.getShort() , 20 );			
			
			short machineCodeAttachmentSize = buffer.getShort();
			machineCode = new byte[machineCodeAttachmentSize];
			buffer.get(machineCode);
			
			short machineDataAttachmentSize = buffer.getShort();
			machineData = new byte[machineDataAttachmentSize];
			buffer.get(machineData);
			
			short propertiesAttachmentSize = buffer.getShort();
			properties = new byte[propertiesAttachmentSize];
			buffer.get(properties);
			
			try {
				this.totalPages = AT_Controller.checkCreationBytes( machineCode, machineData, properties , Nxt.getBlockchain().getHeight() );
			} catch (AT_Exception e) {
				Logger.logErrorMessage( " error checking AT creation Bytes " + e.getMessage() );
				throw new NxtException.NotValidException( e.getMessage() );
			}			
		}

		AutomatedTransactionsCreation(JSONObject attachmentData) throws NxtException.NotValidException {
			super(attachmentData);
			this.name = (String) attachmentData.get("name");
			this.description = (String) attachmentData.get("description");
			this.runType = (String) attachmentData.get("runType");
			this.machineCode = Convert.parseHexString((String)attachmentData.get("machineCode"));
			/*if (machineCode.length > machineCodePages*Constants.AUTOMATED_TRANSACTION_PAGE_SIZE) {
				throw new NxtException.NotValidException("Max mchine code length exceeded: " + machineCode.length);
			}*/
			this.machineData = Convert.parseHexString((String)attachmentData.get("machineData"));
			this.properties = Convert.parseHexString((String)attachmentData.get("properties"));
			
			try {
				this.totalPages = AT_Controller.checkCreationBytes( machineCode, machineData, properties , Nxt.getBlockchain().getHeight() );
			} catch (AT_Exception e) {
				Logger.logErrorMessage( " error checking AT creation Bytes " + e.getMessage() );
				throw new NxtException.NotValidException( e.getMessage() );
			}			

		}
		
		public AutomatedTransactionsCreation(String name, String description, String runType, byte[] machineCode, byte[] machineData, byte[] properties) throws NxtException.NotValidException {		
			this.name = name;
			this.description = description;
			this.runType = runType;
			this.machineCode = machineCode;			
			this.machineData = machineData;
			this.properties = properties;
			
			try {
				this.totalPages = AT_Controller.checkCreationBytes( machineCode, machineData, properties , Nxt.getBlockchain().getHeight() );
			} catch (AT_Exception e) {
				Logger.logErrorMessage( " error checking AT creation Bytes " + e.getMessage() );
				throw new NxtException.NotValidException( e.getMessage() );
			}			
		}
		
		@Override
		public TransactionType getTransactionType() {
			return TransactionType.AutomatedTransactions.AUTOMATED_TRANSACTION_CREATION;
		}

		@Override
		String getAppendixName() {
            return "AutomatedTransactionsCreation";
        }
		@Override
		int getMySize() {
            return 1 + Convert.toBytes( name ).length + 2 + Convert.toBytes( description ).length + 2 + Convert.toBytes( runType ).length 
            		+ 2 + machineCode.length + 2 + machineData.length+ 2 + properties.length;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {        
            byte[] nameBytes = Convert.toBytes( name );            
            buffer.put( ( byte ) nameBytes.length );
            buffer.put( nameBytes );
            byte[] descriptionBytes = Convert.toBytes( description );
            buffer.putShort( ( short ) descriptionBytes.length );
            buffer.put( descriptionBytes );
            byte[] runTypeBytes = Convert.toBytes( runType );
            buffer.putShort( ( short ) runTypeBytes.length );
            buffer.put( runTypeBytes );
            
            buffer.putShort((short) machineCode.length);
            buffer.put(machineCode);
            buffer.putShort((short) machineData.length);
            buffer.put(machineData);
            buffer.putShort((short) properties.length);
            buffer.put(properties);
		}

		@Override
		void putMyJSON(JSONObject attachment) {       
			attachment.put("name", name);
            attachment.put("description", description);
            attachment.put("runType", runType);            
            attachment.put("machineCode", Convert.toHexString(machineCode));
            attachment.put("machineData", Convert.toHexString(machineData));
            attachment.put("properties", Convert.toHexString(properties));            
		}
		
		public int getTotalPages() { return totalPages; }

        public String getName() { return name; }

        public String getDescription() { return description; }
        
        public String getRunType() { return runType; }

		public byte[] getMachineCode() { return machineCode; }
		
		public byte[] getMachineData() { return machineData; }		

        public byte[] getProperties() { return properties;	}
    }
    
    public final static class AutomatedTransactionsState extends AbstractAttachment{
    	
        private final long atId;
        private final short pc;
        private final short steps;
        private final long timeStamp;        
        private final long lastStateId;
        private final int lastRanHeight;
        private final byte[] machineCode;
        private final byte[] machineData;        
    	private final List<AT_Transaction> atPayments;
    	 	
		AutomatedTransactionsState(ByteBuffer buffer,
				byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			
			this.atId = buffer.getLong();
			this.pc = buffer.getShort();
			this.steps = buffer.getShort();			
			this.timeStamp = buffer.getLong();			
			this.lastStateId = buffer.getLong();
			this.lastRanHeight = buffer.getInt();
			this.atPayments = new ArrayList<AT_Transaction>();
			
			byte atPaymentsCount = buffer.get();		
			for ( byte i=0; i < atPaymentsCount ; i++) {
				AT_Transaction tx = new AT_Transaction( AT_API_Helper.getByteArray(buffer.getLong()), buffer.getLong(), null, buffer.getInt(), buffer.getInt());
				this.atPayments.add(tx);
			}

			short machineCodeAttachmentSize = buffer.getShort();
			this.machineCode = new byte[machineCodeAttachmentSize];
			buffer.get(this.machineCode);
			
			short machineDataAttachmentSize = buffer.getShort();
			this.machineData = new byte[machineDataAttachmentSize];
			buffer.get(this.machineData);		
		}

		AutomatedTransactionsState(JSONObject attachmentData) throws NxtException.NotValidException {
			super(attachmentData);
            //this.atId = Convert.parseUnsignedLong((String)attachmentData.get("atId"));
            this.atId = Convert.parseLong(attachmentData.get("atId"));            
            this.pc = ((Long)attachmentData.get("pc")).shortValue();
            this.steps = ((Long)attachmentData.get("steps")).shortValue();
            this.timeStamp =  Convert.parseLong(attachmentData.get("timeStamp"));
            this.lastStateId =  Convert.parseLong(attachmentData.get("lastStateId"));
            this.lastRanHeight = ((Long)attachmentData.get("lastRanHeight")).intValue();

			this.atPayments = new ArrayList<AT_Transaction>();
            JSONArray atPaymentArray = (JSONArray)attachmentData.get("atPayments");            
            if (atPaymentArray.size() >0) {
                for (Object o : atPaymentArray) {
                    JSONObject atPayment = (JSONObject) o;
                	AT_Transaction tx = new AT_Transaction(AT_API_Helper.getByteArray(Convert.parseLong(atPayment.get("recipientId"))), Convert.parseLong(atPayment.get("amount")), null, ((Long)attachmentData.get("x")).intValue(), ((Long)attachmentData.get("y")).intValue());
    				this.atPayments.add(tx);
                }
    			
            }   
            
			this.machineCode = Convert.parseHexString((String)attachmentData.get("machineCode"));
			this.machineData = Convert.parseHexString((String)attachmentData.get("machineData"));
            
		}
		
		public AutomatedTransactionsState(long atId, short pc, short steps, long timeStamp, long lastStateId, int lastRanHeight, List<AT_Transaction> transactions, byte[] machineCode, byte[] machineData) throws NxtException.NotValidException {		
			this.atId = atId;
			this.pc = pc;
			this.steps = steps;			
			this.timeStamp = timeStamp;
			this.lastStateId = lastStateId;
			this.lastRanHeight = lastRanHeight;
			this.atPayments = transactions;
			this.machineCode = machineCode;
			this.machineData = machineData;						
		}
		
		@Override
		public TransactionType getTransactionType() {
			return TransactionType.AutomatedTransactions.AUTOMATED_TRANSACTION_STATE;
		}

		@Override
		String getAppendixName() {
            return "AutomatedTransactionState";
        }
		@Override
		int getMySize() {
            return 8 + 2 + 2 + 8 + 8 + 4 + 1 + (atPayments == null ? 0 : atPayments.size()) *24 + 2 + (machineCode ==null ? 0 : machineCode.length) + 2 + (machineData ==null ? 0 : machineData.length);
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {        
            buffer.putLong((long) atId);            
			buffer.putShort((short) pc);
            buffer.putShort((short) steps);
            buffer.putLong((long) timeStamp);            
            buffer.putLong((long) lastStateId);
            buffer.putInt((int) lastRanHeight);            
    		if ( atPayments != null){            
    			buffer.put((byte) atPayments.size());            
    			for (AT_Transaction tx : atPayments )
    			{
    				buffer.putLong((long) tx.getRecipientIdLong());
    				buffer.putLong((long) tx.getAmount());		    			
    				buffer.putInt((int) tx.getX());
    				buffer.putInt((int) tx.getY());
    			}
    		}
            buffer.putShort((short) (machineCode == null ? 0 :machineCode.length));
            if (machineCode != null) {
                buffer.put(machineCode);	
            }
            buffer.putShort((short) (machineData ==null ? 0 : machineData.length));
            if (machineData != null) {            
            	buffer.put(machineData);
            }
            
		}

		@Override
		void putMyJSON(JSONObject attachment) {       
			attachment.put("atId", atId);
            attachment.put("pc", pc);
            attachment.put("steps", steps);
            attachment.put("timeStamp", timeStamp);           
            attachment.put("lastStateId", lastStateId);
            attachment.put("lastRanHeight", lastRanHeight);  
            JSONArray atPaymentJSONArray = new JSONArray();
            attachment.put("atPayments", atPaymentJSONArray);
    		if ( atPayments != null){
    			for (AT_Transaction tx : atPayments )
    			{
                	JSONObject json = new JSONObject();
                	json.put("recipientId", tx.getRecipientIdLong());
                	json.put("amount", tx.getAmount());
                	json.put("x", tx.getX());
                	json.put("y", tx.getY());
        			atPaymentJSONArray.add(json);                	
    			}
    		}
            attachment.put("machineCode", Convert.toHexString(machineCode));
            attachment.put("machineData", Convert.toHexString(machineData));    		
            
		}
		
		public long getATId() { return atId; }

        public short getPc() { return pc; }

        public short getSteps() { return steps; }

		public long getTimeStamp() { return timeStamp; }        
		
		public long getLastStateId() { return lastStateId; }
		
		public int getLastRanHeight() { return lastRanHeight; }
		
		public List<AT_Transaction> getATPayments() { return atPayments; }
		
		public byte[] getMachineCode() { return machineCode; }
		
		public byte[] getMachineData() { return machineData; }		
		
    }

    public final static class AutomatedTransactionsUpdate extends AbstractAttachment{

		@Override
		public TransactionType getTransactionType() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		String getAppendixName() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		int getMySize() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			// TODO Auto-generated method stub
			
		}

		@Override
		void putMyJSON(JSONObject json) {
			// TODO Auto-generated method stub
			
		}
    	
    }
       
    public final static class AccountControlEffectiveBalanceLeasing extends AbstractAttachment {

        private final short period;

        AccountControlEffectiveBalanceLeasing(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.period = buffer.getShort();
        }

        AccountControlEffectiveBalanceLeasing(JSONObject attachmentData) {
            super(attachmentData);
            this.period = ((Long) attachmentData.get("period")).shortValue();
        }

        public AccountControlEffectiveBalanceLeasing(short period) {
            this.period = period;
        }

        @Override
        String getAppendixName() {
            return "EffectiveBalanceLeasing";
        }

        @Override
        int getMySize() {
            return 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putShort(period);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("period", period);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
        }

        public short getPeriod() {
            return period;
        }
    }
    
    public final static class GamePreDistribute extends AbstractAttachment {

        //x1,y1,x2,y2...
    	private final byte[] coordinate;

        GamePreDistribute(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
			this.coordinate = new byte[Constants.GAME_DISTRIBUTE_PACKAGES * 2];
			buffer.get(this.coordinate);
        }

        GamePreDistribute(JSONObject attachmentData) {
            super(attachmentData);
            this.coordinate = Convert.parseHexString((String)attachmentData.get("coordinate"));
        }

        public GamePreDistribute(byte[] coordinate) throws NxtException.NotValidException {
            this.coordinate = coordinate;
        }

        @Override
        String getAppendixName() {
            return "PreDistritution";
        }

        @Override
        int getMySize() {
            return Constants.GAME_DISTRIBUTE_PACKAGES * 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put(coordinate);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("coordinate", Convert.toHexString(coordinate));
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.AutomatedTransactions.PREDISTRIBUTE;
        }

        public byte[] getCoordinate() {
            return coordinate;
        }

    }
    
    abstract static class GameMove extends AbstractAttachment {
    	private final short xCoordinate;
    	private final short yCoordinate;

    	GameMove(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
			this.xCoordinate = buffer.getShort();
			this.yCoordinate = buffer.getShort();
        }

    	GameMove(JSONObject attachmentData) {
            super(attachmentData);
            this.xCoordinate = ((Long) attachmentData.get("xCoordinate")).shortValue();
            this.yCoordinate = ((Long) attachmentData.get("yCoordinate")).shortValue();
        }

        GameMove(short xCoordinate, short yCoordinate) throws NxtException.NotValidException {
            this.xCoordinate = xCoordinate;
            this.yCoordinate = yCoordinate;
        }

        @Override
        int getMySize() {
            return 2 + 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putShort(xCoordinate);
            buffer.putShort(yCoordinate);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
        	attachment.put("xCoordinate", xCoordinate);
        	attachment.put("yCoordinate", yCoordinate);
        }

        @Override
        public String getAppendixName() {
            return "";
        }
        
        public short getXCoordinate() {
            return xCoordinate;
        }

        public short getYCoordinate() {
            return yCoordinate;
        }    	
    }
    
    public final static class GameBeWorker extends GameMove {

    	GameBeWorker(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
        }

    	GameBeWorker(JSONObject attachmentData) {
            super(attachmentData);
        }

    	public GameBeWorker(short xCoordinate, short yCoordinate) throws NxtException.NotValidException {
            super(xCoordinate, yCoordinate);
        }

    	@Override
        public String getAppendixName() {
            return "Be_Worker";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Game.BE_WORKER;
        }

    }
    
    public final static class GameBeCollector extends GameMove {
    	GameBeCollector(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
        }

    	GameBeCollector(JSONObject attachmentData) {
            super(attachmentData);
        }

    	public GameBeCollector(short xCoordinate, short yCoordinate) throws NxtException.NotValidException {
            super(xCoordinate, yCoordinate);
        }
        
    	@Override
        public String getAppendixName() {
            return "Be_Collector";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Game.BE_COLLECTOR;
        }

    }

    public final static class GameCollect extends GameMove {

    	GameCollect(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
        }

    	GameCollect(JSONObject attachmentData) {
            super(attachmentData);
        }
    	
    	public GameCollect(short xCoordinate, short yCoordinate) throws NxtException.NotValidException {
            super(xCoordinate, yCoordinate);
        }

    	@Override
        public String getAppendixName() {
            return "Collect";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Game.COLLECT;
        }
    }
    
    public final static class GameBuild extends GameMove {
    	GameBuild(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
        }

    	GameBuild(JSONObject attachmentData) {
            super(attachmentData);
        }

    	public GameBuild(short xCoordinate, short yCoordinate) throws NxtException.NotValidException {
            super(xCoordinate, yCoordinate);
        }
        
    	@Override
        public String getAppendixName() {
            return "Build";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Game.BUILD;
        }

    }
    
    public final static class GameCheckIn extends GameMove {

    	GameCheckIn(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
        }

    	GameCheckIn(JSONObject attachmentData) {
            super(attachmentData);
        }

      	public GameCheckIn(short xCoordinate, short yCoordinate) throws NxtException.NotValidException {
            super(xCoordinate, yCoordinate);
        }
    	
    	@Override
        public String getAppendixName() {
            return "Check_In";
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Game.CHECK_IN;
        }

    }
    
    public final static class FinanceTicker extends AbstractAttachment{
    	
        private final String market;
    	private final List<Ticker> tickers;
    	 	
    	FinanceTicker(ByteBuffer buffer,
				byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			
			this.market = Convert.readString(buffer, buffer.get(), 20);
			this.tickers = new ArrayList<Ticker>();
			
			//records number
			byte tickersLength = buffer.get();		
			for ( byte i=0; i < tickersLength ; i++) {
				Ticker tk = new Ticker( buffer.getLong(), Convert.readString(buffer, buffer.getShort(), 20), Convert.readString(buffer, buffer.getShort(), 20), 
						(int)buffer.getShort(), buffer.getLong(), buffer.getLong(), buffer.getLong(),
						buffer.getLong(), buffer.getLong(), buffer.getLong(), buffer.getLong(), (int)buffer.getShort());
				this.tickers.add(tk);
			}
		}
    	
    	FinanceTicker(JSONObject attachmentData) {
            super(attachmentData);
            
            this.market = (String) attachmentData.get("name");
            this.tickers = new ArrayList<Ticker>();
            
            JSONArray tickerArray = (JSONArray) attachmentData.get("tickers");
            Iterator i = tickerArray.iterator();

            while (i.hasNext()) {
                JSONObject attachmentDataSub = (JSONObject) i.next();
             	//public Ticker(long id, String name, String symbol, int rank, long price_usd, long price_btc, long volume_usd_24h,
            	//		long market_cap_usd, long available_supply, long total_supply, long percent_change_1h, int last_updated) {
                
                Ticker tk = new Ticker( Convert.parseLong(attachmentDataSub.get("id")), (String) attachmentDataSub.get("name"), (String) attachmentDataSub.get("symbol"), 
                		(int)attachmentDataSub.get("rank"), Convert.parseLong(attachmentDataSub.get("price_usd")), Convert.parseLong(attachmentDataSub.get("price_btc")),
                		Convert.parseLong(attachmentDataSub.get("volume_usd_24h")), Convert.parseLong(attachmentDataSub.get("market_cap_usd")), Convert.parseLong(attachmentDataSub.get("available_supply")),
                		Convert.parseLong(attachmentDataSub.get("total_supply")), Convert.parseLong(attachmentDataSub.get("percent_change_1h")), (int)attachmentDataSub.get("last_updated"));                		
                		
				this.tickers.add(tk);
				
            }            

        }

        public FinanceTicker(String market, List<Ticker> tickers) {
            this.market = market;
            this.tickers = tickers;
        }

        @Override
        String getAppendixName() {
            return "FinanceTicker";
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(market).length + 2 + 8 + 1;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.market);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("market", market);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASSET_ISSUANCE;
        }

        public String getMarket() {
            return market;
        }

        public List<Ticker> getTickers() {
            return tickers;
        }
    }

}

