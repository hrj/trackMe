
  function toDegree(radian){
	  return (radian * 180) / Math.PI;
  }
  function updateMap(map){
      $.get("http://localhost:8888/api/retrieve",function(serverLocations ,status){
    	  if( lineLayer && markers){
    		  map.removeLayer(lineLayer);
    		  map.removeLayer(markers);
    	  } 
          var markers = new OpenLayers.Layer.Markers( "Markers" );
          map.addLayer(markers);
          
          var size = new OpenLayers.Size(21,25);
          var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
          var icon = new OpenLayers.Icon('/static/img/marker.png',size,offset);

          var lineLayer = new OpenLayers.Layer.Vector("Line Layer");
          map.addLayer(lineLayer);
          var points = new Array();
          console.log(serverLocations);
          var obj = serverLocations;
          var i=0;
          while(i<Number(obj.locations.length)){
            var point = new OpenLayers.Geometry.Point(toDegree(obj.locations[i].long),
            		toDegree(obj.locations[i].lat));
            points.push(point);
            markers.addMarker(new OpenLayers.Marker(new OpenLayers.LonLat(toDegree(obj.locations[i].long),
            		toDegree(obj.locations[i].lat)).
            transform(new OpenLayers.Projection("EPSG:4326"), 
            new OpenLayers.Projection("EPSG:900913")),icon.clone()));    
            i++;
          }

          var line = new OpenLayers.Geometry.LineString(points).transform(new OpenLayers.Projection("EPSG:4326"),
            new OpenLayers.Projection("EPSG:900913"));

          var style = { 
            strokeColor: '#0000ff', 
            strokeOpacity: 0.5,
            strokeWidth: 5
          };

          var lineFeature = new OpenLayers.Feature.Vector(line, null, style);

          lineLayer.addFeatures([lineFeature]);
          map.zoomToExtent(markers.getDataExtent());
    	  
      });
  }
  
  $(window).load(function(){
	  	  OpenLayers.ImgPath = "/static/img/"; 
          var map = new OpenLayers.Map('map');
          var layer = new OpenLayers.Layer.OSM( "Simple OSM Map");
          map.addLayer(layer);
          map.addControl(new OpenLayers.Control.LayerSwitcher());
    	  map.setCenter(new OpenLayers.LonLat(0, 0), 000000000); 
    	  $("#mapUpdate").click(function () {
    		  updateMap(map);
    		  });
  });
