package lzr.io.sentiment.service;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.logging.Logger;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import lzr.io.sentiment.verticle.ModelAttribute;

import java.util.ArrayList;

/**
 * Created by lzr.io on 8/10/2015.
 */
public class DataCollectionService {

    protected final Vertx vertx;
    protected final JsonObject mongodbConfig;
    private final MongoClient mongoClient;
    private Logger vertxLogger;

    public DataCollectionService(Vertx vertx, JsonObject mongodbConfig) {
        this.vertx = vertx;
        this.mongodbConfig = mongodbConfig;
        this.mongoClient = MongoClient.createShared(vertx, mongodbConfig);
        this.vertxLogger = LoggerFactory.getLogger(DataCollectionService.class);
    }

    /**
     * Saves posts and comments of each post into mongoDB
     * @param userId Facebook user's id
     * @param postsWithComments An array contains posts and comments of each post
     * @param commentIds Existing comment Ids
     */
    public void savePostsWithComments(String userId, JsonArray postsWithComments, ArrayList<String> commentIds) {
        postsWithComments.forEach(object ->
        {
            JsonObject postWithCommentsJsonObject = (JsonObject) object;
            String postId = postWithCommentsJsonObject.getString("id");
            postId = postId.substring(postId.lastIndexOf("_") + 1);

            String message = postWithCommentsJsonObject.getString("message");

            // Facebook will return null if the user's post is an image, set message to "[image]" for reference
            if (message == null) {
                message = "[image]";
            }

            String createdTime = postWithCommentsJsonObject.getString("created_time");

            // Query for updating current postId
            JsonObject queryForUpdate = new JsonObject().put("id", postId);
            JsonObject update = new JsonObject().put("$set" , new JsonObject()
                            .put("message", message)
                            .put("created_time", createdTime)
            );
            saveDocToMongoDb(userId, queryForUpdate, update);

            JsonArray commentsArray = new JsonArray();

            if (postWithCommentsJsonObject.getJsonObject("comments") != null) {
                commentsArray = postWithCommentsJsonObject.getJsonObject("comments").getJsonArray("data");
            }

            // Add comments of belongs to the postId to current postId's document
            addComments(userId, postId, commentsArray, commentIds);

        });
    }

    /**
     * Saves page information
     * @param pageInfo Information of the facebook public page
     */
    public void savePageInfo(JsonObject pageInfo) {
        JsonObject query = new JsonObject().put("id", pageInfo.getValue("id"));
        JsonObject update = new JsonObject().put("$set", pageInfo);
        saveDocToMongoDb("page_info", query, update);
    }

    /**
     * Saves documents into mongoDB
     * @param collection the collection to be updated
     * @param query query of updating operation
     * @param update updating commend and data of updating operation
     */
    public void saveDocToMongoDb(String collection, JsonObject query, JsonObject update) {

        UpdateOptions options = new UpdateOptions().setUpsert(true);

        mongoClient.updateWithOptions(collection, query, update, options, res -> {

            if (res.succeeded()) {

                //vertxLogger.info(collection + ":" + query.toString() + " is updatd!");

            } else {
                throw new RuntimeException(res.cause());
            }

        });
    }

    /**
     * Push comments into "comments" field of each post
     * @param userId Facebook user's id
     * @param postId Post id of the user's post
     * @param comments Comments without sentiment analysis
     * @param commentIds Existing comment Ids: For checking esistence of comments to avoid duplicated records
     */
    public void addComments(String userId, String postId, JsonArray comments, ArrayList<String> commentIds){

        JsonArray newComments = new JsonArray();

        comments.forEach(object ->
        {
            JsonObject commentsJsonObject = (JsonObject) object;
            if (!commentsJsonObject.getString("message").trim().isEmpty() && !commentIds.contains(commentsJsonObject.getString("id"))) {
                newComments.add(commentsJsonObject);
            }
        });

        JsonObject query = new JsonObject().put("id", postId);
        JsonObject update = new JsonObject().put("$push", new JsonObject().put("comments", new JsonObject().put("$each", newComments)));

        // TODO: Removes s.o.u.t. of counter for new comments
        // System.out.println("new comments counter: "+ newComments.size());

        saveDocToMongoDb(userId, query, update);

    }

    /**
     * Updates comments field with sentiment result
     * @param userId Facebook user's id
     * @param postId Post id of the user's post
     * @param processedCommentsArrayWithSentiment Processed comments array with sentiment
     */
    public void updateCommentField(String userId, String postId, JsonArray processedCommentsArrayWithSentiment) {
        JsonObject query = new JsonObject().put("id", postId);
        JsonObject update = new JsonObject().put("$set", new JsonObject().put("comments", processedCommentsArrayWithSentiment));

        UpdateOptions options = new UpdateOptions().setUpsert(true);

        mongoClient.updateWithOptions(userId, query, update, options, res -> {

            if (res.succeeded()) {

                vertxLogger.info(userId + ":" + query.toString() + " is updatd!");

            } else {
                throw new RuntimeException(res.cause());
            }

        });
    }

    public void setPageStatusReadyForAnalyzing(String userId) {
        JsonObject query = new JsonObject().put("id", userId);
        JsonObject update = new JsonObject().put("$set", new JsonObject().put("status", ModelAttribute.PAGE_STATUS_READY_FOR_ANALYZING));

        mongoClient.update(ModelAttribute.MONGO_COLLECTION_PAGE_INFO, query, update, res -> {

            if (res.succeeded()) {

                vertxLogger.info(userId + ":" + query.toString() + "'s status is updatd to ReadyForAnalyzing!");

            } else {
                throw new RuntimeException(res.cause());
            }

        });
    }

    public void setPageStatusAnalyzing(String userId) {
        JsonObject query = new JsonObject().put("id", userId);
        JsonObject update = new JsonObject().put("$set", new JsonObject().put("status", ModelAttribute.PAGE_STATUS_ANALYZING));

        mongoClient.update(ModelAttribute.MONGO_COLLECTION_PAGE_INFO, query, update, res -> {

            if (res.succeeded()) {

                vertxLogger.info(userId + ":" + query.toString() + "'s status is updatd to Analyzing!");

            } else {
                throw new RuntimeException(res.cause());
            }

        });
    }

    public void setPageStatusReadyForViewingResult(String userId) {
        JsonObject query = new JsonObject().put("id", userId);
        JsonObject update = new JsonObject().put("$set", new JsonObject().put("status", ModelAttribute.PAGE_STATUS_READY_FOR_VIEWING_RESULT));

        mongoClient.update(ModelAttribute.MONGO_COLLECTION_PAGE_INFO, query, update, res -> {

            if (res.succeeded()) {

                vertxLogger.info(userId + ":" + query.toString() + "'s status is updatd to ReadyForViewing!");

            } else {
                throw new RuntimeException(res.cause());
            }

        });
    }
}
