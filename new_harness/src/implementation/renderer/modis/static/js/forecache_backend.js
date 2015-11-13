var ForeCache = ForeCache || {};
ForeCache.Backend = {};

ForeCache.Backend.URL = "http://modis.csail.mit.edu:10001/forecache/modis/fetch/";

ForeCache.Backend.reset = function(callback) {
  var dat = {};
  dat.reset=true;
  ForeCache.Backend.sendRequest(dat,callback);
};

ForeCache.Backend.getView = function(callback) {
  var dat = {};
  dat.getview=true;
  ForeCache.Backend.sendJsonRequest(dat,callback);
};

ForeCache.Backend.getTileStructure = function(callback) {
  var dat = {};
  dat.getts=true;
  var createTileStructure = function(jsondata) {
    var aggregationWindows = [];
    var tileWidths = [];
    var totalLevels = jsondata.aggregationWindows.length;
    var numdims = jsondata.tileWidths.length;
    for(var z = 0; z < totalLevels; z++) {
      var windows = [];
      for(var d = 0; d < numdims; d++) {
        windows.push(parseInt(jsondata.aggregationWindows[z][d]));
      }
      aggregationWindows.push(windows);
    }
    for(var d = 0; d < numdims; d++) {
        tileWidths.push(parseInt(jsondata.tileWidths[d]));
    }
    var ts = new ForeCache.Backend.TileStructure(aggregationWindows,tileWidths);
    callback(ts);
  };
  ForeCache.Backend.sendJsonRequest(dat,createTileStructure);
};

// uses JSON string format to retrieve tile from server
// returns a ForeCache.Backend.Tile object
ForeCache.Backend.getTileJson = function(tileid,callback) {
  var dat = {};
  dat.json = true;
  dat.zoom = tileid.zoom;
  dat.tile_id = tileid.dimindices.join("_");
  var createTile = function(jsondata) {
    // assumes row-major format
    var columns = ForeCache.Backend.rowsToColumns(jsondata.data);
    var attributes = jsondata.attributes;
    var dataTypes = jsondata.dataTypes;
    var tile = new ForeCache.Backend.Tile(columns,attributes,dataTypes,tileid);
    callback(tile); // return the new tile object
  };
  ForeCache.Backend.sendJsonRequest(dat,createTile);
};

// convenience method, chooses JSON or binary format for you
ForeCache.Backend.getTile = function(tileid,callback) {
  ForeCache.Backend.getTileJson(tileid,callback);
};

// retrieves all tiles, then calls the finalcallback function with
// the list of retrieved tiles
ForeCache.Backend.getTiles = function(tileids,finalcallback) {
  var i = 0;
  var tiles = [];
  var processNextTile = function(tile) {
    tiles.push(tile);
    i++;
    if(i < tileids.length) {
      var tid = tileids[i]; // get the next id to retrieve
      ForeCache.Backend.getTile(tid,processNextTile); // repeat loop
    } else { // all tiles have been processed
      finalcallback(tiles);
    }
  };
  var startingid = tileids[0];
  ForeCache.Backend.getTile(startingid,processNextTile);
};

// calls tilecallback function with each individual tile
// then calls the finalcallback function with the final list of tiles
// when retrieval is finished. final callback function is optional
ForeCache.Backend.getAndProcessTiles = function(tileids,tilecallback,finalcallback) {
  if(tileids.length == 0) {
    return;
  }
  var dofc = arguments.length == 3;
  var i = 0;
  var tiles = [];
  var processNextTile = function(tile) { // ForeCache.Backend.Tile object
    tilecallback(tile);
    tiles.push(tile);
    i++;
    if(i < tileids.length) {
      var tid = tileids[i]; // get the next id to retrieve
      ForeCache.Backend.getTile(tid,processNextTile); // repeat loop
    } else { // all tiles have been processed
      if(dofc) {
        finalcallback(tiles);
      }
    }
  };
  var startingid = tileids[0];
  ForeCache.Backend.getTile(startingid,processNextTile);
};

