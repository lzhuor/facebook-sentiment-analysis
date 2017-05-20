var dt;
var id;
var profileNameMapper = {};
var names = [];

$(document).ready(function () {

    // set cache to false
    $.ajaxSetup({cache: false, async: false});

    id = $.urlParam('id');

    // get name and pic
    $.getJSON('/api/fb_pages', function (data) {
        $.each(data.data, function (i, v) {
            if (v.id == id) {
                // set name and pic
                names[0] = v.name;
                $('#profile-name').text(v.name);
                $('#profile-pic').attr('src', v.picture.data.url);
                return false;
            }
        });
    });

    var jsonUrl = "/api/fb_posts/get/" + id;

    $.getJSON(jsonUrl, function (data) {
        console.log('data loaded');

        dataset = data; // set global variable

        onDataLoaded();
        drawPiechart(polarityData);
        drawGroupedBarchar([polarityData]);
        drawTable();

    });

    $('#select-profile').on('change', function (event) {
        var id = $(this).val();
        console.log('selected profile', id);

        names[1] = profileNameMapper[id];

        var jsonUrl = "/api/fb_posts/get/" + id;

        $.getJSON(jsonUrl, function (data) {
            console.log('data loaded');

            polarityData2 = extractPolarities(extractComments(data.data));

            var chartData = [polarityData, polarityData2];
            console.log(chartData);
            drawGroupedBarchar(chartData);

        });
    });

    loadProfiles();

});


function drawTable() {

    $("#table-comments tbody").loadTemplate($("#template"), commentsData);

    dt = $('#table-comments').DataTable({
        dom: 'Bfrtip',
        buttons: [{extend: 'csv', text: '<span class="glyphicon glyphicon-download-alt"></span> CSV'}, {
            extend: 'excel',
            text: '<span class="glyphicon glyphicon-download-alt"></span> Excel'
        }],
        lengthMenu: [[20, 50, 100, "All"]],
        columns: [null, null, {orderable: false}, null, {orderable: false}]
    });

    $("#table-comments_filter").append($("#template-filter-btn").html());

    $(".dt-buttons").addClass("pull-right");

    $("#btn-polarity button").on('click', function () {
        var p = $(this).text();
        if (p == 'All') {
            p = '';
        }
        dt
            .columns(2)
            .search(p)
            .draw();
    });


    $("#sentiment-comments table").on('click', '.view-details-link', function () {
        var index = $(this).data('index');
        console.log(index, 'clicked');
        showDialog(index);
    });

}

function loadProfiles() {
    $.getJSON('/api/fb_pages', function (data) {
        console.log('data loaded');
        $.each(data.data, function (i, v) {
            profileNameMapper[v.id] = v.name;
        });
        data = $.grep(data.data, function (v) {
            return v.id != id && v.status == 3;
        });
        // populate data on select
        $("#select-profile").loadTemplate($("#template-select"), data, {
            success: onSelectLoaded
        });
    });
}

function onSelectLoaded() {

    console.log('select loaded');

}

