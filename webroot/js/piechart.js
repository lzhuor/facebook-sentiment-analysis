
function drawChart() {

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
    .value(function(d) {
      return d.count;
    });

  var colorScale = d3.scale.ordinal()
  .domain(["Positive", "Negative", "Neutral"])
  .range(["#2ca02c", "#ff7f0e" , "#1f77b4"]);

  var ratioScale = d3.scale.linear()
    .domain([0, polarities.total])
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
    .attr('fill', function(d, i) {
      return colorScale(i);
    })
    .attr('stroke', function(d, i) {
      return d3.rgb(colorScale(i)).darker(.5);
    })
    .attr('stroke-width', 2)
    .attr('d', arc);

  // add pie chart slice text
  pieGroup.selectAll('.slice')
    .append('text')
    .attr('class', 'slice-text')
    .attr('text-anchor', 'middle')
    .attr('transform', function(d, i) {
      d.innerRadius = 0;
      d.outerRadius = RADIUS;
      return 'translate(' + arc.centroid(d) + ')';
    })
    .text(function(d, i) {
      var percent = ratioScale(d.value);
      if (percent < .1) {
        return '-';
      }
      return formatPercentage(percent);
    });

  // add pie chart interaction
  pieGroup.selectAll('.slice')
    .on('mouseover', function(d, i) {
      onMouseoverSlice(d3.select(this), d, i);

      // hover table row
      $(TABLE_SELECTOR + ' tbody tr:eq(' + i + ')').addClass('active');
    })
    .on('mouseout', function(d, i) {
      onMouseoutSlice(d3.select(this), d, i);

      // hover table row
      $(TABLE_SELECTOR + ' tbody tr:eq(' + i + ')').removeClass('active');
    })
    .on('click', function(d, i) {
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
    .style('color', function(d, i) {
      return colorScale(i);
    });

  var cells = rows.selectAll("td")
    .data(function(row) {
      return columns.map(function(column) {
        return {
          column: column,
          value: row[column]
        };
      });
    })
    .enter()
    .append("td")
    .attr('class', function(d, i) {
      if (i === 0) {
        return 'clickable';
      } else if (i === 1 || i === 2) {
        return 'text-right';
      }
      return '';
    })
    .html(function(d, i) {
      if (i === 1) {
        return formatPercentage(ratioScale(d.value));
      } else if (i === 2) {
        return d.value;
      }
      return d.value;
    });

  // set table footer content
  $(TABLE_SELECTOR + ' tfoot tr td:eq(1)').text('100.0%');
  $(TABLE_SELECTOR + ' tfoot tr td:eq(2)').text(polarities.total);

  // add table to slice interactivity
  $(TABLE_SELECTOR + ' tbody tr').on('mouseover', function() {
    var i = $(this).index();
    var slice = svg.select('.slice:nth-child(' + (i + 1) + ')');
    var d = slice.data()[0];
    onMouseoverSlice(slice, d, i);
  });

  $(TABLE_SELECTOR + ' tbody tr').on('mouseout', function() {
    var i = $(this).index();
    var slice = svg.select('.slice:nth-child(' + (i + 1) + ')');
    var d = slice.data()[0];
    onMouseoutSlice(slice, d, i);
  });

  $(TABLE_SELECTOR + ' tbody tr td.clickable').on('click', function() {
    var i = $(this).parent().index();
    var slice = svg.select('.slice:nth-child(' + (i + 1) + ')');
    var d = slice.data()[0];
    onClickSlice(slice, d, i);
  });

}
