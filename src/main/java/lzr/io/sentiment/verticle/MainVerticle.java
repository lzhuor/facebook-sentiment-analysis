package lzr.io.sentiment.verticle;

import io.vertx.core.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import lzr.io.sentiment.service.DataCollectionService;
import org.apache.http.HttpStatus;

import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * This is a verticle. A verticle is a _Vert.x component_. This verticle is implemented in Java, but you can
 * implement them in JavaScript, Groovy or even Ruby.
 *
 * Created by lzr.io on 5/10/2015.
 */
public class MainVerticle extends AbstractVerticle {
    private JsonObject mongodbConfig;

    /**
     * This method is called when the verticle is deployed. It creates a HTTP server and registers a simple request
     * handler.
     * <p/>
     * Notice the `listen` method. It passes a lambda checking the port binding result. When the HTTP server has been
     * bound on the port, it call the `complete` method to inform that the starting has completed. Else it reports the
     * error.
     *
     * @param fut the future
     */
    @Override
    public void start(Future<Void> fut) {
        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(20).setBlockedThreadCheckInterval(100000));

        // Create a router object.
        Router router = Router.router(vertx);

        // Set 10 seconds timeout
        router.route().handler(TimeoutHandler.create(config().getInteger(ServerAttribute.HTTP_SERVICE_TIMEOUT_KEY, ServerAttribute.HTTP_SERVICE_DEFAULT_TIMEOUT)));

        // Set mongoDb Config
        mongodbConfig = new JsonObject()
                .put(ServerAttribute.MONGODB_SERVER_CONN_URL_KEY, config().getString(ServerAttribute.MONGODB_SERVER_CONN_CONF_JSON_KEY))
                .put(ServerAttribute.MONGODB_DB_NAME_KEY, config().getString(ServerAttribute.MONGODB_DB_NAME_CONF_JSON_KEY));

        //router.route("/").handler(StaticHandler.create());
        router.route("/").handler(StaticHandler.create().setCachingEnabled(false));

        // API URI
        // Get politicans' information cached in database
        router.get("/api/fb_pages/").handler(this::getAllPublicPagesInfo);

        // Get politican's post and comments from database
        router.get("/api/fb_posts/update/:userid").handler(this::updateFacebookPostsAndCommentsFrom);

        // Update politican's post and comments
        router.get("/api/fb_posts/get/:userid").handler(this::getPoliticanPostsAndComments);

        // Do sentiment analysis for a politican's post and comments
        router.get("/api/fb_posts/do_sentiment/:userid").handler(this::doSentimentAnalysis);

        router.route("/*").handler(StaticHandler.create());