function drawGroupedBarchar(polarityData) {

    for (var i in polarityData) {
        for (var j in polarityData[i]) {
            polarityData[i][j]['user'] = names[i];
        }
    }

    console.log(polarityData)

    var SVG_SELECTOR = "#svg-polarity-barchart",
        WIDTH = $(SVG_SELECTOR).width(),
        HEIGHT = $(SVG_SELECTOR).height(),
        MARGIN = {
            top: 30,
            right: 40,
            bottom: 30,
            left: 90
        },
        INNER_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom,
        INNER_WIDTH = WIDTH - MARGIN.left - MARGIN.right;

    $(SVG_SELECTOR).html('');

    // polarityData = [polarityData[0]];
    var group = [polarityData[0][0].user.toUpperCase()];
    var total = {};
    total[group[0]] = d3.sum(polarityData[0].map(function (o) {
        return o.count
    }));
    if (polarityData.length > 1) {
        group = [polarityData[0][0].user.toUpperCase(), polarityData[1][0].user.toUpperCase()];
        total[group[1]] = d3.sum(polarityData[1].map(function (o) {
            return o.count
        }));
    }

    console.log('total', total)

    var y = d3.scale.linear()
        .domain([0, 1])
        .range([INNER_HEIGHT, 0]);

    var x0 = d3.scale.ordinal()
        .domain(polarityData[0].map(function (o) {
            return o.polarity;
        }))
        .rangeBands([0, INNER_WIDTH], .2);

    var x1 = d3.scale.ordinal()
        .domain(group)
        .rangeBands([0, x0.rangeBand()]);

    var xAxis = d3.svg.axis()
        .scale(x0)
        .orient("bottom");

    var yAxis = d3.svg.axis()
        .scale(y)
        .tickFormat(function (d) {
            return formatPercentage(d);
        })
        .orient("left");

    var svg = d3.select(SVG_SELECTOR);


    svg.append("g")
        .attr("class", "y axis")
        .attr('transform', 'translate(' + MARGIN.left + ', ' + MARGIN.top + ')')
        .call(yAxis);

    svg.append("g")
        .attr("class", "x axis")
        .attr('transform', "translate(" + MARGIN.left + "," + (HEIGHT - MARGIN.bottom) + ")")
        .call(xAxis);

    var barGroup = svg.append("g").selectAll("g")
        .data(polarityData)
        .enter()
        .append("g")
        .attr('class', 'bar-group')
        .attr("transform", function (d, i) {
            console.log('transform', d, i)
            return "translate(" + (MARGIN.left + x1(group[i])) + ", 0)";
        });

    var bar = barGroup.selectAll(".bar")
        .data(function (d) {
            return d;
        })
        .enter()
        .append('g')
        .attr('class', 'bar');

    bar.append("rect")
        .attr('class', 'bar')
        .attr('class', function (d, i) {
            console.log('in class', d, i)
            var css = '';
            var p = d.polarity.toLowerCase();
            if (p == 'positive') {
                css += ' success';
            } else if (p == 'negative') {
                css += ' warning';
            } else {
                css += ' primary';
            }
            return css;
        })
        .style('fill', function (d, i) {
            console.log('in fill2', d, i)
            var css = '';
            var user = d.user;
            var g = group.map(function (o) {
                return o.toLowerCase()
            });
            var j = g.indexOf(user.toLowerCase());
            console.log(g, user, j)
            var rect = d3.select(this);
            var prevFill = rect.style('fill');
            if (j != 0) {
                return d3.rgb(prevFill).brighter(0.85);
            }
            return prevFill;
        })
        .attr("width", x1.rangeBand())
        .attr("x", function (d, i) {
            console.log('x')
            console.log(d, i)
            console.log(x0(d.polarity))
            return x0(d.polarity);
        })
        .attr('height', 0)
        .attr('y', HEIGHT)
    //.attr("y", function (d) {
    //    console.log('safi: ', d)
    //    return MARGIN.top + y(d.count / total[d.user.toUpperCase()]);
    //})
    //.attr("height", function (d) {
    //    console.log('y')
    //    console.log(d)
    //    console.log(y(d.count / total[d.user.toUpperCase()]))
    //    return INNER_HEIGHT - y(d.count / total[d.user.toUpperCase()]);
    //});


    bar.append("text")
        .style('display', function (d) {
            return d.count == 0 ? 'none' : '';
        })
        .attr("x", function (d) {
            return x0(d.polarity) + bar.select('rect').attr('width') / 2;
        })
        .attr("y", function (d) {
            return MARGIN.top + y(d.count / total[d.user.toUpperCase()]) + 10;
        })
        .attr("dy", "1em")
        .attr('class', 'bar-text')
        .style("text-anchor", "middle")
        .text(function (d) {
            var val = d.count / total[d.user.toUpperCase()];
            if(val < .15) {
                return '';
            }
            return formatPercentage(val);
        });
    bar.append("text")
        .style('display', function (d) {
            return d.count == 0 ? 'none' : '';
        })
        .attr("x", function (d) {
            return x0(d.polarity) + bar.select('rect').attr('width') / 2;
        })
        .attr("y", function (d) {
            return MARGIN.top + y(d.count / total[d.user.toUpperCase()]) + 35;
        })
        .attr("dy", "1em")
        .attr('class', 'bar-text')
        .style("text-anchor", "middle")
        .text(function (d) {
            var val = d.count / total[d.user.toUpperCase()];
            if(val < .15) {
                return '';
            }
            return truncate(d.user.toUpperCase(), 10);
        });

    // add transition
    bar.select('rect')
        .transition()
        .attr("y", function (d) {
            console.log('transition1: ', d);
            return MARGIN.top + y(d.count / total[d.user.toUpperCase()]);
        })
        .attr("height", function (d) {
            console.log('y')
            console.log(d)
            console.log(y(d.count / total[d.user.toUpperCase()]))
            return INNER_HEIGHT - y(d.count / total[d.user.toUpperCase()]);
        })
        .delay(function (d, i) {
            return i * 10;
        })
        .duration(1000)
        .ease('elastic');

    // add tooltip
    bar.select('rect')
        .attr('data-toggle', 'tooltip')
        .attr('data-original-title', function (d) {
            return 'Profile: <b>' + d.user.toUpperCase() + '</b><br/>'+ formatPercentage(d.count / total[d.user.toUpperCase()]);
        });
    // init bootstrap tooltip
    $('[data-toggle="tooltip"]').tooltip({
        'container': 'body',
        'placement': 'top',
        'html': true
    });

}

