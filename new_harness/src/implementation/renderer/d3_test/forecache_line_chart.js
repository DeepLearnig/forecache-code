ForeCacheLineChart = {}

// used to create a unique identifier for visualization objects in the DOM
ForeCacheLineChart.getPrefix = function() { return BigDawgVis.getPrefix() + "forecache-line-chart-";};

// returns a pointer to a new div containing the rendered visualization.
// The new div has a unique identifier taking the form: "bigdawgvis-linechart-<UUID>".
// this function also appends the div to the given node in the dom (a.k.a. "root")
ForeCacheLineChart.getVis = function(root,options,jsondata) {
  var name = ForeCacheLineChart.getPrefix()+BigDawgVis.uuid();
  var visDiv = $("<div id=\""+name+"\"></div>").appendTo(root);
  var graph = new SimpleGraph(visDiv,options,jsondata.table);
  return visDiv;
};


/* chart parameter is a jquery object */
SimpleGraph = function(chart, options,points) {
	var self = this;
  self.points = points;

	//default values
	this.xlabel = options.xlabel
	this.ylabel = options.ylabel
	this.fixYDomain = false;
	this.mousebusy = false;

	this.options = options || {};
	this.chart = (chart.toArray())[0]; // get dom element from jquery
	this.cx = this.options.width;
	this.cy = this.options.height;
	this.blankStyle = "#fff";
	this.background = "#EBEBE0";
	this.fillStyle = "#993366";

	this.padding = this.options.padding;

	this.size = {
		"width":	this.cx - this.padding.left - this.padding.right,
		"height": this.cy - this.padding.top	- this.padding.bottom
	};

	$(this.chart).css("position","relative");
	$(this.chart).css("width",this.cx);
	$(this.chart).css("height",this.cy);

	console.log(["points",points]);

  var xdomain = BigDawgVis.domain(points,this.options.xname)
	var halfWidth = Math.ceil((xdomain[1]-xdomain[0])/2);
	// x-scale
	this.x = d3.scale.linear()
		.domain(xdomain)
		.range([0, this.size.width]);

	if(this.hasOwnProperty("xdomain")) {
		this.x.domain(this.xdomain);
	}

	// y-scale (inverted domain)
	var ydomain = BigDawgVis.domain(points,this.options.yname);
/*
	if((ydomain.length==0)||((ydomain[0]==0) && (ydomain[1]==0))) {
		ydomain = [-1,+1];
	}
*/
	this.y = d3.scale.linear()
		.domain(ydomain)
		.nice()
		.range([this.size.height,0])
		.nice();

	if(this.hasOwnProperty("ydomain")) {
		this.y.domain(this.ydomain);
	}

	this.xAxis = d3.svg.axis().scale(this.x).orient("bottom");
	this.yAxis = d3.svg.axis().scale(this.y).orient("left");

	this.vis = d3.select(this.chart).append("svg")
		.attr("width",	this.cx)
		.attr("height", this.cy)
		.attr("class","forecache-vis")
		.append("g")
			.attr("transform", "translate(" + this.padding.left + "," + this.padding.top + ")");

	
	this.plot = this.vis.append("rect")
			.attr("width", this.size.width)
			.attr("height", this.size.height)
			.style("fill", "#FFFFFF")
			.style("opacity",0);

	//this.plot.call(d3.behavior.zoom().scaleExtent([1,1]).x(this.x).y(this.y).on("zoom", this.redraw()));
	this.plot.call(d3.behavior.zoom().scaleExtent([1,1]).x(this.x).y(this.y).on("zoom", this.redraw())
			.on("zoomend",this.afterZoom()));

	//this.plot.call(d3.behavior.zoom().x(this.x).y(this.y).on("zoom", this.redraw()));

	this.svg = this.vis.append("svg")
			.attr("top", 0)
			.attr("left", 0)
			.attr("width", this.size.width)
			.attr("height", this.size.height)
			//.attr("viewBox", "0 0 "+this.size.width+" "+this.size.height)
			.attr("class", "line");

	// add Chart Title
	if (this.options.title) {
		this.vis.append("text")
				.attr("class", "forecache-axis")
				.text(this.options.title)
				.attr("x", this.size.width/2)
				.attr("dy","-0.8em")
				.style("text-anchor","middle");
	}

	//Add the x-axis label
	this.vis.append("text")
		.attr("class", "x forecache-axis")
		.text(this.xlabel)
		.attr("x", this.size.width/2)
		.attr("y", this.size.height)
		.attr("dy","2.4em")
		.style("text-anchor","middle");

// add y-axis label
	if (this.options.ylabel) {
		this.vis.append("g").append("text")
			.attr("class", "y forecache-axis")
			.text(this.options.ylabel)
			.style("text-anchor","middle")
			.attr("transform","translate(" + -40 + " " + this.size.height/2+") rotate(-90)");
	}

	this.base = d3.select(this.chart);
	this.canvas = this.base.append("canvas")
		.attr("width",this.cx)
		.attr("height",this.cy);
	this.ctx = this.canvas.node().getContext("2d");
	this.buttonsDiv = $(
	"<div class='buttons'>"+
  	"<button data-zoom='1'>Zoom In</button>"+
  	"<button data-zoom='-1'>Zoom Out</button>"+
	"</div>").appendTo(this.chart);

	this.buttonsDiv.css("position","absolute");
	this.buttonsDiv.css("right",Number(this.padding.right)+5);
	this.buttonsDiv.css("top",Number(this.padding.top)+5);
	d3.selectAll(this.buttonsDiv.children().toArray()) // get the actual buttons
    .on("click", this.zoomClick());

 	// used to update the y domain, so the visualization looks better
	this.fixYDomain = true;
	this.redraw()();
};