        router.route("/css/*").handler(StaticHandler.create("css").setCachingEnabled(false));
        router.route("/js/*").handler(StaticHandler.create("js").setCachingEnabled(false));


        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                        // Retrieve the port from the configuration, default to 8080.
                        config().getInteger(ServerAttribute.SERVER_PORT_CONF_KEY, ServerAttribute.SERVER_DEFAULT_PORT),
                        result -> {
                            if (result.succeeded()) {
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                            }
                        }
                );

        System.out.println(config().toString());

        vertx
                .deployVerticle("lzr.io.sentiment.verticle.WorkerVerticle",
                        new DeploymentOptions()
                                .setWorker(true)
                                .setConfig(config())
                );
    }

    /**
     * Get posts with comments by using Facebook Graph API with a generated token.
     *
     * @param routingContext route information of current service URI
     */
    private void updateFacebookPostsAndCommentsFrom(RoutingContext routingContext) {
        MongoClient mongoClient = MongoClient.createShared(vertx, mongodbConfig);

        DataCollectionService dataCollectionService = new DataCollectionService(vertx, mongodbConfig);

        HttpClientOptions options = new HttpClientOptions()
                .setDefaultHost(config().getString(ServerAttribute.FACEBOOK_GRAPH_API_HOST))
                .setKeepAlive(false)
                .setSsl(true)
                .setDefaultPort(ServerAttribute.HTTPS_FACEBOOK_CONN_PORT);

        HttpClient client = vertx.createHttpClient(options);

        String userId =  routingContext.request().getParam(ModelAttribute.REQUEST_PARAM_USERID);

        client.getNow("/v2.4/" + userId + "?fields=posts.limit(3)%7Bcomments%2Cmessage%2Ccreated_time%7D%2Cname%2Cabout%2Cpicture.width(316).height(316)&access_token=" + config().getString(ServerAttribute.FACEBOOK_GRAPH_API_TOKEN), response -> {
            // Asynchronous response handler
            response.bodyHandler(totalBuffer -> {
                try {
                    JsonObject returnMessage = new JsonObject();

                    // Now all the body has been read
                    byte[] bytes = totalBuffer.getBytes();
                    String responseString = new String(bytes, Charset.forName("UTF-8"));

                    JsonObject fbReturnJson = new JsonObject(responseString);

                    if (response.statusCode() == HttpStatus.SC_OK) {

                        ArrayList<String> commentIds = new ArrayList<>();

                        String userDigitalId = fbReturnJson.getString("id");

                        // Find comments for current postId
                        mongoClient.find(userDigitalId, new JsonObject(), res -> {
                            if (res.succeeded()) {
                                for (JsonObject json : res.result()) {
                                    // Add existing commentIds into an arraylist as a record
                                    json.getJsonArray("comments").forEach(comment -> {
                                        JsonObject commentObject = (JsonObject) comment;
                                        commentIds.add(commentObject.getString("id"));
                                    });
                                }

                                JsonArray postsWithComments = fbReturnJson.getJsonObject("posts").getJsonArray("data");

                                JsonObject pageInfo = new JsonObject()
                                        .put("id", fbReturnJson.getString("id"))
                                        .put("name", fbReturnJson.getString("name"))
                                        .put("about", fbReturnJson.getString("about"))
                                        .put("picture", fbReturnJson.getJsonObject("picture"))
                                        .put("status", ModelAttribute.PAGE_STATUS_READY_FOR_ANALYZING);

                                // Store posts and comments information returned from facebook graph API result into MongoDB
                                dataCollectionService.savePostsWithComments(pageInfo.getString("id"), postsWithComments, commentIds);
                                dataCollectionService.savePageInfo(pageInfo);
                                // Update each comment
                                postsWithComments.forEach(object -> {
                                    JsonObject postWithCommentsJsonObject = (JsonObject) object;
                                    String postId = postWithCommentsJsonObject.getString("id");
                                    postId = postId.substring(postId.lastIndexOf("_") + 1);

                                    // If there is any comment with the post
                                    if (postWithCommentsJsonObject.containsKey("comments")) {
                                        // if there is a next page of comments
                                        if (postWithCommentsJsonObject.getJsonObject("comments").getJsonObject("paging").containsKey("next")) {
                                            String nextPageURI = postWithCommentsJsonObject.getJsonObject("comments").getJsonObject("paging").getString("next");

                                            // Get comments in next pages
                                            getCommentsInNextPages(fbReturnJson.getString("id"), postId, client, nextPageURI, dataCollectionService, commentIds);
                                        }
                                    }
                                });
                            } else {
                                throw new RuntimeException(res.cause());
                            }
                        });

                        // if everything goes well, return page information
                        returnMessage =  new JsonObject()
                                .put("id", fbReturnJson.getString("id"))
                                .put("name", fbReturnJson.getString("name"))
                                .put("about", fbReturnJson.getString("about"))
                                .put("picture", fbReturnJson.getJsonObject("picture"));

                    } else {
                        // error message from facebook graph api
                        returnMessage = fbReturnJson;
                    }

                    // Return Pages Information
                    routingContext.response()
                            .setStatusCode(response.statusCode())
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(returnMessage.encodePrettily());
                } catch (NullPointerException ne) {
                    ne.printStackTrace();
                    routingContext.response()
                            .setStatusCode(response.statusCode())
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(new JsonObject().put("error", ne.toString()).encode());
                } catch (RuntimeException re) {
                    re.printStackTrace();
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(new JsonObject().put("error", re.toString()).encode());
                }

            });

        });
    }

    /**
     * Gets pages information
     *
     * @param routingContext route information of current service URI
     */
    private void getAllPublicPagesInfo(RoutingContext routingContext) {
        MongoClient mongoClient = MongoClient.createShared(vertx, mongodbConfig);

        JsonObject query = new JsonObject();
        JsonArray docsArray = new JsonArray();
        try {
            mongoClient.find(ModelAttribute.MONGO_COLLECTION_PAGE_INFO, query, res -> {
                if (res.succeeded()) {
                    res.result().forEach(docsArray::add);
                    JsonObject returnJson = new JsonObject().put("data", docsArray);

                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(returnJson.toString());

                } else {
                    throw new RuntimeException(res.cause());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            routingContext.response()
                    .setStatusCode(500)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("error", e.toString()).toString());
        }
    }

    /**
     * Gets comments according to a specific "Next Page URI" from Facebook graph API
     *
     * @param userId                UserId of the politican's public page
     * @param postId                PostId of the facebook post
     * @param client                Asyn VertX HttpClient
     * @param nextPageURI           NextPageURI of facebook
     * @param dataCollectionService DataCollectionService object
     */
    private void getCommentsInNextPages(String userId, String postId, HttpClient client, String nextPageURI, DataCollectionService dataCollectionService, ArrayList<String> commentIds) {
        client.getNow(nextPageURI, response -> {
            response.bodyHandler(totalBuffer -> {
                // Now all the body has been read
                byte[] bytes = totalBuffer.getBytes();
                String responseString = new String(bytes, Charset.forName("UTF-8"));
                // Cast response String to JsonObject
                JsonObject fbReturnJson = new JsonObject(responseString);
                // Get comments array
                JsonArray comments = fbReturnJson.getJsonArray("data");
                if (response.statusCode() == HttpStatus.SC_OK) {
                    dataCollectionService.addComments(userId, postId, comments, commentIds);
                    if (fbReturnJson.getJsonObject("paging").containsKey("next")) {
                        String nextPageURL = fbReturnJson.getJsonObject("paging").getString("next");
                        String thisNextPageURI = nextPageURL.substring(nextPageURL.lastIndexOf("graph.facebook.com") + 18);
                        getCommentsInNextPages(userId, postId, client, thisNextPageURI, dataCollectionService, commentIds);
                    }
                }

                //System.out.println("postId: "+ postId +" ,comments size:" + comments.size());
            });
        });
    }

    /**
     * Get a politican's posts and comments from database
     *
     * @param routingContext route information of current service URI
     */
    private void getPoliticanPostsAndComments(RoutingContext routingContext) {
        String userId = routingContext.request().getParam(ModelAttribute.REQUEST_PARAM_USERID);
        MongoClient mongoClient = MongoClient.createShared(vertx, mongodbConfig);

        JsonObject query = new JsonObject();
        JsonArray docsArray = new JsonArray();
        try {
            mongoClient.find(userId, query, res -> {
                if (res.succeeded()) {
                    res.result().forEach(docsArray::add);
                    JsonObject returnJson = new JsonObject().put("data", docsArray);

                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(returnJson.toString());

                } else {
                    throw new RuntimeException(res.cause());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            routingContext.response()
                    .setStatusCode(500)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("error", e.toString()).toString());
        }
    }

    /**
     * Do sentiment analysis for a politican
     *
     * @param routingContext routing context of current service
     */
    private void doSentimentAnalysis(RoutingContext routingContext) {
        vertx.eventBus().send(
                "sentiment.analysis",
                // TODO: Add input check and return status code 403 if input is in correct
                routingContext.request().getParam(ModelAttribute.REQUEST_PARAM_USERID),
                r -> {
                    System.out.println("[Main] Receiving reply ' " + r.result().body()
                            + "' in " + Thread.currentThread().getName());

                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(new JsonObject().put("message", r.result().body()).encode());
                }
        );
    }
}