function truncate(string, n) {
    if (string.length > n)
        return $.trim(string.substring(0, n)) + '...';
    else
        return string;
};

function drawPiechart(polarityData) {

    var polarityTotal = d3.sum(polarityData.map(function (o) {
        return o.count;
    }));

    var SVG_SELECTOR = "#svg-polarity-piechart",
        TABLE_SELECTOR = "#table-polarity",
        WIDTH = $(SVG_SELECTOR).width(),
        HEIGHT = $(SVG_SELECTOR).height(),
        MARGIN = {
            top: 30,
            right: 30,
            bottom: 30,
            left: 30
        },
        INNER_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom,
        INNER_WIDTH = WIDTH - MARGIN.left - MARGIN.right,
        RADIUS = Math.floor(Math.min(INNER_HEIGHT, INNER_WIDTH) / 2),
        INNER_RADIUS = RADIUS / 2;

    var svg = d3.select(SVG_SELECTOR);

    var arc = d3.svg.arc()
        .outerRadius(RADIUS)
        .innerRadius(INNER_RADIUS);

    var pie = d3.layout.pie()
        .value(function (d) {
            return d.count;
        });

    var colorScale = d3.scale.ordinal()
        .domain(["Positive", "Negative", "Neutral"])
        .range(["#2ca02c", "#ff7f0e", "#1f77b4"]);

    var ratioScale = d3.scale.linear()
        .domain([0, polarityTotal])
        .range([0, 1]);

    var pieGroup = svg.append('g')
        .attr("transform", "translate(" + WIDTH / 2 + "," + HEIGHT / 2 + ")");

    var centerText = svg.append('text')
        .attr('class', 'pie-center-text')
        .attr('text-anchor', 'middle')
        .attr('alignment-baseline', 'middle')
        .attr("transform", "translate(" + WIDTH / 2 + "," + HEIGHT / 2 + ")")
        .text('');


    pieGroup.selectAll('g')
        .data(pie(polarityData))
        .enter()
        .append('g')
        .attr('class', 'slice');

    pieGroup.selectAll('g.slice')
        .append('path')
        .attr('fill', function (d, i) {
            return colorScale(i);
        })
        .attr('stroke', function (d, i) {
            return d3.rgb(colorScale(i)).darker(.5);
        })
        .attr('stroke-width', 2)
        .attr('d', arc);

    // add pie chart slice text
    pieGroup.selectAll('.slice')
        .append('text')
        .attr('class', 'slice-text')
        .attr('text-anchor', 'middle')
        .attr('transform', function (d, i) {
            d.innerRadius = 0;
            d.outerRadius = RADIUS;
            return 'translate(' + arc.centroid(d) + ')';
        })
        .text(function (d, i) {
            var percent = ratioScale(d.value);
            if (percent == 0) {
                return '';
            } else if (percent < .05) {
                return '-';
            }
            return formatPercentage(percent);
        });

    // add pie chart interaction
    pieGroup.selectAll('.slice')
        .on('mouseover', function (d, i) {
            onMouseoverSlice(d3.select(this), d, i);

            // hover table row
            $(TABLE_SELECTOR + ' tbody tr:eq(' + i + ')').addClass('active');
        })
        .on('mouseout', function (d, i) {
            onMouseoutSlice(d3.select(this), d, i);

            // hover table row
            $(TABLE_SELECTOR + ' tbody tr:eq(' + i + ')').removeClass('active');
        })
        .on('click', function (d, i) {
            onClickSlice(d3.select(this), d, i);
        });

    function onMouseoverSlice(sliceGroup, d, i) {
        var slice = sliceGroup.select('path');
        var prevColor = slice.style('fill');
        var hoverColor = d3.rgb(prevColor).darker(.5);
        var hoverArc = d3.svg.arc()
            .outerRadius(RADIUS + 10)
            .innerRadius(INNER_RADIUS);
        slice
            .attr('fill', hoverColor)
            .attr('stroke-width', 8)
            .transition()
            .duration(400)
            .ease('elastic')
            .attr('d', hoverArc);
        centerText
            .attr('fill', hoverColor)
            .text(d.value);
    }

    function onMouseoutSlice(sliceGroup, d, i) {
        var slice = sliceGroup.select('path');
        var hoverArc = d3.svg.arc()
            .outerRadius(RADIUS)
            .innerRadius(INNER_RADIUS);
        slice
            .attr('fill', colorScale(i))
            .attr('stroke-width', 2)
            .transition()
            .duration(400)
            .ease('elastic')
            .attr('d', hoverArc);
        centerText
            .attr('fill', "#ccc")
            .text('');
    }

    function onClickSlice(sliceGroup, d, i) {
        console.log('clicked on: #' + i + '. ' + JSON.stringify(d));
    }

    // add table
    var columns = ['polarity', 'count', 'count'];
    var rows = d3.select(TABLE_SELECTOR).select('tbody').selectAll('tr')
        .data(polarityData)
        .enter()
        .append('tr')
        .style('font-weight', 'bold')
        .style('color', function (d, i) {
            return colorScale(i);
        });

    var cells = rows.selectAll("td")
        .data(function (row) {
            return columns.map(function (column) {
                return {
                    column: column,
                    value: row[column]
                };
            });
        })
        .enter()
        .append("td")
        .attr('class', function (d, i) {
            if (i === 0) {
                return 'clickable';
            } else if (i === 1 || i === 2) {
                return 'text-right';
            }
            return '';
        })
        .html(function (d, i) {
            if (i === 1) {
                return formatPercentage(ratioScale(d.value));
            } else if (i === 2) {
                return d.value;
            }
            return d.value;
        });

    // set table footer content
    $(TABLE_SELECTOR + ' tfoot tr td:eq(1)').text('100.0%');
    $(TABLE_SELECTOR + ' tfoot tr td:eq(2)').text(polarityTotal);

    // add table to slice interactivity
    $(TABLE_SELECTOR + ' tbody tr').on('mouseover', function () {
        var i = $(this).index();
        var slice = svg.select('.slice:nth-child(' + (i + 1) + ')');
        var d = slice.data()[0];
        if (d == 0) {
            console.log(d)
            return;
        }
        onMouseoverSlice(slice, d, i);
    });

    $(TABLE_SELECTOR + ' tbody tr').on('mouseout', function () {
        var i = $(this).index();
        var slice = svg.select('.slice:nth-child(' + (i + 1) + ')');
        var d = slice.data()[0];
        onMouseoutSlice(slice, d, i);
    });

    $(TABLE_SELECTOR + ' tbody tr td.clickable').on('click', function () {
        var i = $(this).parent().index();
        var slice = svg.select('.slice:nth-child(' + (i + 1) + ')');
        var d = slice.data()[0];
        onClickSlice(slice, d, i);
    });

}

