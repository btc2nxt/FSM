package nxt.http;

public enum APITag {

    ACCOUNTS("Accounts"), ALIASES("Aliases"), AE("Asset Exchange"), CREATE_TRANSACTION("Create Transaction"),
    BLOCKS("Blocks"), DGS("Digital Goods Store"), FORGING("Forging"), INFO("Server Info"), MESSAGES("Messages"),
    TRANSACTIONS("Transactions"), TOKENS("Tokens"), VS("Voting System"), STATISTICS("Statistic"), SEARCH("Search"), 
    UTILS("Utils"), DEBUG("Debug"), AT("Automated Transaction");

    private final String displayName;

    private APITag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
