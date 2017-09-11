package nxt;

import nxt.at.AT_Controller;
import nxt.crypto.Crypto;
import nxt.db.DerivedDbTable;
import nxt.db.FilteringIterator;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.ThreadPool;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

final class BlockchainProcessorImpl implements BlockchainProcessor {

    private static final BlockchainProcessorImpl instance = new BlockchainProcessorImpl();

    private static final int [] heigthsToCheck=Constants.isTestnet ? new int[] {100, 1000, Constants.LAST_KNOWN_BLOCK} 
    	: new int[] {1000, 89010, 175000, Constants.LAST_KNOWN_BLOCK};
    
    private static final String [] previousBlockId=Constants.isTestnet ? new String[] {"4943346385398616171", "5340891100369695005",Constants.LAST_KNOWN_BLOCK_ID} 
    	: new String[] {"4002154554210035192", "15459960525285419228", "12715306889366114804", Constants.LAST_KNOWN_BLOCK_ID};
    
    static BlockchainProcessorImpl getInstance() {
        return instance;
    }

    private final BlockchainImpl blockchain = BlockchainImpl.getInstance();

    private final List<DerivedDbTable> derivedTables = new CopyOnWriteArrayList<>();
    private final boolean trimDerivedTables = Nxt.getBooleanProperty("nxt.trimDerivedTables");
    private volatile int lastTrimHeight;

    private final Listeners<Block, Event> blockListeners = new Listeners<>();
    private volatile Peer lastBlockchainFeeder;
    private volatile int lastBlockchainFeederHeight;
    private volatile boolean getMoreBlocks = true;

    private volatile boolean isScanning;
    private volatile boolean forceScan = Nxt.getBooleanProperty("nxt.forceScan");
    private volatile boolean validateAtScan = Nxt.getBooleanProperty("nxt.forceValidate");

