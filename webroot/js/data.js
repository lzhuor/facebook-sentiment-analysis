var datasetPages = {};
var dataset = {};
var commentsData = [];
var polarityData = [];

function onDataLoaded() {

    // extract all comments
    commentsData = extractComments(dataset.data);

    // extract all sentiment polarities
    polarityData = extractPolarities(commentsData);

}

function extractPolarities(comments) {
    var polarities = {"negative": 0, "positive": 0, "neutral": 0, "total": 0};
    var id = "";
    $.each(comments, function (i, e) {
        id = e.id;
        var polarity = e.polarity;
        polarities[polarity] += 1;
        polarities.total += 1;
    });
    return [
        {"polarity": "Positive", "count": polarities.positive, "id": id},
        {"polarity": "Negative", "count": polarities.negative, "id": id},
        {"polarity": "Neutral", "count": polarities.neutral, "id": id}
    ];
}
function extractComments(data) {
    var cssMap = {"negative": "label-warning", "positive": "label-success", "neutral": "label-primary"};
    var commentsData = [];

    var count = 0;
    $.each(data, function (i, v) {
        for (var j in v.comments) {

            var e = v.comments[j];
            if (typeof e.sentiment === 'undefined')
                continue;

            var obj = {};
            obj.index = count;
            count++;
            obj.mainpost = v.message;
            obj.id = v.id;
            obj.polarity = e.sentiment.sentiment_polarity;
            obj.name = e.from.name;
            obj.comment = e.message;
            obj.score = e.sentiment.sentiment_score;
            obj.score = formatScore(obj.score);
            obj.css = cssMap[obj.polarity];
            obj.theme = e.sentiment.theme;
            obj.category = e.sentiment.category;
            obj.entity = e.sentiment.entity;

            commentsData.push(obj);
        }
    });
    return commentsData;
}

function formatPercentage(value) {
    // value passed in is in the percentage
    return d3.format('.1%')(value);
}

function formatScore(value) {
    // value passed in is in the percentage
    return d3.format('.2f')(value);
}

function getFacebookId(url) {
    var id = url.split('.com/')[1];

    if (id.indexOf('/') > -1) {
        id = id.split('/')[0];
    }

    if (id.indexOf('?') > -1) {
        id = id.split('?')[0];
    }

    console.log(id);
    return id;
}

$.urlParam = function (name) {
    var results = new RegExp('[\?&]' + name + '=([^&#]*)').exec(window.location.href);
    return results[1] || 0;
}


