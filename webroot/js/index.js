var dt = undefined;

$(document).ready(function () {

    // set cache to false
    $.ajaxSetup({cache: false});

    $("#input-url").on("click", function () {
        $(this).select();
    });

    $('#form-url').on('submit', function (event) {
        event.preventDefault();

        var url = $('#input-url').val();
        if (url == '' || url.indexOf('facebook.com/') < 0) {
            $('#input-url').val('');
            return false;
        }

        var id = getFacebookId(url);

        if (id != '') {
            // submit pull request
            $.get('/api/fb_posts/update/' + id, function (data) {
                // refresh data
                // loadData();
                location.reload();
            });
        }

        return false;
    });

    $.addTemplateFormatter("StatusFormatter",
        function (value, template) {
            if (value == 0) {
                return '<span class="glyphicon glyphicon-cloud-download"></span> Pulling...';
            } else if (value == 1) {
                return '<a class="perform-analysis-link btn btn-warning btn-block"><span class="glyphicon glyphicon-arrow-right"></span> Analyze</a> <a class="pullmore-link btn btn-primary btn-block"><span class="glyphicon glyphicon-cloud-download"></span> Pull Latest</a>';
            } else if (value == 2) {
                return '<span class="glyphicon glyphicon-hourglass"></span> Analyzing...';
            } else {
                return '<a class="analysis-link btn btn-success btn-block"><span class="glyphicon glyphicon-signal"></span> View Analysis</a> <a class="pullmore-link btn btn-primary btn-block"><span class="glyphicon glyphicon-cloud-download"></span> Pull Latest</a>';
            }
        });

    // load data
    loadData();

});


function loadData() {
    $.getJSON('/api/fb_pages', function (data) {
        console.log('data loaded');
        datasetPages = data;
        console.log(datasetPages.data);

        // populate data on table
        $("#table-extractions tbody").loadTemplate($("#template"), datasetPages.data, {
            success: onTableLoaded
        });
    });
}

function onTableLoaded() {

    console.log('table loaded');

    if (typeof dt !== 'undefined') {
        console.log('destroy datatable');
        dt.destroy();
    }

    $('.perform-analysis-link').attr('href', function (i, v) {
        v = $(this).parent('div').data('id');
        return "/api/fb_posts/do_sentiment/" + v;
    });

    $('.perform-analysis-link').on('click', function () {
        var url = $(this).attr('href');

        $.get(url, function (data) {
            // loadData();
            location.reload();
        });

        return false;
    });

    $('.pullmore-link').on('click', function () {
        var id = $(this).parent('div').data('id');

        // submit pull request
        $.get('/api/fb_posts/update/' + id, function (data) {
            // refresh data
            // loadData();
            location.reload();
        });

        return false;
    });

    $('.analysis-link').attr('href', function (i, v) {
        v = $(this).parent('div').data('id');
        return "analysis.html?id=" + v;
    });


    dt = $('#table-extractions').DataTable({
        dom: 'Bfrtip',
        buttons: [],
        lengthMenu: [
            [20, 50, 100, "All"]
        ],
        columns: [{
            orderable: false
        }, null, null, null]
    });

    $("#table-extractions_filter").append($("#template-filter-btn").html());

    $("#btn-status a").on('click', function () {
        var p = $(this).text();
        if (p.indexOf('All') != -1) {
            p = '';
        }
        dt
            .columns(3)
            .search(p)
            .draw();
    });
}
