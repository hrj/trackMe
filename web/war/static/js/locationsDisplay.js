function toDegree(radian) {
  return (radian * 180) / Math.PI;
}

function MapView() {
  var map = new OpenLayers.Map('map');
  var layer = new OpenLayers.Layer.OSM("Simple OSM Map");
  map.addLayer(layer);
  map.addControl(new OpenLayers.Control.LayerSwitcher());
  map.setCenter(new OpenLayers.LonLat(0, 0), 000000000);
  var lineLayer, myMarkers, sharedMarkers;
  this.updateMap = function() {
    function onUpdate(serverLocations, status) {
      if (lineLayer && myMarkers && sharedMarkers) {
        map.removeLayer(lineLayer);
        map.removeLayer(myMarkers);
        map.removeLayer(sharedMarkers);
      }
      myMarkers = new OpenLayers.Layer.Markers("My Location Markers");
      map.addLayer(myMarkers);

      var size = new OpenLayers.Size(21, 25);
      var offset = new OpenLayers.Pixel(-(size.w / 2), -size.h);
      var icon = new OpenLayers.Icon('/static/img/marker.png', size, offset);

      lineLayer = new OpenLayers.Layer.Vector("Line Layer");
      map.addLayer(lineLayer);
      var points = new Array();
      var obj = serverLocations;
      if(obj.locations) {
        $.each(obj.locations, function(i) {
          var myPoints = new OpenLayers.Geometry.Point(
              toDegree(obj.locations[i].long),
              toDegree(obj.locations[i].lat)
          );
          points.push(myPoints);
          myMarkers.addMarker(new OpenLayers.Marker(
            new OpenLayers.LonLat(
              toDegree(obj.locations[i].long), 
              toDegree(obj.locations[i].lat)).transform(
                new OpenLayers.Projection("EPSG:4326"), 
                new OpenLayers.Projection("EPSG:900913")
              ),
              icon.clone()
            )
          );
        });
      }

      sharedMarkers = new OpenLayers.Layer.Markers("Shared Location Markers");
      map.addLayer(sharedMarkers);
      var sharedLocations = obj.sharedLocations;
      if(sharedLocations){
        $.each(sharedLocations, function(key, value) {
          sharedMarkers.addMarker(new OpenLayers.Marker(
            new OpenLayers.LonLat(
              toDegree(value.long),
              toDegree(value.lat)).transform(
                new OpenLayers.Projection("EPSG:4326"),
                new OpenLayers.Projection("EPSG:900913")
              ), 
              icon.clone()
            )
          );
        });
      }

      var line = new OpenLayers.Geometry.LineString(points).transform(
          new OpenLayers.Projection("EPSG:4326"), 
          new OpenLayers.Projection("EPSG:900913")
        );

      var style = {
        strokeColor : '#0000ff',
        strokeOpacity : 0.5,
        strokeWidth : 3
      };

      var lineFeature = new OpenLayers.Feature.Vector(line, null, style);

      lineLayer.addFeatures([ lineFeature ]);
      map.zoomToExtent(myMarkers.getDataExtent() || sharedMarkers.getDataExtent());
    }
    $.ajax({
      url : retrieveURL, 
      success : onUpdate
    });
  }
}

$(window).load(function() {
  OpenLayers.ImgPath = "/static/img/";
  var mapView = new MapView;
  mapView.updateMap();
  $("#mapUpdate").click(mapView.updateMap);
});