//
// SimpleGraph methods
//

SimpleGraph.prototype.zoomClick = function() {
	var self = this;
	return function () {};
/*
	return function () {
		if(!self.mousebusy) {
			self.mousebusy = true;
			$('body').css("cursor", "wait");
		}
		var zoomDiff = Number(this.getAttribute("data-zoom"));
		if(zoomDiff < 0) console.log("zoom out");
		else console.log("zoom in");
		var newZoom = Math.min(Math.max(0,self.currentZoom+zoomDiff),self.aggWindows.length-1);
		console.log(["zoomDiff",zoomDiff,"currentZoom",self.currentZoom,"newZoom",newZoom]);
		if(newZoom == self.currentZoom) return; // no change

		var xdomain = self.x.domain();
		var ydomain = self.y.domain();
		var xmid = xdomain[0] + 1.0*(xdomain[1]-xdomain[0])/2;
		console.log(["xmid",xmid,"xdomain",xdomain,"zoomDiff",zoomDiff,
									"currentZoom",self.currentZoom,"newZoom",newZoom]);
		var oldWindow = self.aggWindows[self.currentZoom];
		var newWindow = self.aggWindows[newZoom];
		var newXmid = 1.0*xmid * oldWindow / newWindow;
		var halfWidth = self.k * self.cacheSize / 2.0;
		var newXdomain = [newXmid-halfWidth,newXmid+halfWidth];
		console.log(["oldWindow",oldWindow,"newWindow",newWindow,"newXmid",newXmid,
									"halfWidth",halfWidth,"newXdomain",newXdomain]);
		
		self.currentZoom = newZoom;
		self.tileMap = {};
		self.currentTiles = [];
		self.x.domain(newXdomain);
		self.fixYDomain = true;
		self.afterZoom()();
	};
*/
}

SimpleGraph.prototype.canvasUpdate = function() {
	var self = this;
	this.ctx.beginPath();
	this.ctx.fillStyle = this.background;
	this.ctx.rect(0,0,this.cx,this.cy);
	this.ctx.fill();
	this.ctx.closePath();

	var start = true;
	var arcval = 2 * Math.PI;
	var prevx,prevy;

	var points = self.points;
	for(var i=0; i < points.length;i++) {
		var d = points[i];
		var x = this.x(d[this.options.xname])+this.padding.left;
		var y = this.y(d[this.options.yname])+this.padding.top;
		if(start) {
			start = false;
		} else {
			this.ctx.beginPath();
			this.ctx.moveTo(prevx,prevy);
 			this.ctx.strokeStyle = this.fillStyle;
			this.ctx.lineTo(x,y);
			this.ctx.stroke();
			this.ctx.closePath();
		}
		this.ctx.beginPath();
 		this.ctx.fillStyle = this.fillStyle;
		this.ctx.arc(x,y, 3, 0, arcval, false);
 		this.ctx.fill();
		this.ctx.closePath();

		prevx = x;
		prevy = y;
	}
	this.ctx.beginPath();
	this.ctx.fillStyle=this.blankStyle;
	this.ctx.rect(0,0,this.cx,this.padding.top);//1
	this.ctx.fill();
	this.ctx.closePath();

	this.ctx.beginPath();
	this.ctx.fillStyle=this.blankStyle;
	this.ctx.rect(this.cx-this.padding.right,0,this.cx,this.cy);//2
	this.ctx.fill();
	this.ctx.closePath();

	this.ctx.beginPath();
	this.ctx.fillStyle=this.blankStyle;
	this.ctx.rect(0,this.cy-this.padding.bottom,this.cx,this.cy); //3
	this.ctx.fill();
	this.ctx.closePath();

	this.ctx.beginPath();
	this.ctx.fillStyle=this.blankStyle;
	this.ctx.rect(0,0,this.padding.left,this.cy); //4
	this.ctx.fill();
	this.ctx.closePath();
	if(this.mousebusy) {
		this.mousebusy = false;
		$("body").css("cursor", "default");
	}
}

