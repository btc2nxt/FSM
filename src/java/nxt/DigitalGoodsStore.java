package nxt;

import nxt.crypto.EncryptedData;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;
import nxt.db.VersionedValuesDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DigitalGoodsStore {

    public static enum Event {
        GOODS_LISTED, GOODS_DELISTED, GOODS_PRICE_CHANGE, GOODS_QUANTITY_CHANGE,
        PURCHASE, DELIVERY, REFUND, FEEDBACK
    }

    static {
        Nxt.getBlockchainProcessor().addListener(new Listener<Block>() {
            @Override
            public void notify(Block block) {
                if (block.getHeight() < Constants.DIGITAL_GOODS_STORE_BLOCK) {
                    return;
                }
                List<Purchase> expiredPurchases = new ArrayList<>();
                try (DbIterator<Purchase> iterator = Purchase.getExpiredPendingPurchases(block)) {
                    while (iterator.hasNext()) {
                        expiredPurchases.add(iterator.next());
                    }
                }
                for (Purchase purchase : expiredPurchases) {
                    Account buyer = Account.getAccount(purchase.getBuyerId());
                    buyer.addToUnconfirmedBalanceNQT(Convert.safeMultiply(purchase.getQuantity(), purchase.getPriceNQT()));
                    Goods.getGoods(purchase.getGoodsId()).changeQuantity(purchase.getQuantity());
                    purchase.setPending(false);
                }
            }
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    private static final Listeners<Goods,Event> goodsListeners = new Listeners<>();

    private static final Listeners<Purchase,Event> purchaseListeners = new Listeners<>();

    public static boolean addGoodsListener(Listener<Goods> listener, Event eventType) {
        return goodsListeners.addListener(listener, eventType);
    }

    public static boolean removeGoodsListener(Listener<Goods> listener, Event eventType) {
        return goodsListeners.removeListener(listener, eventType);
    }

    public static boolean addPurchaseListener(Listener<Purchase> listener, Event eventType) {
        return purchaseListeners.addListener(listener, eventType);
    }

    public static boolean removePurchaseListener(Listener<Purchase> listener, Event eventType) {
        return purchaseListeners.removeListener(listener, eventType);
    }

    static void init() {
        Tag.init();
        Goods.init();
        Purchase.init();
    }

    public static final class Tag {

        private static final DbKey.StringKeyFactory<Tag> tagDbKeyFactory = new DbKey.StringKeyFactory<Tag>("tag") {
            @Override
            public DbKey newKey(Tag tag) {
                return tag.dbKey;
            }
        };

        private static final VersionedEntityDbTable<Tag> tagTable = new VersionedEntityDbTable<Tag>("tag", tagDbKeyFactory) {

            @Override
            protected Tag load(Connection con, ResultSet rs) throws SQLException {
                return new Tag(rs);
            }

            @Override
            protected void save(Connection con, Tag tag) throws SQLException {
                tag.save(con);
            }

            @Override
            public String defaultSort() {
                return " ORDER BY in_stock_count DESC, total_count DESC, tag ASC ";
            }

        };

        public static int getCount() {
            return tagTable.getCount();
        }
        public static int getCountInStock() {
            return tagTable.getCount(new DbClause.FixedClause(" in_stock_count > 0 "));
        }

        public static DbIterator<Tag> getAllTags(int from, int to) {
            return tagTable.getAll(from, to);
        }

        public static DbIterator<Tag> getInStockTags(int from, int to) {
            return tagTable.getManyBy(new DbClause.FixedClause(" in_stock_count > 0 "), from, to);
        }

        private static void init() {}

        private static void add(Goods goods) {
            for (String tagValue : goods.getParsedTags()) {
                Tag tag = tagTable.get(tagDbKeyFactory.newKey(tagValue));
                if (tag == null) {
                    tag = new Tag(tagValue);
                }
                tag.inStockCount += 1;
                tag.totalCount += 1;
                tagTable.insert(tag);
            }
        }

        private static void delist(Goods goods) {
            for (String tagValue : goods.getParsedTags()) {
                Tag tag = tagTable.get(tagDbKeyFactory.newKey(tagValue));
                if (tag == null) {
                    throw new IllegalStateException("Unknown tag " + tagValue);
                }
                tag.inStockCount -= 1;
                tagTable.insert(tag);
            }
        }

        private final String tag;
        private final DbKey dbKey;
        private int inStockCount;
        private int totalCount;

        private Tag(String tag) {
            this.tag = tag;
            this.dbKey = tagDbKeyFactory.newKey(this.tag);
        }

        private Tag(ResultSet rs) throws SQLException {
            this.tag = rs.getString("tag");
            this.dbKey = tagDbKeyFactory.newKey(this.tag);
            this.inStockCount = rs.getInt("in_stock_count");
            this.totalCount = rs.getInt("total_count");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO tag (tag, in_stock_count, total_count, height, latest) "
                    + "KEY (tag, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setString(++i, this.tag);
                pstmt.setInt(++i, this.inStockCount);
                pstmt.setInt(++i, this.totalCount);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public String getTag() {
            return tag;
        }

        public int getInStockCount() {
            return inStockCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

    }

    public static final class Goods {

        private static final DbKey.LongKeyFactory<Goods> goodsDbKeyFactory = new DbKey.LongKeyFactory<Goods>("id") {

            @Override
            public DbKey newKey(Goods goods) {
                return goods.dbKey;
            }

        };

        private static final VersionedEntityDbTable<Goods> goodsTable = new VersionedEntityDbTable<Goods>("goods", goodsDbKeyFactory) {

            @Override
            protected Goods load(Connection con, ResultSet rs) throws SQLException {
                return new Goods(rs);
            }

            @Override
            protected void save(Connection con, Goods goods) throws SQLException {
                goods.save(con);
            }

            @Override
            protected String defaultSort() {
                return " ORDER BY timestamp DESC, id ASC ";
            }

        };

        private static final DbClause inStockClause = new DbClause.FixedClause(" goods.delisted = FALSE AND goods.quantity > 0 ");

        public static int getCount() {
            return goodsTable.getCount();
        }

        public static int getCountInStock() {
            return goodsTable.getCount(inStockClause);
        }

        public static Goods getGoods(long goodsId) {
            return goodsTable.get(goodsDbKeyFactory.newKey(goodsId));
        }

        public static DbIterator<Goods> getAllGoods(int from, int to) {
            return goodsTable.getAll(from, to);
        }

        public static DbIterator<Goods> getGoodsInStock(int from, int to) {
            return goodsTable.getManyBy(inStockClause, from, to);
        }

        public static DbIterator<Goods> getSellerGoods(final long sellerId, final boolean inStockOnly, int from, int to) {
            return goodsTable.getManyBy(new SellerDbClause(sellerId, inStockOnly), from, to, " ORDER BY name ASC, timestamp DESC, id ASC ");
        }

        public static int getSellerGoodsCount(long sellerId, boolean inStockOnly) {
            return goodsTable.getCount(new SellerDbClause(sellerId, inStockOnly));
        }

        public static DbIterator<Goods> searchGoods(String query, boolean inStockOnly, int from, int to) {
            return goodsTable.search(query, inStockOnly ? inStockClause : DbClause.EMPTY_CLAUSE, from, to,
                    " ORDER BY ft.score DESC, goods.timestamp DESC ");
        }

        public static DbIterator<Goods> searchSellerGoods(String query, long sellerId, boolean inStockOnly, int from, int to) {
            return goodsTable.search(query, new SellerDbClause(sellerId, inStockOnly), from, to,
                    " ORDER BY ft.score DESC, goods.name ASC, goods.timestamp DESC ");
        }

        private static void init() {}


        private final long id;
        private final DbKey dbKey;
        private final long sellerId;
        private final String name;
        private final String description;
        private final String tags;
        private final String[] parsedTags;
        private final int timestamp;
        private int quantity;
        private long priceNQT;
        private boolean delisted;

        private Goods(Transaction transaction, Attachment.DigitalGoodsListing attachment) {
            this.id = transaction.getId();
            this.dbKey = goodsDbKeyFactory.newKey(this.id);
            this.sellerId = transaction.getSenderId();
            this.name = attachment.getName();
            this.description = attachment.getDescription();
            this.tags = attachment.getTags();
            this.parsedTags = parseTags(this.tags);
            this.quantity = attachment.getQuantity();
            this.priceNQT = attachment.getPriceNQT();
            this.delisted = false;
            this.timestamp = transaction.getTimestamp();
        }

        private Goods(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.dbKey = goodsDbKeyFactory.newKey(this.id);
            this.sellerId = rs.getLong("seller_id");
            this.name = rs.getString("name");
            this.description = rs.getString("description");
            this.tags = rs.getString("tags");
            Object[] array = (Object[])rs.getArray("parsed_tags").getArray();
            this.parsedTags = Arrays.copyOf(array, array.length, String[].class);
            this.quantity = rs.getInt("quantity");
            this.priceNQT = rs.getLong("price");
            this.delisted = rs.getBoolean("delisted");
            this.timestamp = rs.getInt("timestamp");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO goods (id, seller_id, name, "
                    + "description, tags, parsed_tags, timestamp, quantity, price, delisted, height, latest) KEY (id, height) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.getId());
                pstmt.setLong(++i, this.getSellerId());
                pstmt.setString(++i, this.getName());
                pstmt.setString(++i, this.getDescription());
                pstmt.setString(++i, this.getTags());
                pstmt.setObject(++i, this.getParsedTags());
                pstmt.setInt(++i, this.getTimestamp());
                pstmt.setInt(++i, this.getQuantity());
                pstmt.setLong(++i, this.getPriceNQT());
                pstmt.setBoolean(++i, this.isDelisted());
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getSellerId() {
            return sellerId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getTags() {
            return tags;
        }

        public int getTimestamp() {
            return timestamp;
        }

        public int getQuantity() {
            return quantity;
        }

        private void changeQuantity(int deltaQuantity) {
            if (quantity == 0 && deltaQuantity > 0) {
                Tag.add(this);
            }
            quantity += deltaQuantity;
            if (quantity < 0) {
                quantity = 0;
            } else if (quantity > Constants.MAX_DGS_LISTING_QUANTITY) {
                quantity = Constants.MAX_DGS_LISTING_QUANTITY;
            }
            if (quantity == 0) {
                Tag.delist(this);
            }
            goodsTable.insert(this);
        }

        public long getPriceNQT() {
            return priceNQT;
        }

        private void changePrice(long priceNQT) {
            this.priceNQT = priceNQT;
            goodsTable.insert(this);
        }

        public boolean isDelisted() {
            return delisted;
        }

        private void setDelisted(boolean delisted) {
            this.delisted = delisted;
            if (this.quantity > 0) {
                Tag.delist(this);
            }
            goodsTable.insert(this);
        }

        public String[] getParsedTags() {
            return parsedTags;
        }

        private static final Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_30);
        private static final String[] emptyTags = new String[0];

        private static String[] parseTags(String tags) {
            if (tags.trim().length() == 0) {
                return emptyTags;
            }
            List<String> list = new ArrayList<>();
            try (TokenStream stream = analyzer.tokenStream(null, new StringReader(tags))) {
                CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
                String tag;
                while (stream.incrementToken() && list.size() < 3 && (tag = attribute.toString()).length() <= 20 && tag.length() >= 3) {
                    if (!list.contains(tag)) {
                        list.add(tag);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e.toString(), e);
            }
            return list.toArray(new String[list.size()]);
        }

    }

    public static final class Purchase {

        private static final DbKey.LongKeyFactory<Purchase> purchaseDbKeyFactory = new DbKey.LongKeyFactory<Purchase>("id") {

            @Override
            public DbKey newKey(Purchase purchase) {
                return purchase.dbKey;
            }

        };

        private static final VersionedEntityDbTable<Purchase> purchaseTable = new VersionedEntityDbTable<Purchase>("purchase", purchaseDbKeyFactory) {

            @Override
            protected Purchase load(Connection con, ResultSet rs) throws SQLException {
                return new Purchase(rs);
            }

            @Override
            protected void save(Connection con, Purchase purchase) throws SQLException {
                purchase.save(con);
            }

            @Override
            protected String defaultSort() {
                return " ORDER BY timestamp DESC, id ASC ";
            }

        };

        private static final DbKey.LongKeyFactory<Purchase> feedbackDbKeyFactory = new DbKey.LongKeyFactory<Purchase>("id") {

            @Override
            public DbKey newKey(Purchase purchase) {
                return purchase.dbKey;
            }

        };

        private static final VersionedValuesDbTable<Purchase, EncryptedData> feedbackTable = new VersionedValuesDbTable<Purchase, EncryptedData>("purchase_feedback", feedbackDbKeyFactory) {

            @Override
            protected EncryptedData load(Connection con, ResultSet rs) throws SQLException {
                byte[] data = rs.getBytes("feedback_data");
                byte[] nonce = rs.getBytes("feedback_nonce");
                return new EncryptedData(data, nonce);
            }

            @Override
            protected void save(Connection con, Purchase purchase, EncryptedData encryptedData) throws SQLException {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO purchase_feedback (id, feedback_data, feedback_nonce, "
                        + "height, latest) VALUES (?, ?, ?, ?, TRUE)")) {
                    int i = 0;
                    pstmt.setLong(++i, purchase.getId());
                    setEncryptedData(pstmt, encryptedData, ++i);
                    ++i;
                    pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                    pstmt.executeUpdate();
                }
            }

        };

        private static final DbKey.LongKeyFactory<Purchase> publicFeedbackDbKeyFactory = new DbKey.LongKeyFactory<Purchase>("id") {

            @Override
            public DbKey newKey(Purchase purchase) {
                return purchase.dbKey;
            }

        };

        private static final VersionedValuesDbTable<Purchase, String> publicFeedbackTable = new VersionedValuesDbTable<Purchase, String>("purchase_public_feedback", publicFeedbackDbKeyFactory) {

            @Override
            protected String load(Connection con, ResultSet rs) throws SQLException {
                return rs.getString("public_feedback");
            }

            @Override
            protected void save(Connection con, Purchase purchase, String publicFeedback) throws SQLException {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO purchase_public_feedback (id, public_feedback, "
                        + "height, latest) VALUES (?, ?, ?, TRUE)")) {
                    int i = 0;
                    pstmt.setLong(++i, purchase.getId());
                    pstmt.setString(++i, publicFeedback);
                    pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                    pstmt.executeUpdate();
                }
            }

        };

        private static class PurchasesClause extends DbClause {

            private PurchasesClause(String clause, boolean withPublicFeedbacksOnly, boolean completedOnly) {
                super(clause + (completedOnly ? " AND goods IS NOT NULL " : " ")
                        + (withPublicFeedbacksOnly ? " AND has_public_feedbacks = TRUE " : " "));
            }

            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                return index;
            }

        }

        private static final class LongPurchasesClause extends PurchasesClause {

            private final long value;

            private LongPurchasesClause(String columnName, long value, boolean withPublicFeedbacksOnly, boolean completedOnly) {
                super(columnName + " = ? ", withPublicFeedbacksOnly, completedOnly);
                this.value = value;
            }

            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                pstmt.setLong(index++, value);
                return index;
            }

        }

        private static final class SellerBuyerPurchasesClause extends PurchasesClause {

            private final long sellerId;
            private final long buyerId;

            private SellerBuyerPurchasesClause(long sellerId, long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
                super(" seller_id = ? AND buyer_id = ? ", withPublicFeedbacksOnly, completedOnly);
                this.sellerId = sellerId;
                this.buyerId = buyerId;
            }

            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                pstmt.setLong(index++, sellerId);
                pstmt.setLong(index++, buyerId);
                return index;
            }

        }

        public static int getCount() {
            return purchaseTable.getCount();
        }

        public static int getCount(boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new PurchasesClause(" TRUE ", withPublicFeedbacksOnly, completedOnly));
        }

        public static DbIterator<Purchase> getAllPurchases(int from, int to) {
            return purchaseTable.getAll(from, to);
        }

        public static DbIterator<Purchase> getPurchases(boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new PurchasesClause(" TRUE ", withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static DbIterator<Purchase> getSellerPurchases(long sellerId, boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new LongPurchasesClause("seller_id", sellerId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getSellerPurchaseCount(long sellerId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new LongPurchasesClause("seller_id", sellerId, withPublicFeedbacksOnly, completedOnly));
        }

        public static DbIterator<Purchase> getBuyerPurchases(long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new LongPurchasesClause("buyer_id", buyerId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getBuyerPurchaseCount(long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new LongPurchasesClause("buyer_id", buyerId, withPublicFeedbacksOnly, completedOnly));
        }

        public static DbIterator<Purchase> getSellerBuyerPurchases(final long sellerId, final long buyerId,
                                                                   boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new SellerBuyerPurchasesClause(sellerId, buyerId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getSellerBuyerPurchaseCount(final long sellerId, final long buyerId,
                                                                   boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new SellerBuyerPurchasesClause(sellerId, buyerId, withPublicFeedbacksOnly, completedOnly));
        }

        public static DbIterator<Purchase> getGoodsPurchases(long goodsId, boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new LongPurchasesClause("goods_id", goodsId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getGoodsPurchaseCount(final long goodsId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new LongPurchasesClause("goods_id", goodsId, withPublicFeedbacksOnly, completedOnly));
        }

        public static Purchase getPurchase(long purchaseId) {
            return purchaseTable.get(purchaseDbKeyFactory.newKey(purchaseId));
        }

        public static DbIterator<Purchase> getPendingSellerPurchases(final long sellerId, int from, int to) {
            DbClause dbClause = new DbClause(" seller_id = ? AND pending = TRUE ") {
                @Override
                public int set(PreparedStatement pstmt, int index) throws SQLException {
                    pstmt.setLong(index++, sellerId);
                    return index;
                }
            };
            return purchaseTable.getManyBy(dbClause, from, to);
        }

        static Purchase getPendingPurchase(long purchaseId) {
            Purchase purchase = getPurchase(purchaseId);
            return purchase == null || ! purchase.isPending() ? null : purchase;
        }

        private static DbIterator<Purchase> getExpiredPendingPurchases(Block block) {
            final int timestamp = block.getTimestamp();
            final int previousTimestamp = Nxt.getBlockchain().getBlock(block.getPreviousBlockId()).getTimestamp();
            DbClause dbClause = new DbClause(" deadline < ? AND deadline >= ? AND pending = TRUE ") {
                @Override
                public int set(PreparedStatement pstmt, int index) throws SQLException {
                    pstmt.setLong(index++, timestamp);
                    pstmt.setLong(index++, previousTimestamp);
                    return index;
                }
            };
            return purchaseTable.getManyBy(dbClause, 0, -1);
        }

        private static void init() {}


        private final long id;
        private final DbKey dbKey;
        private final long buyerId;
        private final long goodsId;
        private final long sellerId;
        private final int quantity;
        private final long priceNQT;
        private final int deadline;
        private final EncryptedData note;
        private final int timestamp;
        private boolean isPending;
        private EncryptedData encryptedGoods;
		private boolean goodsIsText;
        private EncryptedData refundNote;
        private boolean hasFeedbackNotes;
        private List<EncryptedData> feedbackNotes;
        private boolean hasPublicFeedbacks;
        private List<String> publicFeedbacks;
        private long discountNQT;
        private long refundNQT;

        private Purchase(Transaction transaction, Attachment.DigitalGoodsPurchase attachment, long sellerId) {
            this.id = transaction.getId();
            this.dbKey = purchaseDbKeyFactory.newKey(this.id);
            this.buyerId = transaction.getSenderId();
            this.goodsId = attachment.getGoodsId();
            this.sellerId = sellerId;
            this.quantity = attachment.getQuantity();
            this.priceNQT = attachment.getPriceNQT();
            this.deadline = attachment.getDeliveryDeadlineTimestamp();
            this.note = transaction.getEncryptedMessage() == null ? null : transaction.getEncryptedMessage().getEncryptedData();
            this.timestamp = transaction.getTimestamp();
            this.isPending = true;
        }

        private Purchase(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.dbKey = purchaseDbKeyFactory.newKey(this.id);
            this.buyerId = rs.getLong("buyer_id");
            this.goodsId = rs.getLong("goods_id");
            this.sellerId = rs.getLong("seller_id");
            this.quantity = rs.getInt("quantity");
            this.priceNQT = rs.getLong("price");
            this.deadline = rs.getInt("deadline");
            this.note = loadEncryptedData(rs, "note", "nonce");
            this.timestamp = rs.getInt("timestamp");
            this.isPending = rs.getBoolean("pending");
            this.encryptedGoods = loadEncryptedData(rs, "goods", "goods_nonce");
            this.refundNote = loadEncryptedData(rs, "refund_note", "refund_nonce");
            this.hasFeedbackNotes = rs.getBoolean("has_feedback_notes");
            this.hasPublicFeedbacks = rs.getBoolean("has_public_feedbacks");
            this.discountNQT = rs.getLong("discount");
            this.refundNQT = rs.getLong("refund");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO purchase (id, buyer_id, goods_id, seller_id, "
                    + "quantity, price, deadline, note, nonce, timestamp, pending, goods, goods_nonce, refund_note, "
                    + "refund_nonce, has_feedback_notes, has_public_feedbacks, discount, refund, height, latest) KEY (id, height) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.getId());
                pstmt.setLong(++i, this.getBuyerId());
                pstmt.setLong(++i, this.getGoodsId());
                pstmt.setLong(++i, this.getSellerId());
                pstmt.setInt(++i, this.getQuantity());
                pstmt.setLong(++i, this.getPriceNQT());
                pstmt.setInt(++i, this.getDeliveryDeadlineTimestamp());
                setEncryptedData(pstmt, this.getNote(), ++i);
                ++i;
                pstmt.setInt(++i, this.getTimestamp());
                pstmt.setBoolean(++i, this.isPending());
                setEncryptedData(pstmt, this.getEncryptedGoods(), ++i);
                ++i;
                setEncryptedData(pstmt, this.getRefundNote(), ++i);
                ++i;
                pstmt.setBoolean(++i, this.hasFeedbackNotes);
                pstmt.setBoolean(++i, this.hasPublicFeedbacks);
                pstmt.setLong(++i, this.getDiscountNQT());
                pstmt.setLong(++i, this.getRefundNQT());
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getBuyerId() {
            return buyerId;
        }

        public long getGoodsId() {
            return goodsId;
        }

        public long getSellerId() { return sellerId; }

        public int getQuantity() {
            return quantity;
        }

        public long getPriceNQT() {
            return priceNQT;
        }

        public int getDeliveryDeadlineTimestamp() {
            return deadline;
        }

        public EncryptedData getNote() {
            return note;
        }

        public boolean isPending() {
            return isPending;
        }

        private void setPending(boolean isPending) {
            this.isPending = isPending;
            purchaseTable.insert(this);
        }

        public int getTimestamp() {
            return timestamp;
        }

        public String getName() {
            return Goods.getGoods(goodsId).getName();
        }

        public EncryptedData getEncryptedGoods() {
            return encryptedGoods;
        }

        public boolean goodsIsText() {
            return goodsIsText;
        }

        private void setEncryptedGoods(EncryptedData encryptedGoods, boolean goodsIsText) {
            this.encryptedGoods = encryptedGoods;
            this.goodsIsText = goodsIsText;
            purchaseTable.insert(this);
        }

        public EncryptedData getRefundNote() {
            return refundNote;
        }

        private void setRefundNote(EncryptedData refundNote) {
            this.refundNote = refundNote;
            purchaseTable.insert(this);
        }

        public boolean hasFeedbackNotes() {
            return hasFeedbackNotes;
        }

        public List<EncryptedData> getFeedbackNotes() {
            if (!hasFeedbackNotes) {
                return null;
            }
            feedbackNotes = feedbackTable.get(feedbackDbKeyFactory.newKey(this));
            return feedbackNotes;
        }

        private void addFeedbackNote(EncryptedData feedbackNote) {
            if (getFeedbackNotes() == null) {
                feedbackNotes = new ArrayList<>();
            }
            feedbackNotes.add(feedbackNote);
            this.hasFeedbackNotes = true;
            purchaseTable.insert(this);
            feedbackTable.insert(this, feedbackNotes);
		}

        public boolean hasPublicFeedbacks() {
            return hasPublicFeedbacks;
        }

        public List<String> getPublicFeedbacks() {
            if (!hasPublicFeedbacks) {
                return null;
            }
            publicFeedbacks = publicFeedbackTable.get(publicFeedbackDbKeyFactory.newKey(this));
            return publicFeedbacks;
        }

        private void addPublicFeedback(String publicFeedback) {
            if (getPublicFeedbacks() == null) {
                publicFeedbacks = new ArrayList<>();
            }
            publicFeedbacks.add(publicFeedback);
            this.hasPublicFeedbacks = true;
            purchaseTable.insert(this);
            publicFeedbackTable.insert(this, publicFeedbacks);
        }

        public long getDiscountNQT() {
            return discountNQT;
        }

        public void setDiscountNQT(long discountNQT) {
            this.discountNQT = discountNQT;
            purchaseTable.insert(this);
        }

        public long getRefundNQT() {
            return refundNQT;
        }

        public void setRefundNQT(long refundNQT) {
            this.refundNQT = refundNQT;
            purchaseTable.insert(this);
        }

    }

    private static final class SellerDbClause extends DbClause {

        private final long sellerId;

        private SellerDbClause(long sellerId, boolean inStockOnly) {
            super(" seller_id = ? " + (inStockOnly ? "AND delisted = FALSE AND quantity > 0" : ""));
            this.sellerId = sellerId;
        }

        @Override
        public int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setLong(index++, sellerId);
            return index;
        }

    }

    static void listGoods(Transaction transaction, Attachment.DigitalGoodsListing attachment) {
        Goods goods = new Goods(transaction, attachment);
        Tag.add(goods);
        Goods.goodsTable.insert(goods);
        goodsListeners.notify(goods, Event.GOODS_LISTED);
    }

    static void delistGoods(long goodsId) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(goodsId));
        if (! goods.isDelisted()) {
            goods.setDelisted(true);
            goodsListeners.notify(goods, Event.GOODS_DELISTED);
        } else {
            throw new IllegalStateException("Goods already delisted");
        }
    }

    static void changePrice(long goodsId, long priceNQT) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(goodsId));
        if (! goods.isDelisted()) {
            goods.changePrice(priceNQT);
            goodsListeners.notify(goods, Event.GOODS_PRICE_CHANGE);
        } else {
            throw new IllegalStateException("Can't change price of delisted goods");
        }
    }

    static void changeQuantity(long goodsId, int deltaQuantity) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(goodsId));
        if (! goods.isDelisted()) {
            goods.changeQuantity(deltaQuantity);
            goodsListeners.notify(goods, Event.GOODS_QUANTITY_CHANGE);
        } else {
            throw new IllegalStateException("Can't change quantity of delisted goods");
        }
    }

    static void purchase(Transaction transaction,  Attachment.DigitalGoodsPurchase attachment) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(attachment.getGoodsId()));
        if (! goods.isDelisted() && attachment.getQuantity() <= goods.getQuantity() && attachment.getPriceNQT() == goods.getPriceNQT()
                && attachment.getDeliveryDeadlineTimestamp() > Nxt.getBlockchain().getLastBlock().getTimestamp()) {
            goods.changeQuantity(-attachment.getQuantity());
            Purchase purchase = new Purchase(transaction, attachment, goods.getSellerId());
            Purchase.purchaseTable.insert(purchase);
            purchaseListeners.notify(purchase, Event.PURCHASE);
        } else {
            Account buyer = Account.getAccount(transaction.getSenderId());
            buyer.addToUnconfirmedBalanceNQT(Convert.safeMultiply(attachment.getQuantity(), attachment.getPriceNQT()));
            // restoring the unconfirmed balance if purchase not successful, however buyer still lost the transaction fees
        }
    }

    static void deliver(Transaction transaction, Attachment.DigitalGoodsDelivery attachment) {
        Purchase purchase = Purchase.getPendingPurchase(attachment.getPurchaseId());
        purchase.setPending(false);
        long totalWithoutDiscount = Convert.safeMultiply(purchase.getQuantity(), purchase.getPriceNQT());
        Account buyer = Account.getAccount(purchase.getBuyerId());
        buyer.addToBalanceNQT(Convert.safeSubtract(attachment.getDiscountNQT(), totalWithoutDiscount));
        buyer.addToUnconfirmedBalanceNQT(attachment.getDiscountNQT());
        Account seller = Account.getAccount(transaction.getSenderId());
        seller.addToBalanceAndUnconfirmedBalanceNQT(Convert.safeSubtract(totalWithoutDiscount, attachment.getDiscountNQT()));
        purchase.setEncryptedGoods(attachment.getGoods(), attachment.goodsIsText());
        purchase.setDiscountNQT(attachment.getDiscountNQT());
        purchaseListeners.notify(purchase, Event.DELIVERY);
    }

    static void refund(long sellerId, long purchaseId, long refundNQT, Appendix.EncryptedMessage encryptedMessage) {
        Purchase purchase = Purchase.purchaseTable.get(Purchase.purchaseDbKeyFactory.newKey(purchaseId));
        Account seller = Account.getAccount(sellerId);
        seller.addToBalanceNQT(-refundNQT);
        Account buyer = Account.getAccount(purchase.getBuyerId());
        buyer.addToBalanceAndUnconfirmedBalanceNQT(refundNQT);
        if (encryptedMessage != null) {
            purchase.setRefundNote(encryptedMessage.getEncryptedData());
        }
        purchase.setRefundNQT(refundNQT);
        purchaseListeners.notify(purchase, Event.REFUND);
    }

    static void feedback(long purchaseId, Appendix.EncryptedMessage encryptedMessage, Appendix.Message message) {
        Purchase purchase = Purchase.purchaseTable.get(Purchase.purchaseDbKeyFactory.newKey(purchaseId));
        if (encryptedMessage != null) {
            purchase.addFeedbackNote(encryptedMessage.getEncryptedData());
        }
        if (message != null) {
            purchase.addPublicFeedback(Convert.toString(message.getMessage()));
        }
        purchaseListeners.notify(purchase, Event.FEEDBACK);
    }

    private static EncryptedData loadEncryptedData(ResultSet rs, String dataColumn, String nonceColumn) throws SQLException {
        byte[] data = rs.getBytes(dataColumn);
        if (data == null) {
            return null;
        }
        return new EncryptedData(data, rs.getBytes(nonceColumn));
    }

    private static void setEncryptedData(PreparedStatement pstmt, EncryptedData encryptedData, int i) throws SQLException {
        if (encryptedData == null) {
            pstmt.setNull(i, Types.VARBINARY);
            pstmt.setNull(i + 1, Types.VARBINARY);
        } else {
            pstmt.setBytes(i, encryptedData.getData());
            pstmt.setBytes(i + 1, encryptedData.getNonce());
        }
    }

}