/************** Helper Functions ***************/
ForeCache.Backend.uuid = function() {
    var d = new Date().getTime();
    var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = (d + Math.random()*16)%16 | 0;
        d = Math.floor(d/16);
        return (c=='x' ? r : (r&0x3|0x8)).toString(16);
    });
    return uuid;
};
ForeCache.Backend.sendRequest = function(dat,callback) {
  $.get(ForeCache.Backend.URL,dat,callback);
}

ForeCache.Backend.sendJsonRequest = function(dat,callback) {
  $.getJSON(ForeCache.Backend.URL,dat,callback);
};

// converts nested array in row-major format to nested array in column-major format
ForeCache.Backend.rowsToColumns = function(rows) {
  if(rows.length == 0) {
    return [];
  }
  var columns = [];
  var numcols = rows[0].length;
  for(var c = 0; c < numcols; c++) {
    columns.push([]);
  }
  for(var r = 0; r < rows.length; r++) {
    for(var c = 0; c < numcols; c++) {
      columns[c].push(rows[r][c]);
    }
  }
  return columns;
};

// traverses a list of tiles to compute the overall domain for the given index
ForeCache.Backend.getDomain = function(tiles,index) {
  var domain = [];
  for(var i = 0; i < tiles.length; i++) {
    var tile = tiles[i];
    var tempdomain = tile.getDomain(index);
    if(domain.length == 0) {
      domain = tempdomain;
    } else {
      if(domain[0] > tempdomain[0]) {
        domain[0] = tempdomain[0];
      }
      if(domain[1] < tempdomain[1]) {
        domain[1] = tempdomain[1];
      }
    }
  }
  return domain;
};

/************* Classes *************/

// NewTileKey object
ForeCache.Backend.NewTileKey = function(dimindices,zoom) {
  this.dimindices = dimindices;
  this.zoom = zoom;
  this.name = [zoom,dimindices.join("_")].join("_");
};

ForeCache.Backend.TileMap = function() {
  this.tiles = {};
};

// TileMap object
ForeCache.Backend.TileMap.prototype.batchInsert = function(tiles) {
  for(var i = 0; i < tiles.length; i++) {
    this.insert(tiles[i]);
  }
};


ForeCache.Backend.TileMap.prototype.get = function(id) {
  if(this.containsKey(id)) {
    return this.tiles[id.name];
  }
  return null;
};

ForeCache.Backend.TileMap.prototype.insert = function(tile) {
  this.tiles[tile.id.name] = tile;
};

ForeCache.Backend.TileMap.prototype.containsKey = function(id) {
  return this.tiles.hasOwnProperty(id.name);
};

ForeCache.Backend.TileMap.prototype.remove = function(id) {
  if(this.containsKey(id)) {
    delete this.tiles[id.name];
  }
};

ForeCache.Backend.TileMap.prototype.getTiles = function() {
  var self = this;
  var values = Object.keys(this.tiles).map(function(key){
    return self.tiles[key];
  });
  return values;
};

ForeCache.Backend.TileMap.prototype.clear = function() {
  this.tiles = {};
};

// Tile object
ForeCache.Backend.Tile = function(columns,attributes,dataTypes,id) {
  this.columns = columns;
  this.attributes = attributes;
  this.dataTypes = dataTypes;
  this.id = id;
};

ForeCache.Backend.Tile.prototype.getIndex = function(name) {
  return this.attributes.indexOf(name);
};

ForeCache.Backend.Tile.prototype.getSize = function() {
  if(this.columns.length == 0) return 0;
  return this.columns[0].length;
};

ForeCache.Backend.Tile.prototype.getDomain = function(index) {
  var col = this.columns[index];
	var domain = [];
	if(col.length > 0) {
		domain = [Number(col[0]),Number(col[0])];
  	for(var i = 1; i < col.length; i++) {
    	var val = Number(col[i]);
			if(domain[0] > val) domain[0] = val;
			if(domain[1] < val) domain[1] = val;
		}
	}
	return domain;
};

// TileStructure object
ForeCache.Backend.TileStructure = function(aggregationWindows,tileWidths) {
  this.aggregationWindows = aggregationWindows;
  this.tileWidths = tileWidths;
  this.numdims = this.tileWidths.length;
  this.totalLevels = this.aggregationWindows.length;
};