    private final Runnable getMoreBlocksThread = new Runnable() {

        private final JSONStreamAware getCumulativeDifficultyRequest;

        {
            JSONObject request = new JSONObject();
            request.put("requestType", "getCumulativeDifficulty");
            getCumulativeDifficultyRequest = JSON.prepareRequest(request);
        }

        private boolean peerHasMore;

        @Override
        public void run() {

            try {
                try {
                    if (!getMoreBlocks) {
                        return;
                    }
                    peerHasMore = true;
                    Peer peer = Peers.getAnyPeer(Peer.State.CONNECTED, true);
                    if (peer == null) {
                        return;
                    }
                    JSONObject response = peer.send(getCumulativeDifficultyRequest);
                    if (response == null) {
                        return;
                    }
                    BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
                    String peerCumulativeDifficulty = (String) response.get("cumulativeDifficulty");
                    if (peerCumulativeDifficulty == null) {
                        return;
                    }
                    BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                    if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                        return;
                    }
                    if (response.get("blockchainHeight") != null) {
                        lastBlockchainFeeder = peer;
                        lastBlockchainFeederHeight = ((Long) response.get("blockchainHeight")).intValue();
                    }
                    if (betterCumulativeDifficulty.equals(curCumulativeDifficulty)) {
                        return;
                    }

                    long commonBlockId = Genesis.GENESIS_BLOCK_ID;

                    if (blockchain.getLastBlock().getId() != Genesis.GENESIS_BLOCK_ID) {
                        commonBlockId = getCommonMilestoneBlockId(peer);
                    }
                    if (commonBlockId == 0 || !peerHasMore) {
                        return;
                    }

                    commonBlockId = getCommonBlockId(peer, commonBlockId);
                    if (commonBlockId == 0 || !peerHasMore) {
                        return;
                    }

                    final Block commonBlock = BlockDb.findBlock(commonBlockId);
                    if (commonBlock == null || blockchain.getHeight() - commonBlock.getHeight() >= 720) {
                        return;
                    }

                    long currentBlockId = commonBlockId;
                    List<BlockImpl> forkBlocks = new ArrayList<>();

                    boolean processedAll = true;
                    int requestCount = 0;
                    outer:
                    while (forkBlocks.size() < 1440 && requestCount++ < 10) {
                        JSONArray nextBlocks = getNextBlocks(peer, currentBlockId);
                        if (nextBlocks == null || nextBlocks.size() == 0) {
                            break;
                        }

                        synchronized (blockchain) {

                            for (Object o : nextBlocks) {
                                JSONObject blockData = (JSONObject) o;
                                BlockImpl block;
                                try {
                                    block = BlockImpl.parseBlock(blockData);
                                } catch (NxtException.NotCurrentlyValidException e) {
                                    Logger.logDebugMessage("Cannot validate block: " + e.toString()
                                            + ", will try again later", e);
                                    processedAll = false;
                                    break outer;
                                } catch (RuntimeException | NxtException.ValidationException e) {
                                    Logger.logDebugMessage("Failed to parse block: " + e.toString(), e);
                                    peer.blacklist(e);
                                    return;
                                }
                                currentBlockId = block.getId();

                                if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                                    try {
                                        pushBlock(block);
                                    } catch (BlockNotAcceptedException e) {
                                        peer.blacklist(e);
                                        return;
                                    }
                                } else if (!BlockDb.hasBlock(block.getId())) {
                                    forkBlocks.add(block);
                                }

                            }

                        } //synchronized

                    }

                    if (forkBlocks.size() > 0) {
                        processedAll = false;
                    }

                    if (!processedAll && blockchain.getHeight() - commonBlock.getHeight() < 720) {
                        processFork(peer, forkBlocks, commonBlock);
                    }

                } catch (NxtException.StopException e) {
                    Logger.logMessage("Blockchain download stopped: " + e.getMessage());
                } catch (Exception e) {
                    Logger.logDebugMessage("Error in blockchain download thread", e);
                }
            } catch (Throwable t) {
                Logger.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }

        }

        private long getCommonMilestoneBlockId(Peer peer) {

            String lastMilestoneBlockId = null;

            while (true) {
                JSONObject milestoneBlockIdsRequest = new JSONObject();
                milestoneBlockIdsRequest.put("requestType", "getMilestoneBlockIds");
                if (lastMilestoneBlockId == null) {
                    milestoneBlockIdsRequest.put("lastBlockId", blockchain.getLastBlock().getStringId());
                } else {
                    milestoneBlockIdsRequest.put("lastMilestoneBlockId", lastMilestoneBlockId);
                }

                JSONObject response = peer.send(JSON.prepareRequest(milestoneBlockIdsRequest));
                if (response == null) {
                    return 0;
                }
                JSONArray milestoneBlockIds = (JSONArray) response.get("milestoneBlockIds");
                if (milestoneBlockIds == null) {
                    return 0;
                }
                if (milestoneBlockIds.isEmpty()) {
                    return Genesis.GENESIS_BLOCK_ID;
                }
                // prevent overloading with blockIds
                if (milestoneBlockIds.size() > 20) {
                    Logger.logDebugMessage("Obsolete or rogue peer " + peer.getPeerAddress() + " sends too many milestoneBlockIds, blacklisting");
                    peer.blacklist();
                    return 0;
                }
                if (Boolean.TRUE.equals(response.get("last"))) {
                    peerHasMore = false;
                }
                for (Object milestoneBlockId : milestoneBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) milestoneBlockId);
                    if (BlockDb.hasBlock(blockId)) {
                        if (lastMilestoneBlockId == null && milestoneBlockIds.size() > 1) {
                            peerHasMore = false;
                        }
                        return blockId;
                    }
                    lastMilestoneBlockId = (String) milestoneBlockId;
                }
            }

        }

        private long getCommonBlockId(Peer peer, long commonBlockId) {

            while (true) {
                JSONObject request = new JSONObject();
                request.put("requestType", "getNextBlockIds");
                request.put("blockId", Convert.toUnsignedLong(commonBlockId));
                JSONObject response = peer.send(JSON.prepareRequest(request));
                if (response == null) {
                    return 0;
                }
                JSONArray nextBlockIds = (JSONArray) response.get("nextBlockIds");
                if (nextBlockIds == null || nextBlockIds.size() == 0) {
                    return 0;
                }
                // prevent overloading with blockIds
                if (nextBlockIds.size() > 1440) {
                    Logger.logDebugMessage("Obsolete or rogue peer " + peer.getPeerAddress() + " sends too many nextBlockIds, blacklisting");
                    peer.blacklist();
                    return 0;
                }

                for (Object nextBlockId : nextBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) nextBlockId);
                    if (! BlockDb.hasBlock(blockId)) {
                        return commonBlockId;
                    }
                    commonBlockId = blockId;
                }
            }

        }

        private JSONArray getNextBlocks(Peer peer, long curBlockId) {

            JSONObject request = new JSONObject();
            request.put("requestType", "getNextBlocks");
            request.put("blockId", Convert.toUnsignedLong(curBlockId));
            JSONObject response = peer.send(JSON.prepareRequest(request));
            if (response == null) {
                return null;
            }

            JSONArray nextBlocks = (JSONArray) response.get("nextBlocks");
            if (nextBlocks == null) {
                return null;
            }
            // prevent overloading with blocks
            if (nextBlocks.size() > 1440) {
                Logger.logDebugMessage("Obsolete or rogue peer " + peer.getPeerAddress() + " sends too many nextBlocks, blacklisting");
                peer.blacklist();
                return null;
            }

            return nextBlocks;

        }

        private void processFork(Peer peer, final List<BlockImpl> forkBlocks, final Block commonBlock) {

            synchronized (blockchain) {
                BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();

                List<BlockImpl> myPoppedOffBlocks = popOffTo(commonBlock);

                int pushedForkBlocks = 0;
                if (blockchain.getLastBlock().getId() == commonBlock.getId()) {
                    for (BlockImpl block : forkBlocks) {
                        if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                            try {
                                pushBlock(block);
                                pushedForkBlocks += 1;
                            } catch (BlockNotAcceptedException e) {
                                peer.blacklist(e);
                                break;
                            }
                        }
                    }
                }

                if (pushedForkBlocks > 0 && blockchain.getLastBlock().getCumulativeDifficulty().compareTo(curCumulativeDifficulty) < 0) {
                    Logger.logDebugMessage("Pop off caused by peer " + peer.getPeerAddress() + ", blacklisting");
                    peer.blacklist();
                    List<BlockImpl> peerPoppedOffBlocks = popOffTo(commonBlock);
                    pushedForkBlocks = 0;
                    for (BlockImpl block : peerPoppedOffBlocks) {
                        TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                    }
                }

                if (pushedForkBlocks == 0) {
                    for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                        BlockImpl block = myPoppedOffBlocks.remove(i);
                        try {
                            pushBlock(block);
                        } catch (BlockNotAcceptedException e) {
                            Logger.logErrorMessage("Popped off block no longer acceptable: " + block.getJSONObject().toJSONString(), e);
                            break;
                        }
                    }
                } else {
                    for (BlockImpl block : myPoppedOffBlocks) {
                        TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                    }
                }

            } // synchronized

        }

    };

    private BlockchainProcessorImpl() {

        blockListeners.addListener(new Listener<Block>() {
            @Override
            public void notify(Block block) {
                if (block.getHeight() % 5000 == 0) {
                    Logger.logMessage("processed block " + block.getHeight());
                }
            }
        }, Event.BLOCK_SCANNED);

        blockListeners.addListener(new Listener<Block>() {
            @Override
            public void notify(Block block) {
                if (block.getHeight() % 5000 == 0) {
                    Logger.logMessage("received block " + block.getHeight());
                    Db.db.analyzeTables();
                }
            }
        }, Event.BLOCK_PUSHED);

        if (trimDerivedTables) {
            blockListeners.addListener(new Listener<Block>() {
                @Override
                public void notify(Block block) {
                    if (block.getHeight() % 1440 == 0) {
                        lastTrimHeight = Math.max(block.getHeight() - Constants.MAX_ROLLBACK, 0);
                        if (lastTrimHeight > 0) {
                            for (DerivedDbTable table : derivedTables) {
                                table.trim(lastTrimHeight);
                            }
                        }
                    }
                }
            }, Event.AFTER_BLOCK_APPLY);
        }

        blockListeners.addListener(new Listener<Block>() {
            @Override
            public void notify(Block block) {
                Db.db.analyzeTables();
            }
        }, Event.RESCAN_END);

        ThreadPool.runBeforeStart(new Runnable() {
            @Override
            public void run() {
                addGenesisBlock();
                if (forceScan) {
                    scan(0);
                }
            }
        }, false);

        ThreadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread, 1);

    }

    @Override
    public boolean addListener(Listener<Block> listener, BlockchainProcessor.Event eventType) {
        return blockListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<Block> listener, Event eventType) {
        return blockListeners.removeListener(listener, eventType);
    }

    @Override
    public void registerDerivedTable(DerivedDbTable table) {
        derivedTables.add(table);
    }

    @Override
    public Peer getLastBlockchainFeeder() {
        return lastBlockchainFeeder;
    }

    @Override
    public int getLastBlockchainFeederHeight() {
        return lastBlockchainFeederHeight;
    }

    @Override
    public boolean isScanning() {
        return isScanning;
    }

    @Override
    public int getMinRollbackHeight() {
        return trimDerivedTables ? (lastTrimHeight > 0 ? lastTrimHeight : Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0)) : 0;
    }

    @Override
    public void processPeerBlock(JSONObject request) throws NxtException {
        BlockImpl block = BlockImpl.parseBlock(request);
        pushBlock(block);
    }

    @Override
    public List<BlockImpl> popOffTo(int height) {
        return popOffTo(blockchain.getBlockAtHeight(height));
    }

    @Override
    public void fullReset() {
        synchronized (blockchain) {
            //BlockDb.deleteBlock(Genesis.GENESIS_BLOCK_ID); // fails with stack overflow in H2
            BlockDb.deleteAll();
            addGenesisBlock();
            try {
                setGetMoreBlocks(false);
                scan(0);
            } finally {
                setGetMoreBlocks(true);
            }
        }
    }

    @Override
    public void forceScanAtStart() {
        forceScan = true;
    }

    @Override
    public void validateAtNextScan() {
        validateAtScan = true;
    }

    @Override
    public void setGetMoreBlocks(boolean getMoreBlocks) {
        this.getMoreBlocks = getMoreBlocks;
    }

    private void addBlock(BlockImpl block) {
        try (Connection con = Db.db.getConnection()) {
            BlockDb.saveBlock(con, block);
            blockchain.setLastBlock(block);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void addGenesisBlock() {
        if (BlockDb.hasBlock(Genesis.GENESIS_BLOCK_ID)) {
            Logger.logMessage("Genesis block already in database");
            BlockImpl lastBlock = BlockDb.findLastBlock();
            blockchain.setLastBlock(lastBlock);
            Logger.logMessage("Last block height: " + lastBlock.getHeight());
            return;
        }
        Logger.logMessage("Genesis block not in database, starting from scratch");
        try {
            List<TransactionImpl> transactions = new ArrayList<>();
            for (int i = 0; i < Genesis.GENESIS_RECIPIENTS.length; i++) {
                TransactionImpl transaction = new TransactionImpl.BuilderImpl((byte) 0, Genesis.CREATOR_PUBLIC_KEY,
                        Genesis.GENESIS_AMOUNTS[i] * Constants.ONE_NXT, 0, 0, (short) 0,
                        Attachment.ORDINARY_PAYMENT)
                        .recipientId(Genesis.GENESIS_RECIPIENTS[i])
                        .signature(Genesis.GENESIS_SIGNATURES[i])
                        .height(Integer.MAX_VALUE) //FSM Genesis needs Integer.MAX_VALUE
                        .build();  
                Logger.logMessage("id"+ transaction.getId());                
                transactions.add(transaction);
            }
            Collections.sort(transactions);
            MessageDigest digest = Crypto.sha256();
            for (Transaction transaction : transactions) {
                digest.update(transaction.getBytes());
            }
            BlockImpl genesisBlock = new BlockImpl(-1, 0, 0, Constants.MAX_BALANCE_NQT, 0, transactions.size() * 160, digest.digest(),
                    Genesis.CREATOR_PUBLIC_KEY, new byte[64], Genesis.GENESIS_BLOCK_SIGNATURE, null, transactions);
            genesisBlock.setPrevious(null);
            addBlock(genesisBlock);
        } catch (NxtException.ValidationException e) {
            Logger.logMessage(e.getMessage());
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void pushBlock(final BlockImpl block) throws BlockNotAcceptedException {

        int curTime = Nxt.getEpochTime();

        synchronized (blockchain) {
            TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
            BlockImpl previousLastBlock = null;
            try {
                Db.db.beginTransaction();
                previousLastBlock = blockchain.getLastBlock();

                if (previousLastBlock.getId() != block.getPreviousBlockId()) {
                    throw new BlockOutOfOrderException("Previous block id doesn't match");
                }

                if (block.getVersion() != getBlockVersion(previousLastBlock.getHeight())) {
                    throw new BlockNotAcceptedException("Invalid version " + block.getVersion());
                }

                for (int i = 0; i < heigthsToCheck.length; i++) {
                	if (heigthsToCheck[i]==previousLastBlock.getHeight()){
                		if (! ((new BigInteger(previousBlockId[i])).longValue() == previousLastBlock.getId())) {
                			throw new BlockNotAcceptedException("BlockId check failed at block height "+heigthsToCheck[i]+". Expected "+ previousBlockId[i] +" but found "+Convert.toUnsignedLong(previousLastBlock.getId()));
                		} else {
                			Logger.logInfoMessage("BlockId check passed at block height "+heigthsToCheck[i]);
                		}
                		break;
                	}
				}

                if (block.getVersion() != 1 && !Arrays.equals(Crypto.sha256().digest(previousLastBlock.getBytes()), block.getPreviousBlockHash())) {
                    throw new BlockNotAcceptedException("Previous block hash doesn't match");
                }
                if (block.getTimestamp() > curTime + 15 || block.getTimestamp() <= previousLastBlock.getTimestamp()) {
                    throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
                            + " current time is " + curTime + ", previous block timestamp is " + previousLastBlock.getTimestamp());
                }
                if (block.getId() == 0L || BlockDb.hasBlock(block.getId())) {
                    throw new BlockNotAcceptedException("Duplicate block or invalid id");
                }
                if (!block.verifyGenerationSignature() && !Generator.allowsFakeForging(block.getGeneratorPublicKey())) {
                	throw new BlockNotAcceptedException("Generation signature verification failed");
                }
                if (!block.verifyBlockSignature()) {
                    throw new BlockNotAcceptedException("Block signature verification failed");
                }

                Map<TransactionType, Set<String>> duplicates = new HashMap<>();
                long calculatedTotalAmount = 0;
                long calculatedTotalFee = 0;
                MessageDigest digest = Crypto.sha256();

                for (TransactionImpl transaction : block.getTransactions()) {

                    if (transaction.getTimestamp() > curTime + 15) {
                        throw new BlockOutOfOrderException("Invalid transaction timestamp: " + transaction.getTimestamp()
                                + ", current time is " + curTime);
                    }
                    if (transaction.getTimestamp() > block.getTimestamp() + 15
                            || (transaction.getExpiration() < block.getTimestamp())) {
                        throw new TransactionNotAcceptedException("Invalid transaction timestamp " + transaction.getTimestamp()
                                + " for transaction " + transaction.getStringId() + ", current time is " + curTime
                                + ", block timestamp is " + block.getTimestamp(), transaction);
                    }
                    if (TransactionDb.hasTransaction(transaction.getId())) {
                        throw new TransactionNotAcceptedException("Transaction " + transaction.getStringId()
                                + " is already in the blockchain", transaction);
                    }
                    if (transaction.getReferencedTransactionFullHash() != null) {
                        if ((previousLastBlock.getHeight() < Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                                && !TransactionDb.hasTransaction(Convert.fullHashToId(transaction.getReferencedTransactionFullHash())))
                                || (previousLastBlock.getHeight() >= Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                                && !hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0))) {
                            throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
                                    + transaction.getReferencedTransactionFullHash()
                                    + " for transaction " + transaction.getStringId(), transaction);
                        }
                    }
                    if (transaction.getVersion() != transactionProcessor.getTransactionVersion(previousLastBlock.getHeight())) {
                        throw new TransactionNotAcceptedException("Invalid transaction version " + transaction.getVersion()
                                + " at height " + previousLastBlock.getHeight(), transaction);
                    }
                    if (!transaction.verifySignature()) {
                        throw new TransactionNotAcceptedException("Signature verification failed for transaction "
                                + transaction.getStringId() + " at height " + previousLastBlock.getHeight(), transaction);
                    }
                    /*
                    if (!EconomicClustering.verifyFork(transaction)) {
                        Logger.logDebugMessage("Block " + block.getStringId() + " height " + (previousLastBlock.getHeight() + 1)
                                + " contains transaction that was generated on a fork: "
                                + transaction.getStringId() + " ecBlockHeight " + transaction.getECBlockHeight() + " ecBlockId "
                                + Convert.toUnsignedLong(transaction.getECBlockId()));
                        //throw new TransactionNotAcceptedException("Transaction belongs to a different fork", transaction);
                    }
                    */
                    //validate FSM transaction
                    if (transaction.getType() == TransactionType.findTransactionType((byte) 5, (byte) 1)) {
                        if (!AT_Controller.validateATs(transaction, previousLastBlock.getHeight())) {
                            throw new TransactionNotAcceptedException("Transaction of AT is invalid: "
                                    + transaction.getStringId(), transaction);                        	
                        }
                    }
                    if (transaction.isDuplicate(duplicates)) {
                        throw new TransactionNotAcceptedException("Transaction is a duplicate: "
                                + transaction.getStringId(), transaction);
                    }
                    try {
                        transaction.validate();
                    } catch (NxtException.ValidationException e) {
                        throw new TransactionNotAcceptedException(e.getMessage(), transaction);
                    }

                    calculatedTotalAmount += transaction.getAmountNQT();

                    calculatedTotalFee += transaction.getFeeNQT();

                    digest.update(transaction.getBytes());

                }

                if (calculatedTotalAmount != block.getTotalAmountNQT() || calculatedTotalFee != block.getTotalFeeNQT()) {
                    throw new BlockNotAcceptedException("Total amount or fee don't match transaction totals");
                }
                if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
                    throw new BlockNotAcceptedException("Payload hash doesn't match");
                }

                block.setPrevious(previousLastBlock);
                blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);
                transactionProcessor.requeueAllUnconfirmedTransactions();
                addBlock(block);
                accept(block);

                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                blockchain.setLastBlock(previousLastBlock);
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } // synchronized

        blockListeners.notify(block, Event.BLOCK_PUSHED);

        if (block.getTimestamp() >= Nxt.getEpochTime() - 15) {
            Peers.sendToSomePeers(block);
        }

    }

    private void accept(BlockImpl block) throws TransactionNotAcceptedException {
        TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
        for (TransactionImpl transaction : block.getTransactions()) {
            if (! transaction.applyUnconfirmed()) {
                throw new TransactionNotAcceptedException("Double spending transaction: " + transaction.getStringId(), transaction);
            }
        }
        blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);
        block.apply();
        blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
        if (block.getTransactions().size() > 0) {
            transactionProcessor.notifyListeners(block.getTransactions(), TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
        }
    }

    private List<BlockImpl> popOffTo(Block commonBlock) {
        synchronized (blockchain) {
            if (commonBlock.getHeight() < getMinRollbackHeight()) {
                throw new IllegalArgumentException("Rollback to height " + commonBlock.getHeight() + " not suppported, "
                        + "current height " + Nxt.getBlockchain().getHeight());
            }
            if (! blockchain.hasBlock(commonBlock.getId())) {
                Logger.logDebugMessage("Block " + commonBlock.getStringId() + " not found in blockchain, nothing to pop off");
                return Collections.emptyList();
            }
            List<BlockImpl> poppedOffBlocks = new ArrayList<>();
            try {
                Db.db.beginTransaction();
                BlockImpl block = blockchain.getLastBlock();
                Logger.logDebugMessage("Rollback from " + block.getHeight() + " to " + commonBlock.getHeight());
                while (block.getId() != commonBlock.getId() && block.getId() != Genesis.GENESIS_BLOCK_ID) {
                    poppedOffBlocks.add(block);
                    block = popLastBlock();
                }
                for (DerivedDbTable table : derivedTables) {
                    table.rollback(commonBlock.getHeight());
                }
                Db.db.commitTransaction();
            } catch (RuntimeException e) {
                Db.db.rollbackTransaction();
                Logger.logDebugMessage("Error popping off to " + commonBlock.getHeight(), e);
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            return poppedOffBlocks;
        } // synchronized
    }
    private BlockImpl popLastBlock() {
        BlockImpl block = blockchain.getLastBlock();
        if (block.getId() == Genesis.GENESIS_BLOCK_ID) {
            throw new RuntimeException("Cannot pop off genesis block");
        }
        BlockImpl previousBlock = blockchain.getBlock(block.getPreviousBlockId());
        blockchain.setLastBlock(block, previousBlock);
        for (TransactionImpl transaction : block.getTransactions()) {
            transaction.unsetBlock();
        }
        BlockDb.deleteBlocksFrom(block.getId());
        blockListeners.notify(block, Event.BLOCK_POPPED);
        return previousBlock;
    }

    int getBlockVersion(int previousBlockHeight) {
        return previousBlockHeight < Constants.TRANSPARENT_FORGING_BLOCK ? 1
                : previousBlockHeight < Constants.NQT_BLOCK ? 2
                : 3;
    }

    void generateBlock(String secretPhrase, int blockTimestamp) throws BlockNotAcceptedException {

        TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
        List<TransactionImpl> orderedUnconfirmedTransactions = new ArrayList<>();
        try (FilteringIterator<TransactionImpl> transactions = new FilteringIterator<>(transactionProcessor.getAllUnconfirmedTransactions(),
                new FilteringIterator.Filter<TransactionImpl>() {
                    @Override
                    public boolean ok(TransactionImpl transaction) {
                        return hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0);
                    }
                })) {
            for (TransactionImpl transaction : transactions) {
                orderedUnconfirmedTransactions.add(transaction);
            }
        }

        BlockImpl previousBlock = blockchain.getLastBlock();

        SortedSet<TransactionImpl> blockTransactions = new TreeSet<>();

        Map<TransactionType, Set<String>> duplicates = new HashMap<>();

        long totalAmountNQT = 0;
        long totalFeeNQT = 0;
        int payloadLength = 0;

        while (payloadLength <= Constants.MAX_PAYLOAD_LENGTH && blockTransactions.size() <= Constants.MAX_NUMBER_OF_TRANSACTIONS) {

            int prevNumberOfNewTransactions = blockTransactions.size();

            for (TransactionImpl transaction : orderedUnconfirmedTransactions) {

                int transactionLength = transaction.getSize();
                if (blockTransactions.contains(transaction) || payloadLength + transactionLength > Constants.MAX_PAYLOAD_LENGTH) {
                    continue;
                }

                if (transaction.getVersion() != transactionProcessor.getTransactionVersion(previousBlock.getHeight())) {
                    continue;
                }

                if (transaction.getTimestamp() > blockTimestamp + 15 || transaction.getExpiration() < blockTimestamp) {
                    continue;
                }

                if (transaction.isDuplicate(duplicates)) {
                    continue;
                }

                try {
                    transaction.validate();
                } catch (NxtException.NotCurrentlyValidException e) {
                    continue;
                } catch (NxtException.ValidationException e) {
                    transactionProcessor.removeUnconfirmedTransaction(transaction);
                    continue;
                }

                /*
                if (!EconomicClustering.verifyFork(transaction)) {
                    Logger.logDebugMessage("Including transaction that was generated on a fork: " + transaction.getStringId()
                            + " ecBlockHeight " + transaction.getECBlockHeight() + " ecBlockId " + Convert.toUnsignedLong(transaction.getECBlockId()));
                    //continue;
                }
                */

                blockTransactions.add(transaction);
                payloadLength += transactionLength;
                totalAmountNQT += transaction.getAmountNQT();
                totalFeeNQT += transaction.getFeeNQT();

            }

            if (blockTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }

        final byte[] publicKey = Crypto.getPublicKey(secretPhrase);

        MessageDigest digest = Crypto.sha256();
        for (Transaction transaction : blockTransactions) {
            digest.update(transaction.getBytes());
        }

        byte[] payloadHash = digest.digest();

        digest.update(previousBlock.getGenerationSignature());
        byte[] generationSignature = digest.digest(publicKey);

        BlockImpl block;
        byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.getBytes());

        try {

            block = new BlockImpl(getBlockVersion(previousBlock.getHeight()), blockTimestamp, previousBlock.getId(), totalAmountNQT, totalFeeNQT, payloadLength,
                    payloadHash, publicKey, generationSignature, null, previousBlockHash, new ArrayList<>(blockTransactions));

        } catch (NxtException.ValidationException e) {
            // shouldn't happen because all transactions are already validated
            Logger.logMessage("Error generating block", e);
            return;
        }

        block.sign(secretPhrase);

        try {
            pushBlock(block);
            blockListeners.notify(block, Event.BLOCK_GENERATED);
            Logger.logDebugMessage("Account " + Convert.toUnsignedLong(block.getGeneratorId()) + " generated block " + block.getStringId()
                    + " at height " + block.getHeight());
        } catch (TransactionNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            Transaction transaction = e.getTransaction();
            Logger.logDebugMessage("Removing invalid transaction: " + transaction.getStringId());
            transactionProcessor.removeUnconfirmedTransaction((TransactionImpl) transaction);
            throw e;
        } catch (BlockNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            throw e;
        }
    }

    private boolean hasAllReferencedTransactions(Transaction transaction, int timestamp, int count) {
        if (transaction.getReferencedTransactionFullHash() == null) {
            return timestamp - transaction.getTimestamp() < 60 * 1440 * 60 && count < 10;
        }
        transaction = TransactionDb.findTransactionByFullHash(transaction.getReferencedTransactionFullHash());
        return transaction != null && hasAllReferencedTransactions(transaction, timestamp, count + 1);
    }

    @Override
    public void scan(int height) {
        synchronized (blockchain) {
            TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
            int blockchainHeight = Nxt.getBlockchain().getHeight();
            if (height > blockchainHeight + 1) {
                throw new IllegalArgumentException("Rollback height " + (height - 1) + " exceeds current blockchain height of " + blockchainHeight);
            }
            if (height > 0 && height < getMinRollbackHeight()) {
                Logger.logMessage("Rollback of more than " + Constants.MAX_ROLLBACK + " blocks not supported, will do a full scan");
                height = 0;
            }
            if (height < 0) {
                height = 0;
            }
            Logger.logMessage("Scanning blockchain starting from height " + height + "...");
            if (validateAtScan) {
                Logger.logDebugMessage("Also verifying signatures and validating transactions...");
            }
            Integer popOffHeight=null;
            try (Connection con = Db.db.beginTransaction();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height >= ? ORDER BY db_id ASC")) {
                isScanning = true;
                transactionProcessor.requeueAllUnconfirmedTransactions();
                for (DerivedDbTable table : derivedTables) {
                    if (height == 0) {
                        table.truncate();
                    } else {
                        table.rollback(height - 1);
                    }
                }
                pstmt.setInt(1, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    BlockImpl currentBlock = BlockDb.findBlockAtHeight(height);
                    blockListeners.notify(currentBlock, Event.RESCAN_BEGIN);
                    long currentBlockId = currentBlock.getId();
                    if (height == 0) {
                        blockchain.setLastBlock(currentBlock); // special case to avoid no last block
                        Account.addOrGetAccount(Genesis.CREATOR_ID).apply(Genesis.CREATOR_PUBLIC_KEY, 0);
                    } else {
                        blockchain.setLastBlock(BlockDb.findBlockAtHeight(height - 1));
                    }
                    while (rs.next()) {
                        try {
                            currentBlock = BlockDb.loadBlock(con, rs);
                            if (currentBlock.getId() != currentBlockId) {
                            	throw new NxtException.NotValidException("Database blocks in the wrong order! Is: " + currentBlockId + " expect " + currentBlock.getId());
                            }
                            if (validateAtScan && currentBlockId != Genesis.GENESIS_BLOCK_ID) {
                                if (!currentBlock.verifyBlockSignature()) {
                                    throw new NxtException.NotValidException("Invalid block signature");
                                }
                                if (!currentBlock.verifyGenerationSignature() && !Generator.allowsFakeForging(currentBlock.getGeneratorPublicKey())) {
                                    throw new NxtException.NotValidException("Invalid block generation signature");
                                }
                                if (currentBlock.getVersion() != getBlockVersion(blockchain.getHeight())) {
                                    throw new NxtException.NotValidException("Invalid block version");
                                }
                                byte[] blockBytes = currentBlock.getBytes();
                                JSONObject blockJSON = (JSONObject) JSONValue.parse(currentBlock.getJSONObject().toJSONString());
                                if (!Arrays.equals(blockBytes, BlockImpl.parseBlock(blockJSON).getBytes())) {
                                    throw new NxtException.NotValidException("Block JSON cannot be parsed back to the same block");
                                }
                                for (int i = 0; i < heigthsToCheck.length; i++) {
                                	if (heigthsToCheck[i]==currentBlock.getHeight()){
                                		if (! ((new BigInteger(previousBlockId[i])).longValue() == currentBlock.getId())) {
                                			if (i>0) {
                                				// pop of until last known good 
                                				popOffHeight=heigthsToCheck[i-1];
                                			}
                                			throw new NxtException.NotValidException("BlockId check failed at block height "+heigthsToCheck[i]+". Expected "+ previousBlockId[i] +" but found "+Convert.toUnsignedLong(currentBlock.getId()));                			
                                		} else {
                                			Logger.logInfoMessage("BlockId check passed at block height "+heigthsToCheck[i]);
                                		}
                                		break;
                                	}
                				}
                                for (TransactionImpl transaction : currentBlock.getTransactions()) {
                                    if (!transaction.verifySignature()) {
                                        throw new NxtException.NotValidException("Invalid transaction signature");
                                    }
                                    if (transaction.getVersion() != transactionProcessor.getTransactionVersion(blockchain.getHeight())) {
                                        throw new NxtException.NotValidException("Invalid transaction version");
                                    }
                                    /*
                                    if (!EconomicClustering.verifyFork(transaction)) {
                                        Logger.logDebugMessage("Found transaction that was generated on a fork: " + transaction.getStringId()
                                                + " in block " + currentBlock.getStringId() + " at height " + currentBlock.getHeight()
                                                + " ecBlockHeight " + transaction.getECBlockHeight() + " ecBlockId " + Convert.toUnsignedLong(transaction.getECBlockId()));
                                        //throw new NxtException.NotValidException("Invalid transaction fork");
                                    }
                                    */
                                    transaction.validate();
                                    byte[] transactionBytes = transaction.getBytes();
                                    if (currentBlock.getHeight() > Constants.NQT_BLOCK
                                            && !Arrays.equals(transactionBytes, transactionProcessor.parseTransaction(transactionBytes).getBytes())) {
                                        throw new NxtException.NotValidException("Transaction bytes cannot be parsed back to the same transaction");
                                    }
                                    JSONObject transactionJSON = (JSONObject) JSONValue.parse(transaction.getJSONObject().toJSONString());
                                    if (!Arrays.equals(transactionBytes, transactionProcessor.parseTransaction(transactionJSON).getBytes())) {
                                        throw new NxtException.NotValidException("Transaction JSON cannot be parsed back to the same transaction");
                                    }
                                }                                
                            }
                            blockListeners.notify(currentBlock, Event.BEFORE_BLOCK_ACCEPT);
                            blockchain.setLastBlock(currentBlock);
                            accept(currentBlock);
                            currentBlockId = currentBlock.getNextBlockId();
                            Db.db.commitTransaction();
                        } catch (NxtException | RuntimeException e) {
                            Db.db.rollbackTransaction();
                            Logger.logDebugMessage(e.toString(), e);
                            Logger.logDebugMessage("Applying block " + Convert.toUnsignedLong(currentBlockId) + " at height "
                                    + (currentBlock == null ? 0 : currentBlock.getHeight()) + " failed, deleting from database");
                            if (currentBlock != null) {
                                transactionProcessor.processLater(currentBlock.getTransactions());
                            }
                            while (rs.next()) {
                                try {
                                    currentBlock = BlockDb.loadBlock(con, rs);
                                    transactionProcessor.processLater(currentBlock.getTransactions());
                                } catch (NxtException.ValidationException ignore) {
                                }
                            }
                            BlockDb.deleteBlocksFrom(currentBlockId);
                            blockchain.setLastBlock(BlockDb.findLastBlock());
                        }
                        blockListeners.notify(currentBlock, Event.BLOCK_SCANNED);
                    }
                    Db.db.endTransaction();
                    blockListeners.notify(currentBlock, Event.RESCAN_END);
                }
                validateAtScan = false;
                Logger.logMessage("...done at height " + Nxt.getBlockchain().getHeight());
                if (popOffHeight!=null) {
                	Logger.logMessage("Fork isssue detected, resolving.");
                	popOffTo(popOffHeight);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } finally {
                isScanning = false;
            }
        } // synchronized
    }

}