SimpleGraph.prototype.afterZoom = function() {
	var self = this;
	return function() {};
/*
	return function() {
		if(!self.mousebusy) {
			self.mousebusy = true;
			$('body').css("cursor", "wait");
		}
			var xdom = self.x.domain();
			var low = Math.max(0,parseInt(xdom[0],10));
			var high = Math.max(0,parseInt(xdom[1],10));

			var minID = Math.floor(low / self.k);
			var maxID = Math.floor(high / self.k);
			var newIDs = [];
			var newTileMap = {};
			var toFetch = [];
			for(var tileID = minID; tileID < maxID; tileID++) {
				newIDs.push(tileID);
			}
			newIDs.push(maxID);

			for(var i = 0; i < newIDs.length; i++) {
				var tileID = newIDs[i];
				if(!self.tileMap.hasOwnProperty(tileID)) {
					toFetch.push(tileID);
				} else {
					newTileMap[tileID] = self.tileMap[tileID];
				}
			}
			self.tileMap = newTileMap; //get rid of the stuff we don't need
			self.currentTiles = newIDs; // record the new list of tiles
			console.log(["to fetch",toFetch]);
			console.log(["current tiles",self.currentTiles,self.tileMap]);
			if(toFetch.length > 0) {
				self.getTiles(toFetch,function() {self.redraw()();}); // get the missing tiles from the new list
			} else {
				if(self.mousebusy) {
					self.mousebusy = false;
					$('body').css("cursor", "default");
				}
			}
	};
*/
}


/* re-renders the x-axis and y-axis tick lines and labels*/
SimpleGraph.prototype.redraw = function() {
	var self = this;
	return function() {
		var tx = function(d) { 
			return "translate(" + self.x(d) + ",0)"; 
		},
		ty = function(d) { 
			return "translate(0," + self.y(d) + ")";
		},
		stroke = function(d) { 
			return d ? "#ccc" : "#666"; 
		},
		fx = self.x.tickFormat(10),
		fy = self.y.tickFormat(10);

		//replace the xlabel to match current zoom level
		self.vis.selectAll("text.x.forecache-axis").remove();
		self.vis.append("text")
			.attr("class", "x forecache-axis")
			.text(self.xlabel)
			.attr("x", self.size.width/2)
			.attr("y", self.size.height)
			.attr("dy","2.4em")
			.style("text-anchor","middle");

		// Regenerate x-ticks…
		self.vis.selectAll("g.x.forecache-axis").remove();
		self.vis.append("g")
			.attr("class", "x forecache-axis")
			.attr("transform", "translate(0," + (self.size.height)+ ")")
			.call(self.xAxis);

		if(self.fixYDomain) {
			self.fixYDomain = false;
			var points = self.points;
			var ydomain = BigDawgVis.domain(points,self.options.yname);
			var buffer = .15*(ydomain[1]-ydomain[0]);
			if((ydomain.length == 0)||(buffer == 0)) buffer = 1;
			self.y.domain([ydomain[0]-buffer,ydomain[1]+buffer])
		}

		// Regenerate y-ticks…
		self.vis.selectAll("g.y.forecache-axis").remove();
		self.vis.append("g")
			.attr("class", "y forecache-axis")
			.call(self.yAxis);

		//Leilani
		self.plot.call(d3.behavior.zoom().scaleExtent([1,1]).x(self.x).y(self.y).on("zoom", self.redraw())
			.on("zoomend",self.afterZoom()));
		self.canvasUpdate();		
	}	
}

