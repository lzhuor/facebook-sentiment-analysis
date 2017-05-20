package lzr.io.sentiment.verticle;

/**
 * Created by lzr.io on 15/10/15.
 */
public interface ModelAttribute {
    String REQUEST_PARAM_USERID = "userid";
    String MONGO_COLLECTION_PAGE_INFO = "page_info";

    int PAGE_STATUS_PULLING = 0;
    int PAGE_STATUS_READY_FOR_ANALYZING = 1;
    int PAGE_STATUS_ANALYZING = 2;
    int PAGE_STATUS_READY_FOR_VIEWING_RESULT = 3;
}