function showDialog(index) {
    index = parseInt(index);
    var comment = $.grep(commentsData, function (v) {
        return v.index == index;
    });
    comment = comment[0];

    var html = $('#template-modal').html();
    bootbox.dialog({
        message: html,
        buttons: {
            main: {
                label: "Close",
                className: "btn-primary",
                callback: function () {
                    console.log('close');
                }
            }
        }
    });

    $('#mainpost').html(comment.mainpost);

    populateDynatable('table#theme', comment.theme);
    populateDynatable('table#category', comment.category);
    populateDynatable('table#entity', comment.entity);

}

function populateDynatable(selector, data) {
    if (typeof data !== 'undefined' && data.length > 0) {
        $(selector).dynatable({
            dataset: {
                records: data
            },
            features: {
                paginate: false,
                sort: false,
                pushState: true,
                search: false,
                recordCount: false,
                perPageSelect: true
            },
            writers: {
                _cellWriter: myCellWriter
            },
            table: {
                defaultColumnIdStyle: 'underscore'
            }
        });
    } else {
        $(selector).html('No records to display.');
    }
}

function myCellWriter(column, record) {
    if (column.index == 1) {
        var val = column.attributeWriter(record);
        val = formatScore(val);
        var css = 'label-primary';
        if (val < 0) {
            css = 'label-warning';
        } else if (val > 0) {
            css = 'label-success';
        }
        val = '<span class="label ' + css + '">' + val + '</span>';

        return defaultCellWriter(column, null, val);
    }
    return defaultCellWriter(column, record);
}

function defaultCellWriter(column, record, html) {
    if (!html)
        html = column.attributeWriter(record);

    var td = '<td';

    if (column.hidden || column.textAlign) {
        td += ' style="';

        // keep cells for hidden column headers hidden
        if (column.hidden) {
            td += 'display: none;';
        }

        // keep cells aligned as their column headers are aligned
        if (column.textAlign) {
            td += 'text-align: ' + column.textAlign + ';';
        }

        td += '"';
    }

    if (column.cssClass) {
        td += ' class="' + column.cssClass + '"';
    }

    return td + '>' + html + '</td>';
};
